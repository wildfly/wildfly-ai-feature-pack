/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INTERNAL_ERROR;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.BLOB;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CONTENTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESCRIPTION;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.MIME_TYPE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NAME;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NEXT_CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.RESOURCE_TEMPLATES;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TEXT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.URI;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.URI_TEMPLATE;
import static org.wildfly.extension.mcp.server.MCPServerUtils.SHARED_MAPPER;
import static org.wildfly.extension.mcp.server.MCPServerUtils.getRequestId;
import static org.wildfly.extension.mcp.server.MCPServerUtils.invokeViaReflection;
import static org.wildfly.extension.mcp.server.MCPServerUtils.prepareArguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import static org.wildfly.extension.mcp.server.MCPServerUtils.runWithCDIContext;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mcpjava.server.resources.BlobResourceContents;
import org.mcpjava.server.resources.ResourceContents;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.TextResourceContents;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPResource;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.security.manager.WildFlySecurityManager;

public class ResourceTemplateMessageHandler {

    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;

    ResourceTemplateMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this.registry = registry;
        this.mapper = SHARED_MAPPER;
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
    }

    void resourceTemplatesList(JsonObject message, Responder responder) {
        String id = getRequestId(message);
        JsonObject params = message.getJsonObject(PARAMS);
        String cursorValue = params != null ? params.getString(CURSOR, null) : null;

        Cursor.Page<MCPFeatureMetadata> result = Cursor.paginate(registry.listResourceTemplates(), cursorValue, pageSize, MCPFeatureMetadata::name);

        ROOT_LOGGER.debugf("List resource templates [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder templates = Json.createArrayBuilder();
        for (MCPFeatureMetadata metadata : result.items()) {
            JsonObjectBuilder template = Json.createObjectBuilder()
                    .add(NAME, metadata.name())
                    .add(DESCRIPTION, metadata.description())
                    .add(URI_TEMPLATE, metadata.method().uri())
                    .add(MIME_TYPE, metadata.method().mimeType());
            if (metadata.title() != null) {
                template.add("title", metadata.title());
            }
            ResourceAnnotationsUtil.addAnnotations(template, metadata.audience(), metadata.priority());
            templates.add(template);
        }
        JsonObjectBuilder resultBuilder = Json.createObjectBuilder().add(RESOURCE_TEMPLATES, templates);
        if (result.nextCursor() != null) {
            resultBuilder.add(NEXT_CURSOR, result.nextCursor());
        }
        responder.sendResult(id, resultBuilder);
    }

    void resourceTemplateRead(JsonObject message, Responder responder, MCPConnection connection) {
        String id = getRequestId(message);
        JsonValue paramsValue = message.get(PARAMS);
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        String resourceUri = params.getString(URI);
        ROOT_LOGGER.debugf("Read resource template %s [id: %s]", resourceUri, id);

        final MCPFeatureMetadata metadata = registry.findResourceTemplateByUri(resourceUri);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.noMatchingResourceTemplate(resourceUri));
            return;
        }
        Map<String, JsonValue> args = extractTemplateArguments(metadata.method().uri(), resourceUri);
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            connection.task(executorService.submit(() -> runWithCDIContext(connection, responder, () -> {
                try {
                    MethodMetadata methodMetadata = metadata.method();
                    Class<?> clazz = classLoader.loadClass(methodMetadata.declaringClassName());
                    Instance<?> beanInstance = CDI.current().select(clazz, MCPResource.MCPResourceLiteral.INSTANCE);
                    Object result = null;
                    MethodHandle invoker = registry.findResourceTemplateInvokerByUri(resourceUri);
                    if (beanInstance.isResolvable()) {
                        ROOT_LOGGER.debugf("We have found the Singleton instance of the resource template %s", resourceUri);
                        try {
                            if (args.isEmpty()) {
                                result = invoker.invoke(beanInstance.get());
                            } else {
                                List<Object> preparedArguments = new ArrayList<>(Arrays.asList(prepareArguments(metadata.arguments(), args, mapper)));
                                preparedArguments.add(0, beanInstance.get());
                                result = invoker.invokeWithArguments(preparedArguments);
                            }
                        } catch (Throwable ex) {
                            ROOT_LOGGER.errorInvokingResourceTemplate(ex, resourceUri);
                            responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                            return;
                        }
                    } else {
                        ROOT_LOGGER.debugf("Singleton instance not found for resource template %s, using reflection", resourceUri);
                        Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                        result = invokeViaReflection(method, prepareArguments(metadata.arguments(), args, mapper));
                    }
                    Collection<? extends ResourceContents> contents;
                    if (result instanceof ResourceResponse rr) {
                        contents = rr.getContents();
                    } else {
                        contents = ContentMapper.processResultAsResourceText(resourceUri, result);
                    }
                    JsonArrayBuilder jsonContent = Json.createArrayBuilder();
                    for (ResourceContents content : contents) {
                        JsonObjectBuilder contentResource = Json.createObjectBuilder();
                        contentResource.add(URI, content.uri());
                        String mimeType = content.mimeType().orElse(methodMetadata.mimeType());
                        if (mimeType != null && !mimeType.isEmpty()) {
                            contentResource.add(MIME_TYPE, mimeType);
                        }
                        if (content instanceof BlobResourceContents brc) {
                            contentResource.add(BLOB, Base64.getEncoder().encodeToString(brc.blob()));
                        } else if (content instanceof TextResourceContents trc) {
                            contentResource.add(TEXT, trc.text());
                        }
                        jsonContent.add(contentResource);
                    }
                    JsonObjectBuilder builder = Json.createObjectBuilder();
                    builder.add(CONTENTS, jsonContent);
                    responder.sendResult(id, builder);
                } catch (MCPException e) {
                    MCPException.sendError(e, id, responder);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException | IllegalArgumentException ex) {
                    ROOT_LOGGER.errorInvokingResourceTemplate(ex, resourceUri);
                    responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                }
            })));
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
