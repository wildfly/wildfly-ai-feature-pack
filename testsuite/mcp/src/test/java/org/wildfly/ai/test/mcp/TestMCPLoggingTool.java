/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcp_java.server.tools.Tool;
import org.mcp_java.server.tools.ToolArg;
import org.mcp_java.server.McpLog;

public class TestMCPLoggingTool {

    @Tool(name = "log-test", description = "Tests logging by sending a log message at the specified level")
    String logTest(@ToolArg(description = "The level to log at: debug, info, or error") String level, McpLog log) {
        switch (level) {
            case "debug" -> log.debug("Debug message from tool");
            case "info" -> log.info("Info message from tool");
            case "error" -> log.error("Error message from tool");
            default -> log.info("Default message from tool");
        }
        return "Logged at " + level;
    }
}
