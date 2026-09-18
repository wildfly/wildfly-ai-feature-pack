/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.tool;

import jakarta.json.JsonObject;
import java.util.Map;

/**
 * Provides access to the {@code inputResponses} and {@code requestState} sent by
 * the client in a multi-round tool request (MRTR / SEP-2322).
 *
 * <p>Inject this as a tool parameter to participate in the MRTR flow:</p>
 * <pre>{@code
 * @Tool(name = "my_tool")
 * Object myTool(InputResponses input) {
 *     if (!input.hasResponses()) {
 *         return InputRequiredResult.builder()
 *             .addElicitation("confirm", "Please confirm", ...)
 *             .requestState("some-state")
 *             .build();
 *     }
 *     // use input.responses() and input.requestState()
 *     return ToolResponse.ofText("done");
 * }
 * }</pre>
 */
public interface InputResponses {

    /**
     * Returns {@code true} if the client provided {@code inputResponses} in this call.
     */
    boolean hasResponses();

    /**
     * Returns the client-provided input responses, keyed by the request key
     * from the previous {@code InputRequiredResult.inputRequests} map.
     * Each value is the raw JSON object sent by the client.
     */
    Map<String, JsonObject> responses();

    /**
     * Returns the {@code requestState} string echoed back by the client,
     * or {@code null} if none was provided.
     */
    String requestState();
}
