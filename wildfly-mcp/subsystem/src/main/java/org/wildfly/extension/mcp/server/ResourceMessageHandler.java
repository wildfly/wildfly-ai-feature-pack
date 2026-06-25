/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INTERNAL_ERROR;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ARGUMENTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.BLOB;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CONTENTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESCRIPTION;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.MIME_TYPE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NAME;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NEXT_CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.RESOURCES;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.SIZE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TEXT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TITLE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.URI;

import static org.wildfly.extension.mcp.server.MCPServerUtils.SHARED_MAPPER;
import static org.wildfly.extension.mcp.server.MCPServerUtils.getRequestId;
import static org.wildfly.extension.mcp.server.MCPServerUtils.invokeViaReflection;
import static org.wildfly.extension.mcp.server.MCPServerUtils.prepareArguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.mcpjava.server.resources.BlobResourceContents;
import org.mcpjava.server.resources.ResourceContents;
import org.mcpjava.server.resources.TextResourceContents;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPResource;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.security.manager.WildFlySecurityManager;

public class ResourceMessageHandler {

    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;
    private final ConcurrentHashMap<String, Set<MCPConnection>> subscriptions = new ConcurrentHashMap<>();

    ResourceMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this.registry = registry;
        this.mapper = SHARED_MAPPER;
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
    }

    void resourcesList(JsonObject message, Responder responder) {
        String id = getRequestId(message);
        JsonObject params = message.getJsonObject(PARAMS);
        String cursorValue = params != null ? params.getString(CURSOR, null) : null;

        Cursor.Page<MCPFeatureMetadata> result = Cursor.paginate(registry.listResources(), cursorValue, pageSize, MCPFeatureMetadata::name);

        ROOT_LOGGER.debugf("List resources [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder resources = Json.createArrayBuilder();
        for (MCPFeatureMetadata resourceMetadata : result.items()) {
            JsonObjectBuilder resource = Json.createObjectBuilder()
                    .add(NAME, resourceMetadata.name())
                    .add(DESCRIPTION, resourceMetadata.description())
                    .add(URI, resourceMetadata.method().uri())
                    .add(MIME_TYPE, resourceMetadata.method().mimeType());
            if (resourceMetadata.title() != null) {
                resource.add(TITLE, resourceMetadata.title());
            }
            if (resourceMetadata.size() >= 0) {
                resource.add(SIZE, resourceMetadata.size());
            }
            ResourceAnnotationsUtil.addAnnotations(resource, resourceMetadata.audience(), resourceMetadata.priority());
            resources.add(resource);
        }
        JsonObjectBuilder resultBuilder = Json.createObjectBuilder().add(RESOURCES, resources);
        if (result.nextCursor() != null) {
            resultBuilder.add(NEXT_CURSOR, result.nextCursor());
        }
        responder.sendResult(id, resultBuilder);
    }

    void resourcesSubscribe(JsonObject message, Responder responder, MCPConnection connection) {
        String id = getRequestId(message);
        JsonValue paramsValue = message.get(PARAMS);
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
        String resourceUri = paramsValue.asJsonObject().getString(URI, null);
        if (resourceUri == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.resourceUriNotDefined());
            return;
        }
        subscriptions.computeIfAbsent(resourceUri, k -> ConcurrentHashMap.newKeySet()).add(connection);
        ROOT_LOGGER.debugf("Subscribe to resource %s [id: %s]", resourceUri, id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    void resourcesUnsubscribe(JsonObject message, Responder responder, MCPConnection connection) {
        String id = getRequestId(message);
        JsonValue paramsValue = message.get(PARAMS);
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
        String resourceUri = paramsValue.asJsonObject().getString(URI, null);
        if (resourceUri == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.resourceUriNotDefined());
            return;
        }
        Set<MCPConnection> subscribers = subscriptions.get(resourceUri);
        if (subscribers != null) {
            subscribers.remove(connection);
        }
        ROOT_LOGGER.debugf("Unsubscribe from resource %s [id: %s]", resourceUri, id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    void removeConnection(MCPConnection connection) {
        for (Map.Entry<String, Set<MCPConnection>> entry : subscriptions.entrySet()) {
            entry.getValue().remove(connection);
        }
    }

    //TODO expose this somehow to the user to be actually used.
    void notifyResourceUpdated(String uri) {
        Set<MCPConnection> subscribers = subscriptions.get(uri);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        JsonObject notification = Messages.newNotification("notifications/resources/updated",
                Json.createObjectBuilder().add("uri", uri));
        for (MCPConnection connection : subscribers) {
            if (connection instanceof Responder responder) {
                try {
                    responder.send(notification);
                } catch (Exception e) {
                    ROOT_LOGGER.debugf("Failed to send resource updated notification to connection %s", connection.id());
                }
            }
        }
    }

    void resourceCall(JsonObject message, Responder responder, MCPConnection connection) {
        String id = getRequestId(message);
        JsonValue paramsValue = message.get(PARAMS);
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        String resourceUri = params.getString(URI);
        ROOT_LOGGER.debugf("Call resource %s [id: %s]", resourceUri, id);
        Map<String, JsonValue> args = new HashMap<>();
        JsonObject arguments = params.getJsonObject(ARGUMENTS);
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                args.put(key, arguments.get(key));
            }
        }
        final MCPFeatureMetadata metadata = registry.getResource(resourceUri);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.invalidResourceName(resourceUri));
            return;
        }
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            connection.task(executorService.submit(() -> {
                try {
                    MethodMetadata methodMetadata = metadata.method();
                    Class<?> clazz = classLoader.loadClass(methodMetadata.declaringClassName());
                    Instance<?> beanInstance = CDI.current().select(clazz, MCPResource.MCPResourceLiteral.INSTANCE);
                    Object result = null;
                    if (beanInstance.isResolvable()) {
                        ROOT_LOGGER.debugf("Singleton instance found for resource %s", resourceUri);
                        try {
                            if (args.isEmpty()) {
                                result = registry.getResourceInvoker(resourceUri).invoke(beanInstance.get());
                            } else {
                                List<Object> preparedArguments = new ArrayList<>(Arrays.asList(prepareArguments(metadata.arguments(), args, mapper)));
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
                        result = invokeViaReflection(method, prepareArguments(metadata.arguments(), args, mapper));
                    }
                    Collection<? extends ResourceContents> contents = ContentMapper.processResultAsResourceText(methodMetadata.uri(), result);
                    JsonArrayBuilder jsonContent = Json.createArrayBuilder();
                    for (ResourceContents content : contents) {
                        JsonObjectBuilder contentResource = Json.createObjectBuilder();
                        contentResource.add(URI, content.uri());
                        String mimeType = content.mimeType().orElse(methodMetadata.mimeType());
                        if (mimeType != null && !mimeType.isEmpty()) {
                            contentResource.add(MIME_TYPE, mimeType);
                        }
                        if (content instanceof BlobResourceContents brc) {
                            contentResource.add(BLOB, java.util.Base64.getEncoder().encodeToString(brc.blob()));
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
                    ROOT_LOGGER.errorInvokingResource(ex, resourceUri);
                    responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                }
            }));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

}
