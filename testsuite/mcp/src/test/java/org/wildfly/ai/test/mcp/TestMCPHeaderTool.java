/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.wildfly.mcp.api.McpHeader;

public class TestMCPHeaderTool {

    @Tool(name = "greet-with-header", description = "Greets a user using a header-provided language")
    String greetWithHeader(
            @McpHeader("language") String language,
            @ToolArg(description = "Name to greet") String name) {
        String lang = language != null ? language : "en";
        return switch (lang) {
            case "fr" -> "Bonjour, " + name;
            case "es" -> "Hola, " + name;
            default -> "Hello, " + name;
        };
    }

    @Tool(name = "header-only-echo", description = "Echoes the value of a required header")
    String headerOnlyEcho(@McpHeader(value = "echo-value", required = true) String value) {
        return "echo: " + value;
    }
}
