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
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
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
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPPrompt;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.mcp_java.model.prompt.PromptMessage;
import org.wildfly.security.manager.WildFlySecurityManager;

public class PromptMessageHandler {

    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;

    PromptMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService) {
        this(registry, classLoader, executorService, 0);
    }

    PromptMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this.registry = registry;
        this.mapper = new ObjectMapper();
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
    }

    void promptsList(JsonObject message, Responder responder) {
        String id = message.get("id").toString();
        JsonObject params = message.getJsonObject("params");
        String cursorValue = params != null ? params.getString("cursor", null) : null;

        List<MCPFeatureMetadata> sorted = StreamSupport.stream(registry.listPrompts().spliterator(), false)
                .sorted(Comparator.comparing(MCPFeatureMetadata::name))
                .toList();
        List<MCPFeatureMetadata> page = applyPage(sorted, cursorValue);
        String nextCursor = nextCursor(sorted, page);

        ROOT_LOGGER.debugf("List prompts [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder prompts = Json.createArrayBuilder();
        for (MCPFeatureMetadata promptMetadata : page) {
            JsonObjectBuilder promptJson = Json.createObjectBuilder()
                    .add("name", promptMetadata.name())
                    .add("description", promptMetadata.description());

            JsonArrayBuilder arguments = Json.createArrayBuilder();
            for (ArgumentMetadata arg : promptMetadata.arguments()) {
                JsonObjectBuilder argJson = Json.createObjectBuilder()
                        .add("name", arg.name())
                        .add("description", arg.description())
                        .add("required", arg.required());
                arguments.add(argJson);
            }
            promptJson.add("arguments", arguments);
            prompts.add(promptJson);
        }
        JsonObjectBuilder result = Json.createObjectBuilder().add("prompts", prompts);
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

    void promptsGet(JsonObject message, Responder responder, MCPConnection connection) {
        String id = message.get("id").toString();
        JsonValue paramsValue = message.get("params");
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, "Message params must be present");
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        String promptName = params.getString("name");
        ROOT_LOGGER.debugf("Call prompt %s [id: %s]", promptName, id);
        Map<String, JsonValue> args = new HashMap<>();
        JsonObject arguments = params.getJsonObject("arguments");
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                args.put(key, arguments.get(key));
            }
        }
        final MCPFeatureMetadata metadata = registry.getPrompt(promptName);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, "Invalid prompt name: " + promptName);
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
                        Instance beanInstance = CDI.current().select(clazz, MCPPrompt.MCPPromptLiteral.INSTANCE);
                        Object result = null;
                        if (beanInstance.isResolvable()) {
                            ROOT_LOGGER.debugf("We have found the Singleton instance of the prompt %s", promptName);
                            try {
                                if (args.isEmpty()) {
                                    result = registry.getPromptInvoker(promptName).invoke(beanInstance.get());
                                } else {
                                    ArrayList preparedArguments = new ArrayList(Arrays.asList(prepareArguments(metadata, args, mapper)));
                                    preparedArguments.add(0, beanInstance.get());
                                    result = registry.getPromptInvoker(promptName).invokeWithArguments(preparedArguments);
                                }
                            } catch (Throwable ex) {
                                ROOT_LOGGER.errorInvokingPrompt(ex, promptName);
                                responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                            }
                        } else {
                            ROOT_LOGGER.debugf("Singleton instance not found for prompt %s, using reflection", promptName);
                            Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                            if (Modifier.isStatic(method.getModifiers())) {
                                result = method.invoke(null, prepareArguments(metadata, args, mapper));
                            } else {
                                Constructor defaultConstructor = clazz.getConstructor(new Class[0]);
                                Object instance = defaultConstructor.newInstance(new Object[0]);
                                result = method.invoke(instance, prepareArguments(metadata, args, mapper));
                            }
                        }
                        Collection<? extends PromptMessage> promptMessages = ContentMapper.processResultAsPromptMessage(result);
                        JsonArrayBuilder messagesArray = Json.createArrayBuilder();
                        for (PromptMessage promptMessage : promptMessages) {
                            for (var contentBlock : promptMessage.content()) {
                                JsonObjectBuilder messageJson = Json.createObjectBuilder();
                                messageJson.add("role", promptMessage.role().getValue());
                                try (StringWriter contentOut = new StringWriter()) {
                                    mapper.writeValue(contentOut, contentBlock);
                                    try (StringReader contentIn = new StringReader(contentOut.toString())) {
                                        JsonObject contentJson = Json.createReader(contentIn).readObject();
                                        JsonObjectBuilder filteredContent = Json.createObjectBuilder();
                                        for (String key : contentJson.keySet()) {
                                            if (!contentJson.isNull(key)) {
                                                filteredContent.add(key, contentJson.get(key));
                                            }
                                        }
                                        messageJson.add("content", filteredContent);
                                    }
                                }
                                messagesArray.add(messageJson);
                            }
                        }
                        JsonObjectBuilder builder = Json.createObjectBuilder();
                        builder.add("description", methodMetadata.description());
                        builder.add("messages", messagesArray);
                        responder.sendResult(id, builder);
                    } catch (MCPException e) {
                        ROOT_LOGGER.errorProcessingRequest(e);
                        responder.sendError(id, e.getJsonRpcError(), e.getMessage());
                    } catch (IOException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException | IllegalArgumentException ex) {
                        ROOT_LOGGER.errorInvokingPrompt(ex, promptName);
                        responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                    }
                }
            }));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

}
