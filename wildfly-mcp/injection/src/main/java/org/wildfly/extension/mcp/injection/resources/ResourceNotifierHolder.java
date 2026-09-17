/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.resources;

public final class ResourceNotifierHolder {

    private static final ThreadLocal<ResourceNotifier> CURRENT = new ThreadLocal<>();

    private ResourceNotifierHolder() {
    }

    public static void set(ResourceNotifier notifier) {
        CURRENT.set(notifier);
    }

    public static ResourceNotifier get() {
        return CURRENT.get();
    }

    public static void remove() {
        CURRENT.remove();
    }
}