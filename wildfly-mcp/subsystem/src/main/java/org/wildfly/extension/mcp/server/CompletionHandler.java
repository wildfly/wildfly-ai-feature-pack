/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INTERNAL_ERROR;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_REQUEST;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.wildfly.mcp.api.completion.CompleteContext;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPPrompt;
import org.wildfly.extension.mcp.injection.tool.MCPResource;
import org.wildfly.security.manager.WildFlySecurityManager;

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

        Map<String, String> contextArguments = extractContextArguments(params);

        if ("ref/prompt".equals(referenceType)) {
            MCPFeatureMetadata metadata = registry.getPromptCompletion(completionKey);
            if (metadata == null) {
                sendEmptyCompletion(id, responder);
                return;
            }
            MethodHandle invoker = registry.getPromptCompletionInvoker(completionKey);
            invokeCompletion(id, metadata, invoker, argumentValue, contextArguments, responder, MCPPrompt.MCPPromptLiteral.INSTANCE);
        } else if ("ref/resource".equals(referenceType)) {
            MCPFeatureMetadata metadata = registry.getResourceTemplateCompletion(completionKey);
            if (metadata == null) {
                sendEmptyCompletion(id, responder);
                return;
            }
            MethodHandle invoker = registry.getResourceTemplateCompletionInvoker(completionKey);
            invokeCompletion(id, metadata, invoker, argumentValue, contextArguments, responder, MCPResource.MCPResourceLiteral.INSTANCE);
        } else {
            responder.sendError(id, INVALID_REQUEST, "Unsupported reference found: " + referenceType);
        }
    }

    private Map<String, String> extractContextArguments(JsonObject params) {
        JsonObject context = params.getJsonObject("context");
        if (context == null) {
            return Map.of();
        }
        JsonObject contextArgs = context.getJsonObject("arguments");
        if (contextArgs == null) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : contextArgs.keySet()) {
            map.put(key, contextArgs.getString(key));
        }
        return Map.copyOf(map);
    }

    @SuppressWarnings("unchecked")
    private void invokeCompletion(String id, MCPFeatureMetadata metadata, MethodHandle invoker,
            String argumentValue, Map<String, String> contextArguments,
            Responder responder, jakarta.enterprise.util.AnnotationLiteral<?> qualifier) {
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            Class<?> clazz = classLoader.loadClass(metadata.method().declaringClassName());
            Instance beanInstance = CDI.current().select(clazz, qualifier);
            Object target = beanInstance.isResolvable()
                    ? beanInstance.get()
                    : clazz.getConstructor().newInstance();
            Object[] args = buildCompletionArguments(metadata, argumentValue, contextArguments);
            ArrayList<Object> preparedArgs = new ArrayList<>(args.length + 1);
            preparedArgs.add(target);
            for (Object arg : args) {
                preparedArgs.add(arg);
            }
            Object result = invoker.invokeWithArguments(preparedArgs);
            sendCompletionResponse(id, result, responder);
        } catch (Throwable ex) {
            ROOT_LOGGER.errorInvokingCompletion(ex, metadata.name());
            responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

    private Object[] buildCompletionArguments(MCPFeatureMetadata metadata,
            String argumentValue, Map<String, String> contextArguments) {
        List<ArgumentMetadata> argDefs = metadata.arguments();
        if (argDefs.isEmpty()) {
            return new Object[0];
        }
        Object[] result = new Object[argDefs.size()];
        for (int i = 0; i < argDefs.size(); i++) {
            ArgumentMetadata arg = argDefs.get(i);
            if (arg.type() instanceof Class<?> clazz
                    && CompleteContext.class.isAssignableFrom(clazz)) {
                result[i] = new CompleteContextImpl(contextArguments);
            } else {
                result[i] = argumentValue;
            }
        }
        return result;
    }

    private static final int MAX_COMPLETION_VALUES = 100;

    @SuppressWarnings("unchecked")
    private void sendCompletionResponse(String id, Object result, Responder responder) {
        JsonArrayBuilder valuesArray = Json.createArrayBuilder();
        int total = 0;
        boolean hasMore = false;

        if (result instanceof List<?> list) {
            total = list.size();
            hasMore = total > MAX_COMPLETION_VALUES;
            int limit = Math.min(total, MAX_COMPLETION_VALUES);
            for (int i = 0; i < limit; i++) {
                valuesArray.add(list.get(i).toString());
            }
        } else if (result instanceof String string) {
            valuesArray.add(string);
            total = 1;
        } else if (result != null) {
            valuesArray.add(result.toString());
            total = 1;
        }

        JsonObjectBuilder completion = Json.createObjectBuilder()
                .add("values", valuesArray)
                .add("total", total)
                .add("hasMore", hasMore);
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
