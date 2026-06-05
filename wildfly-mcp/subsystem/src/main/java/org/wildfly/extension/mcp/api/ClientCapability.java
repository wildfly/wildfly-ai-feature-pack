/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

public record ClientCapability(String name, java.util.Map<String, Object> properties) {

    public static final String ELICITATION = "elicitation";
    public static final String FORM = "form";
    public static final String URL = "url";
}
