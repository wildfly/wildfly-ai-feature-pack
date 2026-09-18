/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api;

public interface ClientCapabilities {

    boolean hasCapability(String capability);

    void requireCapability(String capability) throws MissingCapabilityException;
}
