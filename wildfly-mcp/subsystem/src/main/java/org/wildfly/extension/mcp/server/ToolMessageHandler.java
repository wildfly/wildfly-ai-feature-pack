/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ANNOTATIONS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ARGUMENTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CONTENT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESCRIPTION;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESTRUCTIVE_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.IDEMPOTENT_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.INPUT_SCHEMA;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.META;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NAME;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NEXT_CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.OPEN_WORLD_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.OUTPUT_SCHEMA;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PROGRESS_TOKEN;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.READ_ONLY_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.STRUCTURED_CONTENT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TITLE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TOOLS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import static org.wildfly.extension.mcp.server.MCPServerUtils.SHARED_MAPPER;
import static org.wildfly.extension.mcp.server.MCPServerUtils.getRequestId;
import static org.wildfly.extension.mcp.server.MCPServerUtils.invokeViaReflection;
import static org.wildfly.extension.mcp.server.MCPServerUtils.prepareArguments;
import static org.wildfly.extension.mcp.server.MCPServerUtils.sendInvocationFailureResult;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.mcp_java.model.content.ContentBlock;
import org.mcp_java.model.tool.ToolAnnotations;
import org.mcp_java.server.progress.Progress;
import org.mcp_java.server.progress.ProgressToken;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSender;
import org.wildfly.extension.mcp.injection.resources.ResourceNotifier;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPTool;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.extension.mcp.injection.tool.ToolSchemaGenerator;
import org.wildfly.security.manager.WildFlySecurityManager;

public class ToolMessageHandler {

    private final SchemaGenerator schemaGenerator;
    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;
    // Deployment-scoped cache: populated once per tool on first tools/list, never invalidated.
    // Safe because this handler instance is created per deployment and discarded on undeploy/redeploy.
    private final Map<String, JsonObject> toolJsonCache = new ConcurrentHashMap<>();
    // Tools whose schema generation failed permanently for this deployment — skipped on every tools/list.
    private final Set<String> failedToolNames = ConcurrentHashMap.newKeySet();
    private final ResourceMessageHandler resourceHandler;


    ToolMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize,
            ResourceMessageHandler resourceHandler) {
        if (pageSize < 0) {
            throw ROOT_LOGGER.invalidPageSize(pageSize);
        }
        this.schemaGenerator = new SchemaGenerator(
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).build());
        this.registry = registry;
        this.mapper = SHARED_MAPPER;
        this.classLoader = classLoader;
        this.executorService = executorService;
        this.pageSize = pageSize;
        this.resourceHandler = resourceHandler;
    }

    /**
     * Handles a {@code tools/list} request: returns a paginated, sorted list of registered tools
     * with their input schemas, annotations, and optional output schemas.
     */
    void toolsList(JsonObject message, Responder responder) {
        String id = getRequestId(message);
        JsonObject params = message.getJsonObject(PARAMS);
        String cursorValue = params != null ? params.getString(CURSOR, null) : null;

        Cursor.Page<MCPFeatureMetadata> result = Cursor.paginate(registry.listTools(), cursorValue, pageSize, MCPFeatureMetadata::name);

        ROOT_LOGGER.debugf("List tools [id: %s, cursor: %s, pageSize: %d]", id, cursorValue, pageSize);

        JsonArrayBuilder tools = Json.createArrayBuilder();
        for (MCPFeatureMetadata toolMetadata : result.items()) {
            if (failedToolNames.contains(toolMetadata.name())) {
                continue;
            }
            try {
                tools.add(toolJsonCache.computeIfAbsent(toolMetadata.name(), k -> buildToolJson(toolMetadata)));
            } catch (RuntimeException e) {
                failedToolNames.add(toolMetadata.name());
                ROOT_LOGGER.errorSkippingToolFromListing(toolMetadata.name(), e.getMessage());
            }
        }
        JsonObjectBuilder resultBuilder = Json.createObjectBuilder().add(TOOLS, tools);
        if (result.nextCursor() != null) {
            resultBuilder.add(NEXT_CURSOR, result.nextCursor());
        }
        responder.sendResult(id, resultBuilder);
    }

    private JsonObject buildToolJson(MCPFeatureMetadata toolMetadata) {
        JsonObjectBuilder tool = Json.createObjectBuilder()
                .add(NAME, toolMetadata.name())
                .add(DESCRIPTION, toolMetadata.description());
        addInputSchema(tool, toolMetadata);
        addToolAnnotations(tool, toolMetadata.toolAnnotations());
        addOutputSchema(tool, toolMetadata);
        return tool.build();
    }

    private void addInputSchema(JsonObjectBuilder tool, MCPFeatureMetadata toolMetadata) {
        if (toolMetadata.inputSchemaGenerator().isPresent()) {
            JsonObject generated = resolveGeneratedSchema(toolMetadata.inputSchemaGenerator().get());
            if (generated != null) {
                tool.add(INPUT_SCHEMA, generated);
                return;
            }
        }
        JsonObjectBuilder properties = Json.createObjectBuilder();
        JsonArrayBuilder required = Json.createArrayBuilder();
        for (ArgumentMetadata a : toolMetadata.arguments()) {
            if (a.type() instanceof Class<?> clazz
                    && (ElicitationSender.class.isAssignableFrom(clazz)
                            || Progress.class.isAssignableFrom(clazz)
                            || ResourceNotifier.class.isAssignableFrom(clazz))) {
                continue; // injected by the framework, not a client-supplied argument
            }
            properties.add(a.name(), generateInputSchema(a.type(), a));
            if (a.required()) {
                required.add(a.name());
            }
        }
        tool.add(INPUT_SCHEMA, Json.createObjectBuilder()
                .add(TYPE, "object")
                .add("properties", properties)
                .add("required", required));
    }

    private void addToolAnnotations(JsonObjectBuilder tool, ToolAnnotations annotations) {
        if (annotations == null) {
            return;
        }
        if (annotations.title() != null && !annotations.title().isEmpty()) {
            tool.add(TITLE, annotations.title());
        }
        JsonObjectBuilder annBuilder = Json.createObjectBuilder();
        boolean hasAnnotation = false;
        if (annotations.readOnlyHint() != null) {
            annBuilder.add(READ_ONLY_HINT, annotations.readOnlyHint());
            hasAnnotation = true;
        }
        if (annotations.destructiveHint() != null) {
            annBuilder.add(DESTRUCTIVE_HINT, annotations.destructiveHint());
            hasAnnotation = true;
        }
        if (annotations.idempotentHint() != null) {
            annBuilder.add(IDEMPOTENT_HINT, annotations.idempotentHint());
            hasAnnotation = true;
        }
        if (annotations.openWorldHint() != null) {
            annBuilder.add(OPEN_WORLD_HINT, annotations.openWorldHint());
            hasAnnotation = true;
        }
        if (hasAnnotation) {
            tool.add(ANNOTATIONS, annBuilder);
        }
    }

    private void addOutputSchema(JsonObjectBuilder tool, MCPFeatureMetadata toolMetadata) {
        if (toolMetadata.outputSchemaGenerator().isPresent()) {
            JsonObject generated = resolveGeneratedSchema(toolMetadata.outputSchemaGenerator().get());
            if (generated != null) {
                tool.add(OUTPUT_SCHEMA, generated);
                return;
            }
        }
        if (toolMetadata.structuredContent()) {
            String outputSchemaType = toolMetadata.outputSchemaFrom().isPresent()
                    ? toolMetadata.outputSchemaFrom().get()
                    : toolMetadata.method().returnType();
            JsonObject outputSchema = generateOutputSchema(outputSchemaType);
            if (outputSchema != null) {
                tool.add(OUTPUT_SCHEMA, outputSchema);
            }
        }
    }

    /**
     * Resolves a {@link ToolSchemaGenerator} by class name, invokes {@code generate()},
     * and parses the result as a {@link JsonObject}.
     *
     * @return the generated schema, or {@code null} if resolution or generation fails
     */
    private JsonObject resolveGeneratedSchema(String generatorClassName) {
        try {
            Class<?> generatorClass = classLoader.loadClass(generatorClassName);
            if (!ToolSchemaGenerator.class.isAssignableFrom(generatorClass)) {
                ROOT_LOGGER.warnSchemaGeneratorInvalidType(generatorClassName);
                return null;
            }
            String schemaJson = invokeSchemaGenerator(generatorClass);
            try (var reader = Json.createReader(new StringReader(schemaJson))) {
                return reader.readObject();
            }
        } catch (Exception e) {
            ROOT_LOGGER.warnFailedToResolveSchemaGenerator(e, generatorClassName);
            return null;
        }
    }

    /**
     * Invokes a {@link ToolSchemaGenerator} via CDI if available, destroying the bean afterwards
     * to avoid leaking {@code @Dependent}-scoped instances. Falls back to direct instantiation
     * when CDI is unavailable.
     */
    @SuppressWarnings("unchecked")
    private String invokeSchemaGenerator(Class<?> generatorClass) throws Exception {
        try {
            Instance<ToolSchemaGenerator> instance = (Instance<ToolSchemaGenerator>) CDI.current().select(generatorClass);
            if (instance.isResolvable()) {
                ToolSchemaGenerator generator = instance.get();
                try {
                    return generator.generate();
                } finally {
                    instance.destroy(generator);
                }
            }
        } catch (IllegalStateException e) {
            // CDI.current() throws IllegalStateException when invoked outside a managed context
            ROOT_LOGGER.debugf("CDI not available for schema generator %s, falling back to direct instantiation: %s",
                    generatorClass.getName(), e.getMessage());
        }
        return ((ToolSchemaGenerator) generatorClass.getDeclaredConstructor().newInstance()).generate();
    }

    /**
     * Generates a JSON Schema for the given type, strips the {@code $schema} field, and
     * optionally applies a customizer to the schema node before converting to {@link JsonObject}.
     */
    private JsonObject generateSchema(Type type, Consumer<ObjectNode> customizer) {
        JsonNode jsonNode = schemaGenerator.generateSchema(type);
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.remove("$schema");
            if (customizer != null) {
                customizer.accept(objectNode);
            }
        }
        try (var reader = Json.createReader(new StringReader(jsonNode.toString()))) {
            return reader.readObject();
        }
    }

    /**
     * @return the generated output schema, or {@code null} if the return type cannot be loaded or schema generation fails
     */
    private JsonObject generateOutputSchema(String returnTypeName) {
        try {
            Class<?> returnType = classLoader.loadClass(returnTypeName);
            return generateSchema(returnType, null);
        } catch (Exception e) {
            ROOT_LOGGER.errorGeneratingOutputSchema(e, returnTypeName, e.getMessage());
            return null;
        }
    }

    private JsonObject generateInputSchema(Type type, ArgumentMetadata argument) {
        return generateSchema(type, node -> {
            if (argument.description() != null && !argument.description().isBlank()) {
                node.put(DESCRIPTION, argument.description());
            }
        });
    }

    /**
     * Handles a {@code tools/call} request: invokes the named tool asynchronously and sends back
     * the result as content blocks. If structured content is enabled for the tool, the raw return
     * value is also serialized as {@code structuredContent} alongside an {@code outputSchema}.
     */
    void toolsCall(JsonObject message, Responder responder, MCPConnection connection) {
        String id = getRequestId(message);
        JsonValue paramsValue = message.get(PARAMS);
        if (paramsValue == null || paramsValue.getValueType() != JsonValue.ValueType.OBJECT) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
        JsonObject params = paramsValue.asJsonObject();
        if (!params.containsKey(NAME)) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredArgument("name"));
            return;
        }
        String toolName = params.getString(NAME);
        ROOT_LOGGER.debugf("Call tool %s [id: %s]", toolName, id);
        Map<String, JsonValue> args = new HashMap<>();
        JsonObject arguments = params.getJsonObject(ARGUMENTS);
        if (arguments != null) {
            for (String key : arguments.keySet()) {
                args.put(key, arguments.get(key));
            }
        }
        ProgressToken progressToken = null;
        JsonObject meta = params.getJsonObject(META);
        if (meta != null && meta.containsKey(PROGRESS_TOKEN)) {
            JsonValue tokenVal = meta.get(PROGRESS_TOKEN);
            if (tokenVal.getValueType() == ValueType.STRING) {
                progressToken = new ProgressTokenImpl(((JsonString) tokenVal).getString());
            } else if (tokenVal.getValueType() == ValueType.NUMBER) {
                progressToken = new ProgressTokenImpl(((jakarta.json.JsonNumber) tokenVal).longValue());
            }
        }
        final ProgressToken finalProgressToken = progressToken;
        final MCPFeatureMetadata metadata = registry.getTool(toolName);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.invalidToolName(toolName));
            return;
        }
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            connection.task(executorService.submit(() -> {
                try {
                    MethodMetadata methodMetadata = metadata.method();
                    Class<?> clazz = classLoader.loadClass(methodMetadata.declaringClassName());
                    Instance<?> beanInstance = CDI.current().select(clazz, MCPTool.MCPToolLiteral.INSTANCE);
                    Object result = null;
                    Object[] builtArgs = buildArguments(metadata, args, mapper, connection, responder, finalProgressToken);
                    if (beanInstance.isResolvable()) {
                        ROOT_LOGGER.debugf("The Singleton instance of the tool %s has been found", toolName);
                        try {
                            if (builtArgs.length == 0) {
                                result = registry.getToolInvoker(toolName).invoke(beanInstance.get());
                            } else {
                                List<Object> preparedArguments = new ArrayList<>(Arrays.asList(builtArgs));
                                preparedArguments.add(0, beanInstance.get());
                                result = registry.getToolInvoker(toolName).invokeWithArguments(preparedArguments);
                            }
                        } catch (Throwable ex) {
                            ROOT_LOGGER.errorInvokingTool(ex, toolName);
                            sendInvocationFailureResult(id, ex, responder);
                            return;
                        }
                    } else {
                        ROOT_LOGGER.debugf("The Singleton instance for tool %s has not been found, using reflection instead", toolName);
                        Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                        result = invokeViaReflection(method, builtArgs);
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
                    builder.add(CONTENT, contentArray);
                    if (metadata.structuredContent() && result != null) {
                        try (StringWriter out = new StringWriter()) {
                            mapper.writeValue(out, result);
                            builder.add(STRUCTURED_CONTENT, Json.createReader(new StringReader(out.toString())).readValue());
                        } catch (IOException e) {
                            ROOT_LOGGER.errorSerializingStructuredContent(e, toolName);
                            sendInvocationFailureResult(id, e, responder);
                            return;
                        }
                    }
                    responder.sendResult(id, builder);
                } catch (MCPException e) {
                    MCPException.sendError(e, id, responder);
                } catch (IOException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException | IllegalArgumentException ex) {
                    // Infrastructure error: use a generic message to avoid leaking internal details
                    ROOT_LOGGER.errorInvokingTool(ex, toolName);
                    sendInvocationFailureResult(id, ex, responder);
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
            Responder responder,
            ProgressToken progressToken) throws MCPException {
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
            } else if (arg.type() instanceof Class<?> clazz && Progress.class.isAssignableFrom(clazz)) {
                ret[idx] = new ProgressImpl(progressToken, responder);
            } else if (arg.type() instanceof Class<?> clazz && ResourceNotifier.class.isAssignableFrom(clazz)) {
                ret[idx] = new ResourceNotifierImpl(resourceHandler);
            } else {
                JsonValue val = jsonArgs.get(arg.name());
                if (val == null && arg.required()) {
                    throw MCPException.missingRequiredArgument(arg.name());
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
