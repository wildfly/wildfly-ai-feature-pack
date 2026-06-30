/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.progress;

import java.util.Optional;

import jakarta.enterprise.context.RequestScoped;

import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressNotification;
import org.mcpjava.server.progress.ProgressToken;
import org.mcpjava.server.progress.ProgressTracker;

import static org.wildfly.extension.mcp.injection.MCPLogger.ROOT_LOGGER;

@RequestScoped
public class ProgressBean implements Progress {

    @Override
    public Optional<ProgressToken> token() {
        return delegate().token();
    }

    @Override
    public ProgressNotification.Builder notificationBuilder() {
        return delegate().notificationBuilder();
    }

    @Override
    public ProgressTracker.Builder trackerBuilder() {
        return delegate().trackerBuilder();
    }

    private Progress delegate() {
        Progress progress = ProgressHolder.get();
        if (progress == null) {
            throw ROOT_LOGGER.progressNotAvailable();
        }
        return progress;
    }
}
