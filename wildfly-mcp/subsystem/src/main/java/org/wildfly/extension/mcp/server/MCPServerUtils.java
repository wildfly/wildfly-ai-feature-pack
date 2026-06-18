/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.injection.MCPFieldNames.CONTENT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.IS_ERROR;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TEXT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;

/**
 * Package-private utilities shared across MCP message handler classes.
 */
final class MCPServerUtils {

    /** Shared, thread-safe Jackson mapper used by all message handlers in this package. */
    static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    private MCPServerUtils() {
    }

    /**
     * Returns the JSON-RPC {@code id} field as its JSON string representation, or {@code null}
     * if the message has no {@code id} field (e.g. a notification).
     */
    static String getRequestId(JsonObject message) {
        JsonValue idVal = message.get("id");
        return idVal != null ? idVal.toString() : null;
    }

    /**
     * Invokes {@code method} with the given args, instantiating the declaring class via its
     * no-arg constructor if the method is not static.
     */
    static Object invokeViaReflection(Method method, Object[] args)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        if (Modifier.isStatic(method.getModifiers())) {
            return method.invoke(null, args);
        }
        Object instance = method.getDeclaringClass().getConstructor().newInstance();
        return method.invoke(instance, args);
    }

    @SuppressWarnings("unchecked")
    static Object[] prepareArguments(List<ArgumentMetadata> arguments, Map<String, JsonValue> args, ObjectMapper mapper) throws MCPException {
        if (arguments.isEmpty()) {
            return new Object[0];
        }
        Object[] ret = new Object[arguments.size()];
        int idx = 0;
        for (ArgumentMetadata arg : arguments) {
            JsonValue val = args.get(arg.name());
            if (val == null && arg.required()) {
                throw MCPException.missingRequiredArgument(arg.name());
            }
            if (val == null) {
                ret[idx] = null;
            } else {
                if (val.getValueType() == ValueType.OBJECT || val.getValueType() == ValueType.ARRAY) {
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

    /**
     * Sends an MCP invocation result with {@code isError: true}.
     * <p>
     * Unlike {@link Responder#sendError} which sends a JSON-RPC error response, this sends a
     * successful JSON-RPC result whose content describes the failure — following the MCP convention
     * for reporting invocation errors back to the model.
     */
    static void sendInvocationFailureResult(String id, Throwable cause, Responder responder) {
        String message = cause != null && cause.getMessage() != null
                ? cause.getMessage()
                : (cause != null ? cause.getClass().getName() : "Unknown error");
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(CONTENT, Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add(TYPE, TEXT)
                        .add(TEXT, message)));
        builder.add(IS_ERROR, true);
        responder.sendResult(id, builder);
    }
}
