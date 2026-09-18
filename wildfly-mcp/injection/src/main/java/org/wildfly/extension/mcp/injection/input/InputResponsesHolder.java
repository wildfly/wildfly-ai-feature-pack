/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.input;

import org.wildfly.mcp.api.tool.InputResponses;

public final class InputResponsesHolder {

    private static final ThreadLocal<InputResponses> CURRENT = new ThreadLocal<>();

    private InputResponsesHolder() {
    }

    public static void set(InputResponses inputResponses) {
        CURRENT.set(inputResponses);
    }

    public static InputResponses get() {
        return CURRENT.get();
    }

    public static void remove() {
        CURRENT.remove();
    }
}
