/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import jakarta.inject.Inject;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;
import org.wildfly.mcp.api.elicitation.Elicitation;
import org.wildfly.mcp.api.elicitation.ElicitationSender;

import static org.mcpjava.server.Role.USER;

public class TestMCPElicitationInjectedPrompt {

    @Inject
    ElicitationSender elicitationSender;

    @Prompt(name = "confirm-greeting", description = "Asks user to confirm before generating a greeting")
    PromptResponse confirmGreeting(@PromptArg(description = "Name to greet") String name) throws Exception {
        if (!elicitationSender.isFormSupported()) {
            return PromptResponse.of(USER, TextContent.of("Hello, " + name + "!"));
        }
        Elicitation.FormBuilder form = Elicitation.formBuilder("Confirm greeting for " + name + "?")
                .timeout(30_000);
        form.addBoolean("confirm");
        Elicitation.Response response = elicitationSender.send(form.build());
        if (response.isAccepted() && response.getBoolean("confirm").orElse(false)) {
            return PromptResponse.of(USER, TextContent.of("Confirmed greeting for " + name));
        }
        return PromptResponse.of(USER, TextContent.of("Greeting not confirmed"));
    }
}
