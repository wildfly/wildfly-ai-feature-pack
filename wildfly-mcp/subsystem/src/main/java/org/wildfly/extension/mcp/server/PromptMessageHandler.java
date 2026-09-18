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
import static org.wildfly.extension.mcp.injection.MCPFieldNames.INPUT_REQUESTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.INPUT_REQUIRED;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.MESSAGES;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NAME;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NEXT_CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PROMPTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.REQUEST_STATE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.REQUIRED;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.RESULT_TYPE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ROLE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TITLE;

import static org.wildfly.extension.mcp.server.MCPServerUtils.SHARED_MAPPER;
import static org.wildfly.extension.mcp.server.MCPServerUtils.asJsonObject;
import static org.wildfly.extension.mcp.server.MCPServerUtils.getRequestId;
import static org.wildfly.extension.mcp.server.MCPServerUtils.invokeViaReflection;
import static org.wildfly.extension.mcp.server.MCPServerUtils.prepareArguments;
import static org.wildfly.extension.mcp.server.MCPServerUtils.sendInvocationFailureResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import static org.wildfly.extension.mcp.server.MCPServerUtils.runWithCDIContext;
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
import org.mcpjava.server.prompts.PromptMessage;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.input.InputResponsesHolder;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPPrompt;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.mcp.api.tool.InputRequiredResult;
import org.wildfly.mcp.api.tool.InputResponses;
import org.wildfly.security.manager.WildFlySecurityManager;

public class PromptMessageHandler {

    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;
    private final RequestStateCodec requestStateCodec;

    PromptMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService,
            int pageSize, RequestStateCodec requestStateCodec) {
        this.registry = registry;
        this.mapper = SHARED_MAPPER;
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
        this.requestStateCodec = requestStateCodec;
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
            if (promptMetadata.title() != null && !promptMetadata.title().isEmpty()) {
                promptJson.add(TITLE, promptMetadata.title());
            }

            JsonArrayBuilder arguments = Json.createArrayBuilder();
            for (ArgumentMetadata arg : promptMetadata.arguments()) {
                if (arg.type() instanceof Class<?> clazz && InputResponses.class.isAssignableFrom(clazz)) {
                    continue;
                }
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

    void promptsGet(JsonObject message, Responder responder, MCPConnection connection,
            RequestStateCodec.DecodedState decodedRequestState) {
        String id = getRequestId(message);
        JsonObject params = asJsonObject(message.get(PARAMS));
        if (params == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
        String promptName = params.getString(NAME);
        ROOT_LOGGER.debugf("Call prompt %s [id: %s]", promptName, id);
        Map<String, JsonValue> args = new HashMap<>();
        JsonObject arguments = params.getJsonObject(ARGUMENTS);
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                args.put(key, arguments.get(key));
            }
        }
        final InputResponses inputResponses = ToolMessageHandler.parseInputResponses(params, decodedRequestState);
        final MCPFeatureMetadata metadata = registry.getPrompt(promptName);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.invalidPromptName(promptName));
            return;
        }
        if (decodedRequestState != null) {
            String tokenPrincipal = decodedRequestState.principal();
            if (tokenPrincipal != null && !tokenPrincipal.isEmpty()
                    && !tokenPrincipal.equals(MCPServerUtils.currentPrincipalName())) {
                responder.sendError(id, INVALID_PARAMS, "requestState principal mismatch");
                return;
            }
            String tokenPromptName = decodedRequestState.toolName();
            if (tokenPromptName != null && !tokenPromptName.isEmpty()
                    && !tokenPromptName.equals(promptName)) {
                responder.sendError(id, INVALID_PARAMS, "requestState prompt binding mismatch");
                return;
            }
        }
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            connection.task(executorService.submit(() -> runWithCDIContext(connection, responder, MCPServerUtils.extractProgressToken(params), null, null, inputResponses, () -> {
                try {
                    MethodMetadata methodMetadata = metadata.method();
                    Class<?> clazz = classLoader.loadClass(methodMetadata.declaringClassName());
                    Instance<?> beanInstance = CDI.current().select(clazz, MCPPrompt.MCPPromptLiteral.INSTANCE);
                    Object result = null;
                    Object[] builtArgs = buildPromptArguments(metadata, args, mapper);
                    if (beanInstance.isResolvable()) {
                        ROOT_LOGGER.debugf("We have found the Singleton instance of the prompt %s", promptName);
                        try {
                            if (builtArgs.length == 0) {
                                result = registry.getPromptInvoker(promptName).invoke(beanInstance.get());
                            } else {
                                List<Object> preparedArguments = new ArrayList<>(Arrays.asList(builtArgs));
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
                        result = invokeViaReflection(method, builtArgs);
                    }
                    if (result instanceof InputRequiredResult irr) {
                        JsonObjectBuilder builder = Json.createObjectBuilder();
                        builder.add(RESULT_TYPE, INPUT_REQUIRED);
                        JsonObjectBuilder inputRequestsBuilder = Json.createObjectBuilder();
                        for (Map.Entry<String, JsonObject> entry : irr.inputRequests().entrySet()) {
                            inputRequestsBuilder.add(entry.getKey(), entry.getValue());
                        }
                        builder.add(INPUT_REQUESTS, inputRequestsBuilder);
                        if (irr.requestState() != null) {
                            builder.add(REQUEST_STATE, MCPServerUtils.encodeMrtrRequestState(
                                    irr.requestState(), id, promptName, requestStateCodec));
                        }
                        responder.sendResult(id, builder);
                        return;
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
                } catch (IllegalArgumentException e) {
                    MCPServerUtils.sendInvalidParamsError(e, id, responder);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException ex) {
                    ROOT_LOGGER.errorInvokingPrompt(ex, promptName);
                    sendInvocationFailureResult(id, ex, responder);
                }
            })));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

    private Object[] buildPromptArguments(MCPFeatureMetadata metadata, Map<String, JsonValue> jsonArgs, ObjectMapper objectMapper) {
        if (metadata.arguments().isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[metadata.arguments().size()];
        int idx = 0;
        for (ArgumentMetadata arg : metadata.arguments()) {
            if (arg.type() instanceof Class<?> clazz && InputResponses.class.isAssignableFrom(clazz)) {
                ret[idx] = InputResponsesHolder.get();
            } else {
                JsonValue val = jsonArgs.get(arg.name());
                if (val == null && arg.required()) {
                    throw new IllegalArgumentException(ROOT_LOGGER.missingRequiredArgument(arg.name()));
                }
                Map<String, JsonValue> singleArg = val != null ? Map.of(arg.name(), val) : Map.of();
                Object[] single = prepareArguments(List.of(arg), singleArg, objectMapper);
                ret[idx] = single[0];
            }
            idx++;
        }
        return ret;
    }

}
