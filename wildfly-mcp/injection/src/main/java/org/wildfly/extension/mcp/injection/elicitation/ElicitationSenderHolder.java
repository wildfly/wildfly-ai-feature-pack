/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import org.wildfly.mcp.model.elicitation.ElicitationSender;

public final class ElicitationSenderHolder {

    private static final ThreadLocal<ElicitationSender> CURRENT = new ThreadLocal<>();

    private ElicitationSenderHolder() {
    }

    public static void set(ElicitationSender sender) {
        CURRENT.set(sender);
    }

    public static ElicitationSender get() {
        return CURRENT.get();
    }

    public static void remove() {
        CURRENT.remove();
    }
}
