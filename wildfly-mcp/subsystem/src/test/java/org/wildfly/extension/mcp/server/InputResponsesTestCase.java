/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.Test;
import org.wildfly.mcp.api.tool.InputResponses;

public class InputResponsesTestCase {

    @Test
    public void testDecodedRequestStateIsInjectedInsteadOfToken() {
        JsonObject params = Json.createObjectBuilder()
                .add("requestState", "opaque-signed-token")
                .add("inputResponses", Json.createObjectBuilder()
                        .add("confirm", Json.createObjectBuilder().add("ok", true)))
                .build();
        RequestStateCodec.DecodedState decodedState = new RequestStateCodec.DecodedState(
                Json.createObjectBuilder().add("toolState", "server-state").build(),
                "user", "request-1", "tool");

        InputResponses inputResponses = ToolMessageHandler.parseInputResponses(params, decodedState);

        assertTrue(inputResponses.hasResponses());
        assertTrue(inputResponses.responses().get("confirm").getBoolean("ok"));
        assertEquals("server-state", inputResponses.requestState());
    }
}
