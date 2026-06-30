/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import jakarta.inject.Inject;

import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressTracker;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.ResourceContents;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.resources.TextResourceContents;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

import static org.mcpjava.server.Role.USER;

public class TestMCPInjectedProgress {

    @Inject
    Progress progress;

    @Tool(name = "progress-injected-test", description = "Tests progress reporting via CDI-injected Progress")
    String progressTest(@ToolArg(description = "Number of steps to simulate") int steps) {
        if (progress.token().isPresent()) {
            ProgressTracker tracker = progress.trackerBuilder()
                    .setTotal(steps)
                    .build();
            for (int i = 0; i < steps; i++) {
                tracker.advanceAndForget();
            }
        }
        return "Completed " + steps + " steps";
    }

    @Tool(name = "progress-injected-no-token", description = "Tests injected progress when no token is provided")
    String progressNoToken() {
        return progress.token().isPresent() ? "has-token" : "no-token";
    }

    @Prompt(name = "progress-prompt", description = "A prompt that sends progress notifications")
    PromptResponse progressPrompt(@PromptArg(description = "Name") String name) {
        if (progress.token().isPresent()) {
            progress.notificationBuilder()
                    .setProgress(1)
                    .setTotal(1)
                    .setMessage("Generating greeting")
                    .build()
                    .sendAndForget();
        }
        return PromptResponse.of(USER, TextContent.of("Hello, " + name + "!"));
    }

    @ResourceTemplate(uriTemplate = "test://progress/{id}", mimeType = "text/plain", name = "progress-resource")
    ResourceContents progressResource(@ResourceTemplateArg(name = "id") String id) {
        if (progress.token().isPresent()) {
            progress.notificationBuilder()
                    .setProgress(1)
                    .setTotal(1)
                    .setMessage("Loading resource")
                    .build()
                    .sendAndForget();
        }
        return TextResourceContents.of("test://progress/" + id, "data-for-" + id);
    }
}
