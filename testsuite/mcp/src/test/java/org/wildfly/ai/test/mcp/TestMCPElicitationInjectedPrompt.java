/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import java.util.List;

import jakarta.inject.Inject;

import org.mcp_java.server.prompts.Prompt;
import org.mcp_java.server.prompts.PromptArg;
import org.wildfly.mcp.model.content.TextContent;
import org.wildfly.mcp.model.elicitation.Elicitation;
import org.wildfly.mcp.model.elicitation.ElicitationSender;
import org.wildfly.mcp.model.prompt.PromptMessage;

public class TestMCPElicitationInjectedPrompt {

    @Inject
    ElicitationSender elicitationSender;

    @Prompt(name = "confirm-greeting", description = "Asks user to confirm before generating a greeting")
    PromptMessage confirmGreeting(@PromptArg(description = "Name to greet") String name) throws Exception {
        if (!elicitationSender.isFormSupported()) {
            return PromptMessage.user(List.of(TextContent.of("Hello, " + name + "!")));
        }
        Elicitation.FormBuilder form = Elicitation.formBuilder("Confirm greeting for " + name + "?")
                .timeout(30_000);
        form.addBoolean("confirm");
        Elicitation.Response response = elicitationSender.send(form.build());
        if (response.isAccepted() && response.getBoolean("confirm").orElse(false)) {
            return PromptMessage.user(List.of(TextContent.of("Confirmed greeting for " + name)));
        }
        return PromptMessage.user(List.of(TextContent.of("Greeting not confirmed")));
    }
}
