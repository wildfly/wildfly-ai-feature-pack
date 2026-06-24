/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcpjava.server.Role;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.TextResourceContents;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/**
 * MCP-annotated beans providing enough tools, prompts, and resources to
 * exercise cursor-based pagination in the MCP server.
 *
 * <p>Registers 12 tools, 6 prompts, and 6 resources — enough to exceed any
 * reasonable default page size and force multi-page responses when pagination
 * is enabled on the server.</p>
 */
public class TestMCPPaginationFixtures {

    // ==================== Tools (12 total) ====================

    @Tool(name = "multiply", description = "Multiplies two numbers together")
    int multiply(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b) {
        return a * b;
    }

    @Tool(name = "divide", description = "Divides the first number by the second")
    String divide(
            @ToolArg(description = "Dividend") int a,
            @ToolArg(description = "Divisor") int b) {
        if (b == 0) {
            return "Cannot divide by zero";
        }
        return String.valueOf(a / (double) b);
    }

    @Tool(name = "subtract", description = "Subtracts the second number from the first")
    int subtract(
            @ToolArg(description = "Minuend") int a,
            @ToolArg(description = "Subtrahend") int b) {
        return a - b;
    }

    @Tool(name = "square", description = "Returns the square of a number")
    int square(@ToolArg(description = "The number to square") int n) {
        return n * n;
    }

    @Tool(name = "cube", description = "Returns the cube of a number")
    int cube(@ToolArg(description = "The number to cube") int n) {
        return n * n * n;
    }

    @Tool(name = "negate", description = "Returns the negation of a number")
    int negate(@ToolArg(description = "The number to negate") int n) {
        return -n;
    }

    @Tool(name = "absolute", description = "Returns the absolute value of a number")
    int absolute(@ToolArg(description = "The number") int n) {
        return Math.abs(n);
    }

    @Tool(name = "max-of-two", description = "Returns the maximum of two numbers")
    int maxOfTwo(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b) {
        return Math.max(a, b);
    }

    @Tool(name = "min-of-two", description = "Returns the minimum of two numbers")
    int minOfTwo(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b) {
        return Math.min(a, b);
    }

    @Tool(name = "is-even", description = "Returns whether a number is even")
    String isEven(@ToolArg(description = "The number to check") int n) {
        return Boolean.toString(n % 2 == 0);
    }

    @Tool(name = "power", description = "Raises a base number to the given exponent")
    double power(
            @ToolArg(description = "The base") int base,
            @ToolArg(description = "The exponent") int exp) {
        return Math.pow(base, exp);
    }

    @Tool(name = "modulo", description = "Returns the remainder after dividing the first number by the second")
    int modulo(
            @ToolArg(description = "Dividend") int a,
            @ToolArg(description = "Divisor") int b) {
        return a % b;
    }

    // ==================== Prompts (6 total) ====================

    @Prompt(name = "summarize", description = "Summarizes the provided text")
    PromptResponse summarize(@PromptArg(description = "Text to summarize") String text) {
        return PromptResponse.of(Role.USER, TextContent.of("Please summarize: " + text));
    }

    @Prompt(name = "translate", description = "Translates text into a target language")
    PromptResponse translate(
            @PromptArg(description = "Text to translate") String text,
            @PromptArg(description = "Target language") String language) {
        return PromptResponse.of(Role.USER, TextContent.of("Translate '" + text + "' into " + language));
    }

    @Prompt(name = "classify", description = "Classifies text into categories")
    PromptResponse classify(@PromptArg(description = "Text to classify") String text) {
        return PromptResponse.of(Role.USER, TextContent.of("Classify the following: " + text));
    }

    @Prompt(name = "analyze", description = "Analyzes text for sentiment and tone")
    PromptResponse analyze(@PromptArg(description = "Text to analyze") String text) {
        return PromptResponse.of(Role.ASSISTANT, TextContent.of("Sentiment analysis of: " + text));
    }

    @Prompt(name = "compare", description = "Compares two items and highlights differences")
    PromptResponse compare(
            @PromptArg(description = "First item") String first,
            @PromptArg(description = "Second item") String second) {
        return PromptResponse.of(Role.USER, TextContent.of("Compare '" + first + "' with '" + second + "'"));
    }

    @Prompt(name = "explain", description = "Explains a concept in plain language")
    PromptResponse explain(@PromptArg(description = "Concept to explain") String concept) {
        return PromptResponse.of(Role.ASSISTANT, TextContent.of("Here is a plain-language explanation of: " + concept));
    }

    // ==================== Resources (6 total) ====================

    @Resource(uri = "test://config", mimeType = "text/plain", name = "test-config")
    TextResourceContents config() {
        return TextResourceContents.of("test://config", "max-connections=100\ntimeout=30s\nretry-count=3");
    }

    @Resource(uri = "test://metrics", mimeType = "application/json", name = "test-metrics")
    TextResourceContents metrics() {
        return TextResourceContents.of("test://metrics", "{\"requests\":1000,\"errors\":5,\"latency_ms\":42}");
    }

    @Resource(uri = "test://health", mimeType = "application/json", name = "test-health")
    TextResourceContents health() {
        return TextResourceContents.of("test://health", "{\"status\":\"healthy\",\"uptime\":\"10d\"}");
    }

    @Resource(uri = "test://version", mimeType = "text/plain", name = "test-version")
    TextResourceContents version() {
        return TextResourceContents.of("test://version", "2.0.0");
    }

    @Resource(uri = "test://docs", mimeType = "text/plain", name = "test-docs")
    TextResourceContents docs() {
        return TextResourceContents.of("test://docs", "WildFly MCP Integration Test Documentation");
    }

    @Resource(uri = "test://logs", mimeType = "text/plain", name = "test-logs")
    TextResourceContents logs() {
        return TextResourceContents.of("test://logs", "2025-01-01 INFO Server started\n2025-01-01 INFO Ready to serve");
    }
}
