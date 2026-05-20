/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INTERNAL_ERROR;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;

import static org.wildfly.extension.mcp.server.ToolMessageHandler.prepareArguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutorService;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPResource;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.mcp_java.model.resource.ResourceContents;
import org.wildfly.security.manager.WildFlySecurityManager;

public class ResourceTemplateMessageHandler {

    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;

    ResourceTemplateMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService) {
        this(registry, classLoader, executorService, 0);
    }

    ResourceTemplateMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
    }

    void resourceTemplatesList(JsonObject message, Responder responder) {
        String id = message.get("id").toString();
        JsonObject params = message.getJsonObject("params");
        String cursorValue = params != null ? params.getString("cursor", null) : null;

        List<MCPFeatureMetadata> sorted = StreamSupport.stream(registry.listResourceTemplates().spliterator(), false)
                .sorted(Comparator.comparing(MCPFeatureMetadata::name))
                .toList();
        List<MCPFeatureMetadata> page = applyPage(sorted, cursorValue);
        String nextCursor = nextCursor(sorted, page);

        ROOT_LOGGER.debugf("List resource templates [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder templates = Json.createArrayBuilder();
        for (MCPFeatureMetadata metadata : page) {
            JsonObjectBuilder template = Json.createObjectBuilder()
                    .add("name", metadata.name())
                    .add("description", metadata.description())
                    .add("uriTemplate", metadata.method().uri())
                    .add("mimeType", metadata.method().mimeType());
            templates.add(template);
        }
        JsonObjectBuilder result = Json.createObjectBuilder().add("resourceTemplates", templates);
        if (nextCursor != null) {
            result.add("nextCursor", nextCursor);
        }
        responder.sendResult(id, result);
    }

    private List<MCPFeatureMetadata> applyPage(List<MCPFeatureMetadata> sorted, String cursorValue) {
        int start = 0;
        if (cursorValue != null) {
            String lastName = Cursor.decode(cursorValue);
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).name().equals(lastName)) {
                    start = i + 1;
                    break;
                }
            }
        }
        if (pageSize > 0 && start + pageSize < sorted.size()) {
            return sorted.subList(start, start + pageSize);
        }
        return sorted.subList(start, sorted.size());
    }

    private String nextCursor(List<MCPFeatureMetadata> all, List<MCPFeatureMetadata> page) {
        if (pageSize > 0 && !page.isEmpty()) {
            MCPFeatureMetadata last = page.get(page.size() - 1);
            if (all.indexOf(last) < all.size() - 1) {
                return Cursor.encode(last.name());
            }
        }
        return null;
    }

    void resourceTemplateRead(JsonObject message, Responder responder, MCPConnection connection) {
        String id = message.get("id").toString();
        JsonValue paramsValue = message.get("params");
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, "Message params must be present");
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        String resourceUri = params.getString("uri");
        ROOT_LOGGER.debugf("Read resource template %s [id: %s]", resourceUri, id);

        final MCPFeatureMetadata metadata = registry.findResourceTemplateByUri(resourceUri);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, "No resource template matches URI: " + resourceUri);
            return;
        }
        Map<String, JsonValue> args = extractTemplateArguments(metadata.method().uri(), resourceUri);
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            connection.task(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        MethodMetadata methodMetadata = metadata.method();
                        Class<?> clazz = classLoader.loadClass(methodMetadata.declaringClassName());
                        Instance beanInstance = CDI.current().select(clazz, MCPResource.MCPResourceLiteral.INSTANCE);
                        Object result = null;
                        MethodHandle invoker = registry.findResourceTemplateInvokerByUri(resourceUri);
                        if (beanInstance.isResolvable()) {
                            ROOT_LOGGER.debugf("We have found the Singleton instance of the resource template %s", resourceUri);
                            try {
                                if (args.isEmpty()) {
                                    result = invoker.invoke(beanInstance.get());
                                } else {
                                    ArrayList preparedArguments = new ArrayList(Arrays.asList(prepareArguments(metadata, args, mapper)));
                                    preparedArguments.add(0, beanInstance.get());
                                    result = invoker.invokeWithArguments(preparedArguments);
                                }
                            } catch (Throwable ex) {
                                ROOT_LOGGER.errorInvokingResourceTemplate(ex, resourceUri);
                                responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                            }
                        } else {
                            ROOT_LOGGER.debugf("Singleton instance not found for resource template %s, using reflection", resourceUri);
                            Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                            if (Modifier.isStatic(method.getModifiers())) {
                                result = method.invoke(null, prepareArguments(metadata, args, mapper));
                            } else {
                                Constructor defaultConstructor = clazz.getConstructor(new Class[0]);
                                Object instance = defaultConstructor.newInstance(new Object[0]);
                                result = method.invoke(instance, prepareArguments(metadata, args, mapper));
                            }
                        }
                        Collection<? extends ResourceContents> contents = ContentMapper.processResultAsResourceText(resourceUri, result);
                        JsonArrayBuilder jsonContent = Json.createArrayBuilder();
                        for (ResourceContents content : contents) {
                            JsonObjectBuilder contentResource = Json.createObjectBuilder();
                            contentResource.add("uri", content.uri());
                            String mimeType = content.mimeType() != null ? content.mimeType() : methodMetadata.mimeType();
                            if (mimeType != null && !mimeType.isEmpty()) {
                                contentResource.add("mimeType", mimeType);
                            }
                            if (content.isBlob()) {
                                contentResource.add("blob", content.blob());
                            } else {
                                contentResource.add("text", content.text());
                            }
                            jsonContent.add(contentResource);
                        }
                        JsonObjectBuilder builder = Json.createObjectBuilder();
                        builder.add("contents", jsonContent);
                        responder.sendResult(id, builder);
                    } catch (MCPException e) {
                        ROOT_LOGGER.errorProcessingRequest(e);
                        responder.sendError(id, e.getJsonRpcError(), e.getMessage());
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException | IllegalArgumentException ex) {
                        ROOT_LOGGER.errorInvokingResourceTemplate(ex, resourceUri);
                        responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                    }
                }
            }));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

    private Map<String, JsonValue> extractTemplateArguments(String uriTemplate, String uri) {
        Map<String, JsonValue> args = new HashMap<>();
        // Extract parameter names from template
        Pattern paramPattern = Pattern.compile("\\{([^}]+)}");
        Matcher paramMatcher = paramPattern.matcher(uriTemplate);
        java.util.List<String> paramNames = new ArrayList<>();
        while (paramMatcher.find()) {
            paramNames.add(paramMatcher.group(1));
        }
        // Build regex from template to extract values
        String regex = uriTemplate.replaceAll("\\{[^}]+}", "([^/]+)");
        Matcher valueMatcher = Pattern.compile(regex).matcher(uri);
        if (valueMatcher.matches()) {
            for (int i = 0; i < paramNames.size() && i < valueMatcher.groupCount(); i++) {
                String value = valueMatcher.group(i + 1);
                args.put(paramNames.get(i), Json.createValue(value));
            }
        }
        return args;
    }
}
