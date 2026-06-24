/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressTracker;

public class TestMCPProgressTool {

    @Tool(name = "progress-test", description = "Tests progress reporting by sending progress notifications")
    String progressTest(@ToolArg(description = "Number of steps to simulate") int steps, Progress progress) {
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

    @Tool(name = "progress-notification-test", description = "Tests individual progress notifications")
    String progressNotificationTest(Progress progress) {
        if (progress.token().isPresent()) {
            progress.notificationBuilder()
                    .setProgress(50)
                    .setTotal(100)
                    .setMessage("Halfway done")
                    .build()
                    .sendAndForget();
        }
        return "Done";
    }

    @Tool(name = "progress-no-token", description = "Tests progress when no token is provided")
    String progressNoToken(Progress progress) {
        return progress.token().isPresent() ? "has-token" : "no-token";
    }
}
