/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.mcp;

import org.mcp_java.server.tools.Tool;
import org.mcp_java.server.tools.ToolArg;

public class CalculatorTools {

    @Tool(name = "add", description = "Adds two numbers together")
    public int add(@ToolArg(description = "First number") int a,
                   @ToolArg(description = "Second number") int b) {
        return a + b;
    }

    @Tool(name = "multiply", description = "Multiplies two numbers together")
    public int multiply(@ToolArg(description = "First number") int a,
                        @ToolArg(description = "Second number") int b) {
        return a * b;
    }

    @Tool(name = "subtract", description = "Subtracts the second number from the first")
    public int subtract(@ToolArg(description = "Number to subtract from") int a,
                        @ToolArg(description = "Number to subtract") int b) {
        return a - b;
    }
}
