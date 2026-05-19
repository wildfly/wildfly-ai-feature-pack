/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.api.JsonRPC.INTERNAL_ERROR;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.wildfly.extension.mcp.api.Cursor;
import org.mcp_java.model.content.ContentBlock;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPTool;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSender;
import org.mcp_java.server.McpLog;
import org.wildfly.security.manager.WildFlySecurityManager;

public class ToolMessageHandler {

    private final SchemaGenerator schemaGenerator;
    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;

    ToolMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService) {
        this(registry, classLoader, executorService, 0);
    }

    ToolMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this.schemaGenerator = new SchemaGenerator(
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).build());
        this.registry = registry;
        this.mapper = new ObjectMapper();
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
    }

    void toolsList(JsonObject message, Responder responder) {
        String id = message.get("id").toString();
        JsonObject params = message.getJsonObject("params");
        String cursorValue = params != null ? params.getString("cursor", null) : null;

        List<MCPFeatureMetadata> sorted = StreamSupport.stream(registry.listTools().spliterator(), false)
                .sorted(Comparator.comparing(MCPFeatureMetadata::name))
                .toList();
        List<MCPFeatureMetadata> page = applyPage(sorted, cursorValue);
        String nextCursor = nextCursor(sorted, page);

        MCPLogger.ROOT_LOGGER.debugf("List tools [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder tools = Json.createArrayBuilder();
        for (MCPFeatureMetadata toolMetadata : page) {
            JsonObjectBuilder tool = Json.createObjectBuilder()
                    .add("name", toolMetadata.name())
                    .add("description", toolMetadata.description());
            JsonObjectBuilder properties = Json.createObjectBuilder();
            JsonArrayBuilder required = Json.createArrayBuilder();
            for (ArgumentMetadata a : toolMetadata.arguments()) {
                if (a.type() instanceof Class<?> clazz
                        && (ElicitationSender.class.isAssignableFrom(clazz) || McpLog.class.isAssignableFrom(clazz))) {
                    continue; // injected by the framework, not a client-supplied argument
                }
                properties.add(a.name(), generateSchema(a.type(), a));
                if (a.required()) {
                    required.add(a.name());
                }
            }
            tool.add("inputSchema", Json.createObjectBuilder()
                    .add("type", "object")
                    .add("properties", properties)
                    .add("required", required));
            tools.add(tool);
        }
        JsonObjectBuilder result = Json.createObjectBuilder().add("tools", tools);
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
            int lastIndex = all.indexOf(last);
            if (lastIndex < all.size() - 1) {
                return Cursor.encode(last.name());
            }
        }
        return null;
    }

    private JsonObject generateSchema(Type type, ArgumentMetadata argument) {
        JsonNode jsonNode = schemaGenerator.generateSchema(type);
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.remove("$schema");
            if (argument.description() != null && !argument.description().isBlank()) {
                objectNode.put("description", argument.description());
            }
        }
        return Json.createReader(new StringReader(jsonNode.toString())).readObject();
    }

    void toolsCall(JsonObject message, Responder responder, MCPConnection connection) {
        String id = message.get("id").toString();
        JsonValue paramsValue = message.get("params");
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, "Message params must be present");
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        String toolName = params.getString("name");
        MCPLogger.ROOT_LOGGER.debugf("Call tool %s [id: %s]", toolName, id);
        Map<String, JsonValue> args = new HashMap<>();
        JsonObject arguments = params.getJsonObject("arguments");
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                args.put(key, arguments.get(key));
            }
        }
        final MCPFeatureMetadata metadata = registry.getTool(toolName);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, "Invalid tool name: " + toolName);
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
                        Instance beanInstance = CDI.current().select(clazz, MCPTool.MCPToolLiteral.INSTANCE);
                        Object result = null;
                        Object[] builtArgs = buildArguments(metadata, args, mapper, connection, responder);
                        if (beanInstance.isResolvable()) {
                            MCPLogger.ROOT_LOGGER.debugf("We have found the Singleton instance of the tool %s", toolName);
                            try {
                                if (builtArgs.length == 0) {
                                    result = registry.getToolInvoker(toolName).invoke(beanInstance.get());
                                } else {
                                    ArrayList preparedArguments = new ArrayList(Arrays.asList(builtArgs));
                                    preparedArguments.add(0, beanInstance.get());
                                    result = registry.getToolInvoker(toolName).invokeWithArguments(preparedArguments);
                                }
                            } catch (Throwable ex) {
                                MCPLogger.ROOT_LOGGER.errorf(ex, "Error invoking tool %s", toolName);
                                responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                            }
                        } else {
                            MCPLogger.ROOT_LOGGER.warnf("We have NOT found the Singleton instance of the tool %s", toolName);
                            Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                            if (Modifier.isStatic(method.getModifiers())) {
                                result = method.invoke(null, builtArgs);
                            } else {
                                Constructor defaultConstructor = clazz.getConstructor(new Class[0]);
                                Object instance = defaultConstructor.newInstance(new Object[0]);
                                result = method.invoke(instance, builtArgs);
                            }
                        }
                        Collection<? extends ContentBlock> content = ContentMapper.processResultAsText(result);
                        JsonArrayBuilder contentArray = Json.createArrayBuilder();
                        for (var contentBlock : content) {
                            try (StringWriter out = new StringWriter()) {
                                mapper.writeValue(out, contentBlock);
                                try (StringReader in = new StringReader(out.toString())) {
                                    JsonObject contentJson = Json.createReader(in).readObject();
                                    JsonObjectBuilder filtered = Json.createObjectBuilder();
                                    for (String key : contentJson.keySet()) {
                                        if (!contentJson.isNull(key)) {
                                            filtered.add(key, contentJson.get(key));
                                        }
                                    }
                                    contentArray.add(filtered);
                                }
                            }
                        }
                        JsonObjectBuilder builder = Json.createObjectBuilder();
                        builder.add("content", contentArray);
                        responder.sendResult(id, builder);
                    } catch (MCPException e) {
                        MCPLogger.ROOT_LOGGER.error(e);
                        responder.sendError(id, e.getJsonRpcError(), e.getMessage());
                    } catch (IOException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException | IllegalArgumentException ex) {
                        MCPLogger.ROOT_LOGGER.errorf(ex, "Error invoking tool %s", toolName);
                        responder.sendError(id, INTERNAL_ERROR, ex.getMessage());
                    }
                }
            }));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

    /**
     * Like {@link #prepareArguments} but additionally injects framework-managed parameters
     * such as {@link ElicitationSender} based on the declared argument type.
     */
    private Object[] buildArguments(
            MCPFeatureMetadata metadata,
            Map<String, JsonValue> jsonArgs,
            ObjectMapper objectMapper,
            MCPConnection connection,
            Responder responder) throws MCPException {
        if (metadata.arguments().isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[metadata.arguments().size()];
        int idx = 0;
        for (ArgumentMetadata arg : metadata.arguments()) {
            if (arg.type() instanceof Class<?> clazz && ElicitationSender.class.isAssignableFrom(clazz)) {
                ret[idx] = new ElicitationSenderImpl(
                        connection.pendingRequests(),
                        responder,
                        connection.initializeRequest());
            } else if (arg.type() instanceof Class<?> clazz && McpLog.class.isAssignableFrom(clazz)) {
                ret[idx] = new McpLogImpl(connection, responder, metadata.name());
            } else {
                JsonValue val = jsonArgs.get(arg.name());
                if (val == null && arg.required()) {
                    throw new MCPException("Missing required argument: " + arg.name(), JsonRPC.INVALID_PARAMS);
                }
                // Delegate to the existing single-value conversion logic via a one-element map
                Map<String, JsonValue> singleArg = val != null ? Map.of(arg.name(), val) : Map.of();
                Object[] single = prepareArguments(
                        new MCPFeatureMetadata(metadata.kind(), metadata.name(),
                                new org.wildfly.extension.mcp.injection.tool.MethodMetadata(
                                        metadata.method().name(), metadata.method().description(),
                                        metadata.method().uri(), metadata.method().mimeType(),
                                        List.of(arg),
                                        metadata.method().declaringClassName(),
                                        metadata.method().returnType())),
                        singleArg, objectMapper);
                ret[idx] = single[0];
            }
            idx++;
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    protected static Object[] prepareArguments(MCPFeatureMetadata metadata, Map<String, JsonValue> args, ObjectMapper mapper) throws MCPException {
        if (metadata.arguments().isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[metadata.arguments().size()];
        int idx = 0;
        for (ArgumentMetadata arg : metadata.arguments()) {
            JsonValue val = args.get(arg.name());
            if (val == null && arg.required()) {
                throw new MCPException("Missing required argument: " + arg.name(), JsonRPC.INVALID_PARAMS);
            }
            if (val == null) {
                ret[idx] = null;
            } else {
                if (val.getValueType() == ValueType.OBJECT) {
                    // json object
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(val.toString(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (val.getValueType() == ValueType.ARRAY) {
                    // json array
                    JavaType javaType = mapper.getTypeFactory().constructType(arg.type());
                    try {
                        ret[idx] = mapper.readValue(val.toString(), javaType);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                } else {
                    if (arg.type() instanceof Class) {
                        Class clazz = (Class) arg.type();
                        if (clazz.isEnum()) {
                            ret[idx] = Enum.valueOf(clazz, ((JsonString) val).getString());
                        } else {
                            try {
                                ret[idx] = mapper.readValue(val.toString(), clazz);
                            } catch (JsonProcessingException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    } else {
                        ret[idx] = val.toString();
                    }
                }
            }
            idx++;
        }
        return ret;
    }
}
