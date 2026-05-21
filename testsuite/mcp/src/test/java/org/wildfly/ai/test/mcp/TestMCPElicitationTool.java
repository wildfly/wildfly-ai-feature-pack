/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcp_java.server.tools.Tool;
import org.mcp_java.server.tools.ToolArg;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationRequest;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationResponse;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSender;
import org.wildfly.extension.mcp.injection.elicitation.StringSchema;

public class TestMCPElicitationTool {

    @Tool(name = "greet-with-name", description = "Asks the user for their name via elicitation and greets them")
    String greetWithName(ElicitationSender elicitationSender) throws Exception {
        if (!elicitationSender.isSupported()) {
            return "Elicitation not supported by client";
        }
        ElicitationRequest request = ElicitationRequest.builder("What is your name?")
                .addSchemaProperty("name", new StringSchema(true, "Your Name", "Enter your first name"))
                .timeout(30_000)
                .build();
        ElicitationResponse response = elicitationSender.send(request);
        if (response.isAccepted()) {
            return "Hello, " + response.getString("name") + "!";
        } else if (response.isDeclined()) {
            return "You declined to provide your name.";
        } else {
            return "Request was cancelled.";
        }
    }

    @Tool(name = "add-with-confirmation", description = "Adds two numbers after user confirms the operation")
    String addWithConfirmation(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b,
            ElicitationSender elicitationSender) throws Exception {
        if (!elicitationSender.isSupported()) {
            return String.valueOf(a + b);
        }
        ElicitationRequest request = ElicitationRequest.builder("Confirm: add " + a + " + " + b + "?")
                .addSchemaProperty("confirm", new org.wildfly.extension.mcp.injection.elicitation.BooleanSchema(true))
                .timeout(30_000)
                .build();
        ElicitationResponse response = elicitationSender.send(request);
        if (response.isAccepted() && Boolean.TRUE.equals(response.getBoolean("confirm"))) {
            return "Result: " + (a + b);
        }
        return "Operation not confirmed.";
    }
}
