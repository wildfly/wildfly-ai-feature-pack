/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;

import static org.wildfly.extension.mcp.injection.MCPFieldNames.ARGUMENTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CONTENT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESCRIPTION;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.MESSAGES;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NAME;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NEXT_CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PROMPTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.REQUIRED;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ROLE;

import static org.wildfly.extension.mcp.server.MCPServerUtils.SHARED_MAPPER;
import static org.wildfly.extension.mcp.server.MCPServerUtils.getRequestId;
import static org.wildfly.extension.mcp.server.MCPServerUtils.invokeViaReflection;
import static org.wildfly.extension.mcp.server.MCPServerUtils.prepareArguments;
import static org.wildfly.extension.mcp.server.MCPServerUtils.sendInvocationFailureResult;

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
import java.util.concurrent.ExecutorService;
import org.mcp_java.server.prompts.PromptMessage;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPPrompt;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.security.manager.WildFlySecurityManager;

public class PromptMessageHandler {

    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;

    PromptMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this.registry = registry;
        this.mapper = SHARED_MAPPER;
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
    }

    void promptsList(JsonObject message, Responder responder) {
        String id = getRequestId(message);
        JsonObject params = message.getJsonObject(PARAMS);
        String cursorValue = params != null ? params.getString(CURSOR, null) : null;

        Cursor.Page<MCPFeatureMetadata> result = Cursor.paginate(registry.listPrompts(), cursorValue, pageSize, MCPFeatureMetadata::name);

        ROOT_LOGGER.debugf("List prompts [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder prompts = Json.createArrayBuilder();
        for (MCPFeatureMetadata promptMetadata : result.items()) {
            JsonObjectBuilder promptJson = Json.createObjectBuilder()
                    .add(NAME, promptMetadata.name())
                    .add(DESCRIPTION, promptMetadata.description());

            JsonArrayBuilder arguments = Json.createArrayBuilder();
            for (ArgumentMetadata arg : promptMetadata.arguments()) {
                JsonObjectBuilder argJson = Json.createObjectBuilder()
                        .add(NAME, arg.name())
                        .add(DESCRIPTION, arg.description())
                        .add(REQUIRED, arg.required());
                arguments.add(argJson);
            }
            promptJson.add(ARGUMENTS, arguments);
            prompts.add(promptJson);
        }
        JsonObjectBuilder resultBuilder = Json.createObjectBuilder().add(PROMPTS, prompts);
        if (result.nextCursor() != null) {
            resultBuilder.add(NEXT_CURSOR, result.nextCursor());
        }
        responder.sendResult(id, resultBuilder);
    }

    void promptsGet(JsonObject message, Responder responder, MCPConnection connection) {
        String id = getRequestId(message);
        JsonValue paramsValue = message.get(PARAMS);
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        String promptName = params.getString(NAME);
        ROOT_LOGGER.debugf("Call prompt %s [id: %s]", promptName, id);
        Map<String, JsonValue> args = new HashMap<>();
        JsonObject arguments = params.getJsonObject(ARGUMENTS);
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                args.put(key, arguments.get(key));
            }
        }
        final MCPFeatureMetadata metadata = registry.getPrompt(promptName);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.invalidPromptName(promptName));
            return;
        }
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            connection.task(executorService.submit(() -> {
                try {
                    MethodMetadata methodMetadata = metadata.method();
                    Class<?> clazz = classLoader.loadClass(methodMetadata.declaringClassName());
                    Instance<?> beanInstance = CDI.current().select(clazz, MCPPrompt.MCPPromptLiteral.INSTANCE);
                    Object result = null;
                    if (beanInstance.isResolvable()) {
                        ROOT_LOGGER.debugf("We have found the Singleton instance of the prompt %s", promptName);
                        try {
                            if (args.isEmpty()) {
                                result = registry.getPromptInvoker(promptName).invoke(beanInstance.get());
                            } else {
                                List<Object> preparedArguments = new ArrayList<>(Arrays.asList(prepareArguments(metadata.arguments(), args, mapper)));
                                preparedArguments.add(0, beanInstance.get());
                                result = registry.getPromptInvoker(promptName).invokeWithArguments(preparedArguments);
                            }
                        } catch (Throwable ex) {
                            ROOT_LOGGER.errorInvokingPrompt(ex, promptName);
                            sendInvocationFailureResult(id, ex, responder);
                            return;
                        }
                    } else {
                        ROOT_LOGGER.debugf("Singleton instance not found for prompt %s, using reflection", promptName);
                        Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                        result = invokeViaReflection(method, prepareArguments(metadata.arguments(), args, mapper));
                    }
                    Collection<? extends PromptMessage> promptMessages = ContentMapper.processResultAsPromptMessage(result);
                    JsonArrayBuilder messagesArray = Json.createArrayBuilder();
                    for (PromptMessage promptMessage : promptMessages) {
                        JsonObjectBuilder messageJson = Json.createObjectBuilder();
                        messageJson.add(ROLE, promptMessage.role().name().toLowerCase());
                        messageJson.add(CONTENT, ContentMapper.contentBlockToJson(promptMessage.content()));
                        messagesArray.add(messageJson);
                    }
                    JsonObjectBuilder builder = Json.createObjectBuilder();
                    builder.add(DESCRIPTION, methodMetadata.description());
                    builder.add(MESSAGES, messagesArray);
                    responder.sendResult(id, builder);
                } catch (MCPException e) {
                    MCPException.sendError(e, id, responder);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException | IllegalArgumentException ex) {
                    ROOT_LOGGER.errorInvokingPrompt(ex, promptName);
                    sendInvocationFailureResult(id, ex, responder);
                }
            }));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

}
