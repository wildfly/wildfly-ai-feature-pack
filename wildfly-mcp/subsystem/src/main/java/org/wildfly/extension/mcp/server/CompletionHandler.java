/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INTERNAL_ERROR;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_REQUEST;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.List;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPPrompt;
import org.wildfly.extension.mcp.injection.tool.MCPResource;

public class CompletionHandler {

    private final WildFlyMCPRegistry registry;
    private final ClassLoader classLoader;

    CompletionHandler(WildFlyMCPRegistry registry, ClassLoader classLoader) {
        this.registry = registry;
        this.classLoader = classLoader;
    }

    void complete(JsonObject message, Responder responder, MCPConnection connection) {
        String id = message.get("id").toString();
        JsonObject params = message.getJsonObject("params");
        JsonObject ref = params.getJsonObject("ref");
        if (ref == null) {
            responder.sendError(id, INVALID_REQUEST, "Reference not found");
            return;
        }
        String referenceType = ref.getString("type");
        if (referenceType == null) {
            responder.sendError(id, INVALID_REQUEST, "Reference type not found");
            return;
        }
        JsonObject argument = params.getJsonObject("argument");
        if (argument == null) {
            responder.sendError(id, INVALID_REQUEST, "Argument not found");
            return;
        }
        String referenceName = ref.getString("name");
        String argumentName = argument.getString("name");
        String argumentValue = argument.getString("value", "");
        String completionKey = referenceName + "_" + argumentName;

        if ("ref/prompt".equals(referenceType)) {
            MCPFeatureMetadata metadata = registry.getPromptCompletion(completionKey);
            if (metadata == null) {
                sendEmptyCompletion(id, responder);
                return;
            }
            MethodHandle invoker = registry.getPromptCompletionInvoker(completionKey);
            invokeCompletion(id, metadata, invoker, argumentValue, responder, MCPPrompt.MCPPromptLiteral.INSTANCE);
        } else if ("ref/resource".equals(referenceType)) {
            MCPFeatureMetadata metadata = registry.getResourceTemplateCompletion(completionKey);
            if (metadata == null) {
                sendEmptyCompletion(id, responder);
                return;
            }
            MethodHandle invoker = registry.getResourceTemplateCompletionInvoker(completionKey);
            invokeCompletion(id, metadata, invoker, argumentValue, responder, MCPResource.MCPResourceLiteral.INSTANCE);
        } else {
            responder.sendError(id, INVALID_REQUEST, "Unsupported reference found: " + referenceType);
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeCompletion(String id, MCPFeatureMetadata metadata, MethodHandle invoker,
            String argumentValue, Responder responder, jakarta.enterprise.util.AnnotationLiteral<?> qualifier) {
        try {
            Class<?> clazz = classLoader.loadClass(metadata.method().declaringClassName());
            Instance beanInstance = CDI.current().select(clazz, qualifier);
            Object result;
            if (beanInstance.isResolvable()) {
                result = invoker.invoke(beanInstance.get(), argumentValue);
            } else {
                result = invoker.invoke(clazz.getConstructor().newInstance(), argumentValue);
            }
            sendCompletionResponse(id, result, responder);
        } catch (Throwable ex) {
            ROOT_LOGGER.errorInvokingCompletion(ex, metadata.name());
            responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void sendCompletionResponse(String id, Object result, Responder responder) {
        JsonArrayBuilder valuesArray = Json.createArrayBuilder();
        Integer total = null;
        Boolean hasMore = false;

        if (result instanceof org.mcp_java.model.completion.CompleteResult completeResult) {
            for (String value : completeResult.completion().values()) {
                valuesArray.add(value);
            }
            total = completeResult.completion().total();
            hasMore = completeResult.completion().hasMore();
        } else if (result instanceof List<?> list) {
            for (Object item : list) {
                valuesArray.add(item.toString());
            }
            total = list.size();
        } else if (result instanceof String string) {
            valuesArray.add(string);
            total = 1;
        } else if (result != null) {
            valuesArray.add(result.toString());
            total = 1;
        }

        JsonObjectBuilder completion = Json.createObjectBuilder()
                .add("values", valuesArray);
        if (total != null) {
            completion.add("total", total);
        }
        if (hasMore != null) {
            completion.add("hasMore", hasMore);
        }
        responder.sendResult(id, Json.createObjectBuilder().add("completion", completion));
    }

    private void sendEmptyCompletion(String id, Responder responder) {
        responder.sendResult(id, Json.createObjectBuilder()
                .add("completion", Json.createObjectBuilder()
                        .add("values", Json.createArrayBuilder())
                        .add("hasMore", false)
                        .add("total", 0)));
    }
}
