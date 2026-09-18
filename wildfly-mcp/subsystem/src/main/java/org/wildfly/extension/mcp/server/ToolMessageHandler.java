/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.JsonRPC.INVALID_PARAMS;
import static org.wildfly.extension.mcp.api.MCPMethods.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ANNOTATIONS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ARGUMENTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CONTENT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESCRIPTION;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESTRUCTIVE_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.IDEMPOTENT_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.INPUT_REQUESTS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.INPUT_REQUIRED;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.INPUT_RESPONSES;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.INPUT_SCHEMA;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.META;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NAME;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NEXT_CURSOR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.OPEN_WORLD_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.OUTPUT_SCHEMA;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PARAMS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.READ_ONLY_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.REQUEST_STATE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.RESULT_TYPE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.STRUCTURED_CONTENT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TITLE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TOOLS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TOOL_STATE;
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
import static org.wildfly.extension.mcp.server.MCPServerUtils.asJsonObject;
import static org.wildfly.extension.mcp.server.MCPServerUtils.runWithCDIContext;
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
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.mcpjava.server.content.ContentBlock;
import org.mcpjava.server.tools.ToolResponse;
import org.wildfly.extension.mcp.injection.tool.ToolAnnotations;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressToken;
import org.wildfly.extension.mcp.api.ContentMapper;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.RequestMetadata;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.mcp.api.elicitation.ElicitationSender;
import org.wildfly.mcp.api.tool.InputRequiredResult;
import org.wildfly.mcp.api.tool.InputResponses;
import org.wildfly.extension.mcp.injection.capabilities.ClientCapabilitiesHolder;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSenderHolder;
import org.wildfly.extension.mcp.injection.input.InputResponsesHolder;
import org.wildfly.extension.mcp.injection.listchange.ListChangeNotifierHolder;
import org.wildfly.extension.mcp.injection.progress.ProgressHolder;
import org.wildfly.mcp.api.ClientCapabilities;
import org.wildfly.mcp.api.ListChangeNotifier;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.mcp.api.MissingCapabilityException;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPTool;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.extension.mcp.injection.tool.ToolSchemaGenerator;
import org.wildfly.security.manager.WildFlySecurityManager;

public class ToolMessageHandler {

    private static final String REQUEST_STATE_META_KEY = "requestState";
    static final String ALLOW_UNSIGNED_REQUEST_STATE_PROPERTY = "org.wildfly.extension.mcp.allow-unsigned-request-state";

    private final SchemaGenerator schemaGenerator;
    private final WildFlyMCPRegistry registry;
    private final ObjectMapper mapper;
    private final ClassLoader classLoader;
    private final ExecutorService executorService;
    private final int pageSize;
    private final RequestStateCodec requestStateCodec;
    private volatile ListChangeNotifier listChangeNotifier;
    // Deployment-scoped cache: populated once per tool on first tools/list, never invalidated.
    // Safe because this handler instance is created per deployment and discarded on undeploy/redeploy.
    private final Map<String, JsonObject> toolJsonCache = new ConcurrentHashMap<>();
    // Tools whose schema generation failed permanently for this deployment — skipped on every tools/list.
    private final Set<String> failedToolNames = ConcurrentHashMap.newKeySet();

    ToolMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize) {
        this(registry, classLoader, executorService, pageSize, null);
    }

    ToolMessageHandler(WildFlyMCPRegistry registry, ClassLoader classLoader, ExecutorService executorService, int pageSize, RequestStateCodec requestStateCodec) {
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
        this.requestStateCodec = requestStateCodec;
    }

    void setListChangeNotifier(ListChangeNotifier notifier) {
        this.listChangeNotifier = notifier;
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
            if (a.isHeader()) {
                JsonObject propSchema = generateInputSchema(a.type(), a);
                JsonObjectBuilder enhanced = Json.createObjectBuilder(propSchema)
                        .add("x-mcp-header", a.headerName());
                properties.add(a.headerName(), enhanced);
                if (a.required()) {
                    required.add(a.headerName());
                }
                continue;
            }
            if (a.type() instanceof Class<?> clazz
                    && (ElicitationSender.class.isAssignableFrom(clazz)
                            || Progress.class.isAssignableFrom(clazz)
                            || ClientCapabilities.class.isAssignableFrom(clazz)
                            || ListChangeNotifier.class.isAssignableFrom(clazz)
                            || InputResponses.class.isAssignableFrom(clazz))) {
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
    void toolsCall(JsonObject message, Responder responder, MCPConnection connection,
            RequestStateCodec.DecodedState decodedRequestState, Map<String, String> mcpHeaders,
            RequestMetadata requestMetadata) {
        String id = getRequestId(message);
        JsonObject params = asJsonObject(message.get(PARAMS));
        if (params == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.missingRequiredMessage());
            return;
        }
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
        final InputResponses inputResponses;
        try {
            inputResponses = parseInputResponses(params, decodedRequestState, requestStateCodec != null);
        } catch (IllegalArgumentException e) {
            MCPServerUtils.sendInvalidParamsError(e, id, responder);
            return;
        }

        Map<String, String> effectiveHeaders = mergeHeaders(mcpHeaders, params);
        final ProgressToken finalProgressToken = MCPServerUtils.extractProgressToken(params);
        final MCPFeatureMetadata metadata = registry.getTool(toolName);
        if (metadata == null) {
            responder.sendError(id, INVALID_PARAMS, ROOT_LOGGER.invalidToolName(toolName));
            return;
        }
        String currentPrincipal = MCPServerUtils.currentPrincipalName();
        if (decodedRequestState != null) {
            String tokenPrincipal = decodedRequestState.principal();
            if (tokenPrincipal != null && !tokenPrincipal.isEmpty()
                    && !tokenPrincipal.equals(currentPrincipal)) {
                responder.sendError(id, INVALID_PARAMS,
                        "requestState principal mismatch");
                return;
            }
            String tokenToolName = decodedRequestState.toolName();
            if (tokenToolName != null && !tokenToolName.isEmpty()
                    && !tokenToolName.equals(toolName)) {
                responder.sendError(id, INVALID_PARAMS,
                        "requestState tool binding mismatch");
                return;
            }
        }
        final ClassLoader prevCL = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            connection.task(executorService.submit(() -> runWithCDIContext(connection, responder, finalProgressToken, requestMetadata, listChangeNotifier, inputResponses, () -> {
                try {
                    MethodMetadata methodMetadata = metadata.method();
                    Class<?> clazz = classLoader.loadClass(methodMetadata.declaringClassName());
                    Instance<?> beanInstance = CDI.current().select(clazz, MCPTool.MCPToolLiteral.INSTANCE);
                    Object result = null;
                    Object[] builtArgs = buildArguments(metadata, args, effectiveHeaders, mapper);
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
                            Throwable cause = ex;
                            while (cause.getCause() != null && cause.getCause() != cause) {
                                cause = cause.getCause();
                            }
                            if (cause instanceof MissingCapabilityException mce) {
                                JsonObjectBuilder reqCaps = Json.createObjectBuilder()
                                        .add(mce.capability(), Json.createObjectBuilder());
                                JsonObjectBuilder data = Json.createObjectBuilder()
                                        .add("requiredCapabilities", reqCaps);
                                responder.send(Messages.newErrorWithData(id,
                                        MISSING_REQUIRED_CLIENT_CAPABILITY,
                                        mce.getMessage(), data));
                            } else if (cause instanceof IllegalArgumentException iae) {
                                MCPServerUtils.sendInvalidParamsError(iae, id, responder);
                            } else {
                                ROOT_LOGGER.errorInvokingTool(ex, toolName);
                                sendInvocationFailureResult(id, ex, responder);
                            }
                            return;
                        }
                    } else {
                        ROOT_LOGGER.debugf("The Singleton instance for tool %s has not been found, using reflection instead", toolName);
                        Method method = clazz.getMethod(methodMetadata.name(), methodMetadata.argumentTypes());
                        result = invokeViaReflection(method, builtArgs);
                    }
                    if (result instanceof InputRequiredResult irr) {
                        InitializeRequest initReq = connection.initializeRequest();
                        for (Map.Entry<String, JsonObject> entry : irr.inputRequests().entrySet()) {
                            String inputMethod = entry.getValue().getString("method", "");
                            String requiredCapability = inputRequestMethodToCapability(inputMethod);
                            if (requiredCapability != null && !hasClientCapability(initReq, requiredCapability)) {
                                JsonObjectBuilder reqCaps = Json.createObjectBuilder()
                                        .add(requiredCapability, Json.createObjectBuilder());
                                JsonObjectBuilder capData = Json.createObjectBuilder()
                                        .add("requiredCapabilities", reqCaps);
                                responder.send(Messages.newErrorWithData(id,
                                        MISSING_REQUIRED_CLIENT_CAPABILITY,
                                        "Client does not support required capability: " + requiredCapability, capData));
                                return;
                            }
                        }
                        JsonObjectBuilder builder = Json.createObjectBuilder();
                        builder.add(RESULT_TYPE, INPUT_REQUIRED);
                        JsonObjectBuilder inputRequestsBuilder = Json.createObjectBuilder();
                        for (Map.Entry<String, JsonObject> entry : irr.inputRequests().entrySet()) {
                            inputRequestsBuilder.add(entry.getKey(), entry.getValue());
                        }
                        builder.add(INPUT_REQUESTS, inputRequestsBuilder);
                        if (irr.requestState() != null) {
                            builder.add(REQUEST_STATE, MCPServerUtils.encodeMrtrRequestState(
                                    irr.requestState(), id, toolName, requestStateCodec));
                        }
                        builder.add(CONTENT, Json.createArrayBuilder());
                        responder.sendResult(id, builder);
                        return;
                    }
                    JsonArrayBuilder contentArray = Json.createArrayBuilder();
                    JsonObjectBuilder builder = Json.createObjectBuilder();
                    if (result instanceof ToolResponse tr) {
                        for (var contentBlock : tr.content()) {
                            contentArray.add(ContentMapper.contentBlockToJson(contentBlock));
                        }
                        builder.add(CONTENT, contentArray);
                        if (tr.isError()) {
                            builder.add("isError", true);
                        }
                        tr.structuredContent().ifPresent(sc -> {
                            try (StringWriter out = new StringWriter()) {
                                mapper.writeValue(out, sc);
                                builder.add(STRUCTURED_CONTENT, Json.createReader(new StringReader(out.toString())).readValue());
                            } catch (IOException e) {
                                ROOT_LOGGER.errorSerializingStructuredContent(e, toolName);
                            }
                        });
                        encodeRequestState(tr, id, toolName, builder);
                    } else {
                        Collection<? extends ContentBlock> content = ContentMapper.processResultAsText(result);
                        for (var contentBlock : content) {
                            contentArray.add(ContentMapper.contentBlockToJson(contentBlock));
                        }
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
                    }
                    responder.sendResult(id, builder);
                } catch (IllegalArgumentException e) {
                    MCPServerUtils.sendInvalidParamsError(e, id, responder);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException ex) {
                    ROOT_LOGGER.errorInvokingTool(ex, toolName);
                    sendInvocationFailureResult(id, ex, responder);
                }
            })));
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(prevCL);
        }
    }

    /**
     * Like {@link #prepareArguments} but additionally injects framework-managed parameters
     * such as {@link ElicitationSender} and {@link Progress} based on the declared argument type.
     */
    private Object[] buildArguments(
            MCPFeatureMetadata metadata,
            Map<String, JsonValue> jsonArgs,
            Map<String, String> mcpHeaders,
            ObjectMapper objectMapper) {
        if (metadata.arguments().isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[metadata.arguments().size()];
        int idx = 0;
        for (ArgumentMetadata arg : metadata.arguments()) {
            if (arg.type() instanceof Class<?> clazz && ElicitationSender.class.isAssignableFrom(clazz)) {
                ret[idx] = ElicitationSenderHolder.get();
            } else if (arg.type() instanceof Class<?> clazz && Progress.class.isAssignableFrom(clazz)) {
                ret[idx] = ProgressHolder.get();
            } else if (arg.type() instanceof Class<?> clazz && ClientCapabilities.class.isAssignableFrom(clazz)) {
                ret[idx] = ClientCapabilitiesHolder.get();
            } else if (arg.type() instanceof Class<?> clazz && ListChangeNotifier.class.isAssignableFrom(clazz)) {
                ret[idx] = ListChangeNotifierHolder.get();
            } else if (arg.type() instanceof Class<?> clazz && InputResponses.class.isAssignableFrom(clazz)) {
                ret[idx] = InputResponsesHolder.get();
            } else if (arg.isHeader()) {
                String headerValue = mcpHeaders.get(arg.headerName());
                if (headerValue == null) {
                    JsonValue bodyValue = jsonArgs.get(arg.headerName());
                    if (bodyValue != null && bodyValue.getValueType() == JsonValue.ValueType.STRING) {
                        headerValue = ((JsonString) bodyValue).getString();
                    }
                }
                if (headerValue == null && arg.required()) {
                    throw new IllegalArgumentException(ROOT_LOGGER.missingRequiredArgument("header: " + arg.headerName()));
                }
                ret[idx] = headerValue;
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

    private void encodeRequestState(ToolResponse tr, String requestId, String toolName, JsonObjectBuilder builder) {
        if (requestStateCodec == null) {
            return;
        }
        Map<String, Object> meta = tr.metadata();
        if (meta == null || !meta.containsKey(REQUEST_STATE_META_KEY)) {
            return;
        }
        Object stateObj = meta.get(REQUEST_STATE_META_KEY);
        JsonObject state;
        if (stateObj instanceof JsonObject jo) {
            state = jo;
        } else {
            try (var reader = Json.createReader(new StringReader(mapper.writeValueAsString(stateObj)))) {
                state = reader.readObject();
            } catch (Exception e) {
                ROOT_LOGGER.debugf(e, "Failed to serialize requestState metadata to JSON");
                return;
            }
        }
        String token = requestStateCodec.encode(state, MCPServerUtils.currentPrincipalName(), requestId, toolName);
        builder.add(META, Json.createObjectBuilder().add(REQUEST_STATE_META_KEY, token));
    }

    private static String inputRequestMethodToCapability(String method) {
        return switch (method) {
            case "elicitation/create" -> ClientCapability.ELICITATION;
            case "sampling/createMessage" -> "sampling";
            case "roots/list" -> "roots";
            default -> null;
        };
    }

    private static boolean hasClientCapability(InitializeRequest initReq, String capabilityName) {
        if (initReq == null || initReq.clientCapabilities() == null) {
            return false;
        }
        return initReq.clientCapabilities().stream()
                .anyMatch(c -> capabilityName.equals(c.name()));
    }

    private static Map<String, String> mergeHeaders(Map<String, String> httpHeaders, JsonObject params) {
        Map<String, String> merged = new HashMap<>();
        JsonObject meta = params.getJsonObject(META);
        if (meta != null) {
            JsonObject jsonHeaders = meta.getJsonObject("headers");
            if (jsonHeaders != null) {
                for (String key : jsonHeaders.keySet()) {
                    JsonValue val = jsonHeaders.get(key);
                    if (val.getValueType() == JsonValue.ValueType.STRING) {
                        merged.put(key, ((JsonString) val).getString());
                    }
                }
            }
        }
        if (httpHeaders != null) {
            merged.putAll(httpHeaders);
        }
        return Collections.unmodifiableMap(merged);
    }

    static InputResponses parseInputResponses(JsonObject params, RequestStateCodec.DecodedState decodedRequestState) {
        return parseInputResponses(params, decodedRequestState, true);
    }

    static InputResponses parseInputResponses(JsonObject params, RequestStateCodec.DecodedState decodedRequestState,
                                              boolean codecConfigured) {
        Map<String, JsonObject> responses = null;
        JsonValue irValue = params.get(INPUT_RESPONSES);
        if (irValue != null && irValue.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject inputResponsesJson = irValue.asJsonObject();
            responses = new HashMap<>();
            for (String key : inputResponsesJson.keySet()) {
                JsonValue val = inputResponsesJson.get(key);
                if (val != null && val.getValueType() == JsonValue.ValueType.OBJECT) {
                    responses.put(key, val.asJsonObject());
                }
            }
        }
        String requestState;
        if (decodedRequestState != null) {
            requestState = decodedRequestState.state().getString(TOOL_STATE, null);
        } else {
            requestState = null;
            JsonValue rsValue = params.get(REQUEST_STATE);
            if (rsValue != null && rsValue.getValueType() == JsonValue.ValueType.STRING) {
                if (!codecConfigured) {
                    if (Boolean.getBoolean(ALLOW_UNSIGNED_REQUEST_STATE_PROPERTY)) {
                        ROOT_LOGGER.unsignedRequestStateAccepted();
                    } else {
                        ROOT_LOGGER.unsignedRequestStateRejected();
                        throw new IllegalArgumentException(
                                "Unsigned requestState rejected — configure a request-state-secret "
                                        + "or set system property " + ALLOW_UNSIGNED_REQUEST_STATE_PROPERTY
                                        + "=true to accept unsigned tokens");
                    }
                }
                requestState = ((JsonString) rsValue).getString();
            }
        }
        final Map<String, JsonObject> finalResponses = responses;
        final String finalRequestState = requestState;
        return new InputResponses() {
            @Override
            public boolean hasResponses() {
                return finalResponses != null && !finalResponses.isEmpty();
            }
            @Override
            public Map<String, JsonObject> responses() {
                return finalResponses != null ? Collections.unmodifiableMap(finalResponses) : Map.of();
            }
            @Override
            public String requestState() {
                return finalRequestState;
            }
        };
    }

}
