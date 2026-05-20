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
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.concurrent.ExecutorService;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
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
import java.util.stream.StreamSupport;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPResource;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.mcp_java.model.resource.ResourceContents;
import org.wildfly.security.manager.WildFlySecurityManager;

public class ResourceMessageHandler {

    private final SchemaGenerator schemaGenerator;
    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;

    ResourceMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService) {
        this(registry, classLoader, executorService, 0);
    }

    ResourceMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this.schemaGenerator = new SchemaGenerator(
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).build());
        this.registry = registry;
        this.mapper = new ObjectMapper();
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
    }

    void resourcesList(JsonObject message, Responder responder) {
        String id = message.get("id").toString();
        JsonObject params = message.getJsonObject("params");
        String cursorValue = params != null ? params.getString("cursor", null) : null;

        List<MCPFeatureMetadata> sorted = StreamSupport.stream(registry.listResources().spliterator(), false)
                .sorted(Comparator.comparing(MCPFeatureMetadata::name))
                .toList();
        List<MCPFeatureMetadata> page = applyPage(sorted, cursorValue);
        String nextCursor = nextCursor(sorted, page);

        ROOT_LOGGER.debugf("List resources [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder resources = Json.createArrayBuilder();
        for (MCPFeatureMetadata resourceMetadata : page) {
            JsonObjectBuilder resource = Json.createObjectBuilder()
                    .add("name", resourceMetadata.name())
                    .add("description", resourceMetadata.description())
                    .add("uri", resourceMetadata.method().uri())
                    .add("mimeType", resourceMetadata.method().mimeType());
            resources.add(resource);
        }
        JsonObjectBuilder result = Json.createObjectBuilder().add("resources", resources);
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

    void resourcesSubscribe(JsonObject message, Responder responder) {
        String id = message.get("id").toString();
        JsonValue paramsValue = message.get("params");
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, "Message params must be present");
            return;
        }
        String resourceUri = paramsValue.asJsonObject().getString("uri", null);
        if (resourceUri == null) {
            responder.sendError(id, INVALID_PARAMS, "Resource URI not defined");
            return;
        }
        ROOT_LOGGER.debugf("Subscribe to resource %s [id: %s]", resourceUri, id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    void resourcesUnsubscribe(JsonObject message, Responder responder) {
        String id = message.get("id").toString();
        JsonValue paramsValue = message.get("params");
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, "Message params must be present");
            return;
        }
        String resourceUri = paramsValue.asJsonObject().getString("uri", null);
        if (resourceUri == null) {
            responder.sendError(id, INVALID_PARAMS, "Resource URI not defined");
            return;
        }
        ROOT_LOGGER.debugf("Unsubscribe from resource %s [id: %s]", resourceUri, id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    void resourceCall(JsonObject message, Responder responder, MCPConnection connection) {
        String id = message.get("id").toString();
        JsonValue paramsValue = message.get("params");
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, "Message params must be present");
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        String resourceUri = params.getString("uri");
        ROOT_LOGGER.debugf("Call resource %s [id: %s]", resourceUri, id);
        Map<String, JsonValue> args = new HashMap<>();
        JsonObject arguments = params.getJsonObject("arguments");
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                args.put(key, arguments.get(key));
            }
        }
        final MCPFeatureMetadata metadata = registry.getResource(resourceUri);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, "Invalid resource name: " + resourceUri);
            return;
        }
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
                        if (beanInstance.isResolvable()) {
                            ROOT_LOGGER.debugf("Singleton instance found for resource %s", resourceUri);
                            try {
                                if (args.isEmpty()) {
                                    result = registry.getResourceInvoker(resourceUri).invoke(beanInstance.get());
                                } else {
                                    ArrayList preparedArguments = new ArrayList(Arrays.asList(prepareArguments(metadata, args, mapper)));
                                    preparedArguments.add(0, beanInstance.get());
                                    result = registry.getResourceInvoker(resourceUri).invokeWithArguments(preparedArguments);
                                }
                            } catch (Throwable ex) {
                                ROOT_LOGGER.errorInvokingResource(ex, resourceUri);
                                responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                            }
                        } else {
                            ROOT_LOGGER.debugf("Singleton instance not found for resource %s, using reflection", resourceUri);
                            Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                            if (Modifier.isStatic(method.getModifiers())) {
                                result = method.invoke(null, prepareArguments(metadata, args, mapper));
                            } else {
                                Constructor defaultConstructor = clazz.getConstructor(new Class[0]);
                                Object instance = defaultConstructor.newInstance(new Object[0]);
                                result = method.invoke(instance, prepareArguments(metadata, args, mapper));
                            }
                        }
                        Collection<? extends ResourceContents> contents = ContentMapper.processResultAsResourceText(methodMetadata.uri(), result);
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
                        ROOT_LOGGER.errorInvokingResource(ex, resourceUri);
                        responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                    }
                }
            }));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

}
