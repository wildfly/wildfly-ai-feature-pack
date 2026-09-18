/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import java.util.Set;

/**
 * A client capability declared during initialization or in per-request {@code _meta}.
 * Only the property <em>names</em> are tracked (e.g. {@code "form"}, {@code "url"});
 * property values are not inspected by the server.
 */
public record ClientCapability(String name, Set<String> capabilities) {

    public static final String ELICITATION = "elicitation";
    public static final String FORM = "form";
    public static final String URL = "url";
}
