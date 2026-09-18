/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp.conformance;

import static org.wildfly.ai.test.mcp.conformance.ConformanceFixtures.MINIMAL_PNG;
import static org.wildfly.ai.test.mcp.conformance.ConformanceFixtures.MINIMAL_WAV;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import java.util.List;

import org.mcpjava.server.content.AudioContent;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.tools.ToolResponse;
import org.wildfly.mcp.api.McpHeader;
import org.wildfly.mcp.api.ClientCapabilities;
import org.wildfly.mcp.api.ListChangeNotifier;
import org.wildfly.mcp.api.elicitation.Elicitation;
import org.wildfly.mcp.api.elicitation.ElicitationSender;
import org.wildfly.mcp.api.tool.InputRequiredResult;
import org.wildfly.mcp.api.tool.InputResponses;
import org.wildfly.mcp.api.tool.InputSchema;

public class ConformanceTool {

    private static JsonObjectBuilder requiredPropertySchema(String propertyName, String propertyType) {
        return Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                        .add(propertyName, Json.createObjectBuilder().add("type", propertyType)))
                .add("required", Json.createArrayBuilder().add(propertyName));
    }

    @Tool(name = "_test_custom_header", description = "Echoes custom header value for conformance testing")
    String testCustomHeader(@McpHeader("custom-key") String customKey) {
        return customKey != null ? customKey : "(no header)";
    }

    @Tool(name = "echo", description = "Echoes the input message back")
    String echo(@ToolArg(description = "The message to echo") String message) {
        return message;
    }

    @Tool(name = "add", description = "Adds two numbers together")
    int add(@ToolArg(description = "First number") int a, @ToolArg(description = "Second number") int b) {
        return a + b;
    }

    @Tool(name = "test_simple_text", description = "Returns a simple text response for conformance testing")
    String testSimpleText() {
        return "This is a simple text response for testing.";
    }

    @Tool(name = "test_image_content", description = "Returns image content for conformance testing")
    ToolResponse testImageContent() {
        return ToolResponse.builder()
                .addContent(ImageContent.of(MINIMAL_PNG, "image/png"))
                .build();
    }

    @Tool(name = "test_audio_content", description = "Returns audio content for conformance testing")
    ToolResponse testAudioContent() {
        return ToolResponse.builder()
                .addContent(AudioContent.of(MINIMAL_WAV, "audio/wav"))
                .build();
    }

    @Tool(name = "test_embedded_resource", description = "Returns embedded resource content for conformance testing")
    ToolResponse testEmbeddedResource() {
        return ToolResponse.builder()
                .addContent(EmbeddedResource.builder("This is an embedded resource content.",
                        "test://embedded-resource")
                        .setMimeType("text/plain")
                        .build())
                .build();
    }

    @Tool(name = "test_multiple_content_types", description = "Returns multiple content types for conformance testing")
    ToolResponse testMultipleContentTypes() {
        return ToolResponse.builder()
                .addContent(TextContent.of("Multiple content types test:"))
                .addContent(ImageContent.of(MINIMAL_PNG, "image/png"))
                .addContent(EmbeddedResource.builder("{\"test\":\"data\",\"value\":123}",
                        "test://mixed-content-resource")
                        .setMimeType("application/json")
                        .build())
                .build();
    }

    @Tool(name = "test_error_handling", description = "Always returns an error for conformance testing")
    ToolResponse testErrorHandling() {
        return ToolResponse.builder()
                .addTextContent("This tool intentionally returns an error for testing")
                .setError(true)
                .build();
    }

    @Tool(name = "test_tool_with_progress", description = "Reports progress notifications for conformance testing")
    ToolResponse testToolWithProgress(Progress progress) throws Exception {
        if (progress.token().isPresent()) {
            progress.notificationBuilder()
                    .setProgress(0).setTotal(100).setMessage("Starting")
                    .build().sendAndForget();
            Thread.sleep(50);
            progress.notificationBuilder()
                    .setProgress(50).setTotal(100).setMessage("Processing")
                    .build().sendAndForget();
            Thread.sleep(50);
            progress.notificationBuilder()
                    .setProgress(100).setTotal(100).setMessage("Complete")
                    .build().sendAndForget();
        }
        return ToolResponse.ofText("Progress test completed");
    }

    // TODO: implement log notifications once notifications/message API is available (baselined failure)
    @Tool(name = "test_tool_with_logging", description = "Sends log messages during execution for conformance testing")
    ToolResponse testToolWithLogging() {
        return ToolResponse.ofText("Logging test completed");
    }

    // NOTE: conformance testing only — do not echo user input in production (PII risk)
    @Tool(name = "test_elicitation", description = "Requests user input from the client for conformance testing")
    ToolResponse testElicitation(
            @ToolArg(description = "The message to show the user") String message,
            ElicitationSender elicitationSender) throws Exception {
        Elicitation.FormBuilder fb = Elicitation.formBuilder(message);
        fb.addString("username").description("User's response").required(true);
        fb.addString("email").description("User's email address").required(true);
        Elicitation.Response response = elicitationSender.send(fb.build());
        return ToolResponse.ofText("User response: action=" + response.action()
                + ", content=" + response.content());
    }

    @Tool(name = "test_elicitation_sep1034_defaults",
            description = "Requests elicitation with default values for all primitive types")
    ToolResponse testElicitationSep1034Defaults(ElicitationSender elicitationSender) throws Exception {
        Elicitation.FormBuilder fb = Elicitation.formBuilder(
                "Test default value handling - please accept with defaults");
        fb.addString("name").description("User name").defaultValue("John Doe").optional();
        fb.addInteger("age").description("User age").defaultValue(30).optional();
        fb.addNumber("score").description("User score").defaultValue(95.5).optional();
        fb.addEnum("status", "active", "inactive", "pending")
                .description("User status").defaultValue("active").optional();
        fb.addBoolean("verified").description("Verification status").defaultValue(true).optional();
        Elicitation.Response response = elicitationSender.send(fb.build());
        return ToolResponse.ofText("Elicitation completed: " + response.content());
    }

    @Tool(name = "test_elicitation_sep1330_enums",
            description = "Requests elicitation with enum schema improvements")
    ToolResponse testElicitationSep1330Enums(ElicitationSender elicitationSender) throws Exception {
        Elicitation.FormBuilder fb = Elicitation.formBuilder(
                "Test enum variants - please accept");
        fb.addEnum("untitledSingle", "option1", "option2", "option3")
                .description("Untitled single-select");
        fb.addEnum("titledSingle", "value1", "value2", "value3")
                .enumTitles(List.of("First Option", "Second Option", "Third Option"))
                .description("Titled single-select");
        fb.addEnum("legacyEnum", "opt1", "opt2", "opt3")
                .enumTitles(List.of("Option One", "Option Two", "Option Three"))
                .legacyFormat()
                .description("Legacy titled enum");
        fb.addMultiString("untitledMulti", "option1", "option2", "option3")
                .description("Untitled multi-select");
        fb.addMultiString("titledMulti", "value1", "value2", "value3")
                .enumTitles(List.of("First Choice", "Second Choice", "Third Choice"))
                .description("Titled multi-select");
        Elicitation.Response response = elicitationSender.send(fb.build());
        return ToolResponse.ofText("Elicitation completed: action=" + response.action()
                + ", content=" + response.content());
    }

    @Tool(name = "test_missing_capability", description = "Tool that requires sampling capability for conformance testing")
    String testMissingCapability(ClientCapabilities capabilities) {
        capabilities.requireCapability("sampling");
        return "This should only succeed when the client declares sampling capability";
    }

    @Tool(name = "test_streaming_elicitation", description = "Tool for streaming elicitation conformance testing")
    String testStreamingElicitation() {
        return "Streaming elicitation test completed";
    }

    @Tool(name = "test_logging_tool", description = "Tool for logging conformance testing")
    String testLoggingTool() {
        return "Logging tool test completed";
    }

    @Tool(name = "test_trigger_tool_change", description = "Triggers a tool list change notification")
    String testTriggerToolChange(ListChangeNotifier notifier) {
        notifier.notifyToolsChanged();
        return "Tool change triggered";
    }

    @Tool(name = "test_trigger_prompt_change", description = "Triggers a prompt list change notification")
    String testTriggerPromptChange(ListChangeNotifier notifier) {
        notifier.notifyPromptsChanged();
        return "Prompt change triggered";
    }

    @Tool(name = "json_schema_2020_12_tool", description = "Tool with JSON Schema 2020-12 features")
    @InputSchema(generator = JsonSchema2020_12SchemaGenerator.class)
    ToolResponse jsonSchema2020_12Tool(
            @ToolArg(description = "Name") String name,
            @ToolArg(description = "Contact method") String contactMethod,
            @ToolArg(description = "Phone number", required = false) String phone,
            @ToolArg(description = "Email address", required = false) String email) {
        return ToolResponse.ofText("JSON Schema 2020-12 tool called with name=" + name);
    }

    @Tool(name = "test_input_required_result_elicitation",
            description = "Tests basic elicitation InputRequiredResult flow (SEP-2322)")
    Object testInputRequiredResultElicitation(InputResponses input) {
        if (input.hasResponses() && input.responses().containsKey("user_name")) {
            return ToolResponse.ofText("Hello, user!");
        }
        return InputRequiredResult.builder()
                .addElicitation("user_name", "Please provide your name",
                        requiredPropertySchema("name", "string"))
                .build();
    }

    @Tool(name = "test_input_required_result_request_state",
            description = "Tests requestState round-trip in MRTR flow (SEP-2322)")
    Object testInputRequiredResultRequestState(InputResponses input) {
        if (!input.hasResponses()) {
            return InputRequiredResult.builder()
                    .addElicitation("confirm", "Please confirm the operation",
                            requiredPropertySchema("ok", "boolean"))
                    .requestState("mrtr-state")
                    .build();
        }
        return ToolResponse.ofText("state-ok");
    }

    @Tool(name = "test_input_required_result_multiple_inputs",
            description = "Tests multiple input requests of different types in a single InputRequiredResult (SEP-2322)")
    Object testInputRequiredResultMultipleInputs(InputResponses input) {
        if (input.hasResponses()
                && input.responses().containsKey("elicit_name")
                && input.responses().containsKey("sample_greeting")
                && input.responses().containsKey("list_roots")) {
            return ToolResponse.ofText("All inputs received");
        }
        return InputRequiredResult.builder()
                .addElicitation("elicit_name", "Please provide your name",
                        requiredPropertySchema("name", "string"))
                .addSampling("sample_greeting",
                        Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("role", "user")
                                        .add("content", Json.createObjectBuilder()
                                                .add("type", "text")
                                                .add("text", "Generate a greeting"))),
                        100)
                .addRootsList("list_roots")
                .requestState("multi-input-state")
                .build();
    }

    @Tool(name = "test_input_required_result_multi_round",
            description = "Tests multi-round ephemeral InputRequiredResult flow with evolving requestState (SEP-2322)")
    Object testInputRequiredResultMultiRound(InputResponses input) {
        if (!input.hasResponses()) {
            return InputRequiredResult.builder()
                    .addElicitation("step1", "What is your name?",
                            requiredPropertySchema("name", "string"))
                    .requestState("round-1")
                    .build();
        }
        String state = input.requestState();
        if (state != null && state.contains("round-1")) {
            return InputRequiredResult.builder()
                    .addElicitation("step2", "What is your favorite color?",
                            requiredPropertySchema("color", "string"))
                    .requestState("round-2")
                    .build();
        }
        return ToolResponse.ofText("Multi-round complete");
    }

    @Tool(name = "test_input_required_result_tampered_state",
            description = "Tests that server rejects tampered requestState (SEP-2322)")
    Object testInputRequiredResultTamperedState(InputResponses input) {
        if (!input.hasResponses()) {
            return InputRequiredResult.builder()
                    .addElicitation("confirm", "Please confirm",
                            requiredPropertySchema("ok", "boolean"))
                    .requestState("tamper-test-state")
                    .build();
        }
        return ToolResponse.ofText("Confirmed");
    }

    @Tool(name = "test_input_required_result_capabilities",
            description = "Tests that server only sends inputRequests for capabilities the client declared (SEP-2322)")
    Object testInputRequiredResultCapabilities(InputResponses input, ClientCapabilities capabilities) {
        if (input.hasResponses()) {
            return ToolResponse.ofText("Capabilities check complete");
        }
        InputRequiredResult.Builder builder = InputRequiredResult.builder();
        boolean hasAny = false;
        if (capabilities.hasCapability("elicitation")) {
            builder.addElicitation("elicit_data", "Please provide data",
                    requiredPropertySchema("data", "string"));
            hasAny = true;
        }
        if (capabilities.hasCapability("sampling")) {
            builder.addSampling("sample_data",
                    Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add("role", "user")
                                    .add("content", Json.createObjectBuilder()
                                            .add("type", "text")
                                            .add("text", "Generate data"))),
                    100);
            hasAny = true;
        }
        if (capabilities.hasCapability("roots")) {
            builder.addRootsList("list_roots");
            hasAny = true;
        }
        if (!hasAny) {
            return ToolResponse.ofText("No supported capabilities declared");
        }
        builder.requestState("capability-check-state");
        return builder.build();
    }

    @Tool(name = "test_reconnection", description = "Returns a simple response for SSE polling conformance testing")
    String testReconnection() {
        return "Reconnection test response";
    }
}
