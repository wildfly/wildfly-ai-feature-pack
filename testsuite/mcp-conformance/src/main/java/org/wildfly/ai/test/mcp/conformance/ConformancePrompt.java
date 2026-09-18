/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp.conformance;

import static org.wildfly.ai.test.mcp.conformance.ConformanceFixtures.MINIMAL_PNG;

import jakarta.json.Json;

import org.mcpjava.server.Role;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;
import org.wildfly.mcp.api.tool.InputRequiredResult;
import org.wildfly.mcp.api.tool.InputResponses;

public class ConformancePrompt {

    @Prompt(name = "greeting", description = "Generates a greeting message")
    PromptResponse greeting(@PromptArg(description = "Name of the person to greet") String name) {
        return PromptResponse.of(Role.USER, TextContent.of("Hello, " + name + "!"));
    }

    @Prompt(name = "test_simple_prompt", description = "A simple prompt for conformance testing")
    PromptResponse testSimplePrompt() {
        return PromptResponse.of(Role.USER,
                TextContent.of("This is a simple prompt for testing."));
    }

    @Prompt(name = "test_prompt_with_arguments", description = "A prompt with arguments for conformance testing")
    PromptResponse testPromptWithArguments(
            @PromptArg(description = "First test argument") String arg1,
            @PromptArg(description = "Second test argument") String arg2) {
        return PromptResponse.of(Role.USER,
                TextContent.of("Prompt with arguments: arg1='" + arg1 + "', arg2='" + arg2 + "'"));
    }

    @Prompt(name = "test_prompt_with_embedded_resource",
            description = "A prompt with embedded resource for conformance testing")
    PromptResponse testPromptWithEmbeddedResource(
            @PromptArg(description = "URI of the resource to embed") String resourceUri) {
        return PromptResponse.builder()
                .addMessage(Role.USER, EmbeddedResource.builder(
                        "Embedded resource content for testing.", resourceUri)
                        .setMimeType("text/plain")
                        .build())
                .addMessage(Role.USER,
                        TextContent.of("Please process the embedded resource above."))
                .build();
    }

    @Prompt(name = "test_prompt_with_image",
            description = "A prompt with image content for conformance testing")
    PromptResponse testPromptWithImage() {
        return PromptResponse.builder()
                .addMessage(Role.USER, ImageContent.of(MINIMAL_PNG, "image/png"))
                .addMessage(Role.USER, TextContent.of("Please analyze the image above."))
                .build();
    }

    @Prompt(name = "test_input_required_result_prompt",
            description = "Tests InputRequiredResult from prompts/get (SEP-2322)")
    Object testInputRequiredResultPrompt(InputResponses input) {
        if (input.hasResponses()) {
            return PromptResponse.of(Role.USER,
                    TextContent.of("Prompt completed with input."));
        }
        return InputRequiredResult.builder()
                .addElicitation("context_input", "Please provide context",
                        Json.createObjectBuilder()
                                .add("type", "object")
                                .add("properties", Json.createObjectBuilder()
                                        .add("context", Json.createObjectBuilder().add("type", "string")))
                                .add("required", Json.createArrayBuilder().add("context")))
                .build();
    }
}
