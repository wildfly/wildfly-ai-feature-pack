/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api;

public class MissingCapabilityException extends RuntimeException {

    private final String capability;

    public MissingCapabilityException(String capability) {
        super("Client missing required capability: " + capability);
        this.capability = capability;
    }

    public String capability() {
        return capability;
    }
}
