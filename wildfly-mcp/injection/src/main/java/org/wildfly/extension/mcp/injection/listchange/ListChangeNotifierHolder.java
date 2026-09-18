/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.listchange;

import org.wildfly.mcp.api.ListChangeNotifier;

public final class ListChangeNotifierHolder {

    private static final ThreadLocal<ListChangeNotifier> CURRENT = new ThreadLocal<>();

    private ListChangeNotifierHolder() {
    }

    public static void set(ListChangeNotifier notifier) {
        CURRENT.set(notifier);
    }

    public static ListChangeNotifier get() {
        return CURRENT.get();
    }

    public static void remove() {
        CURRENT.remove();
    }
}
