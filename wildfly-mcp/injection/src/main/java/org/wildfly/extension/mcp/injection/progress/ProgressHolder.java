/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.progress;

import org.mcpjava.server.progress.Progress;

public final class ProgressHolder {

    private static final ThreadLocal<Progress> CURRENT = new ThreadLocal<>();

    private ProgressHolder() {
    }

    public static void set(Progress progress) {
        CURRENT.set(progress);
    }

    public static Progress get() {
        return CURRENT.get();
    }

    public static void remove() {
        CURRENT.remove();
    }
}
