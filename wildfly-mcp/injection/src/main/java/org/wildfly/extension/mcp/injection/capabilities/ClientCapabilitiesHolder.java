/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.capabilities;

import org.wildfly.mcp.api.ClientCapabilities;

public final class ClientCapabilitiesHolder {

    private static final ThreadLocal<ClientCapabilities> CURRENT = new ThreadLocal<>();

    private ClientCapabilitiesHolder() {
    }

    public static void set(ClientCapabilities capabilities) {
        CURRENT.set(capabilities);
    }

    public static ClientCapabilities get() {
        return CURRENT.get();
    }

    public static void remove() {
        CURRENT.remove();
    }
}
