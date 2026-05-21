/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcp_java.server.tools.Tool;
import org.mcp_java.server.tools.ToolArg;

public class TestMCPTool {

    @Tool(name = "add", description = "Adds two numbers together")
    int add(@ToolArg(description = "First number") int a, @ToolArg(description = "Second number") int b) {
        return a + b;
    }

    @Tool(name = "echo", description = "Echoes the input message")
    String echo(@ToolArg(description = "The message to echo") String message) {
        return message;
    }
}
