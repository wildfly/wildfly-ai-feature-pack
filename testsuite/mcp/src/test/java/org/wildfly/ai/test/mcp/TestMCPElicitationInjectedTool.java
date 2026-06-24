/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import jakarta.inject.Inject;

import org.mcpjava.server.tools.Tool;
import org.wildfly.mcp.api.elicitation.Elicitation;
import org.wildfly.mcp.api.elicitation.ElicitationSender;

public class TestMCPElicitationInjectedTool {

    @Inject
    ElicitationSender elicitationSender;

    @Tool(name = "greet-with-name-injected", description = "Asks the user for their name via CDI-injected elicitation and greets them")
    String greetWithName() throws Exception {
        if (!elicitationSender.isFormSupported()) {
            return "Elicitation Form Mode not supported by client";
        }
        Elicitation.FormBuilder form = Elicitation.formBuilder("What is your name?")
                .timeout(30_000);
        form.addString("name")
                .title("Your Name")
                .description("Enter your first name");
        Elicitation.Response response = elicitationSender.send(form.build());
        return switch (response.action()) {
            case ACCEPT -> "Hello, " + response.getString("name").orElse("stranger") + "!";
            case CANCEL -> "Request was cancelled.";
            case DECLINE -> "You declined to provide your name.";
        };
    }
}
