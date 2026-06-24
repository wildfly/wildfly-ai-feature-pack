/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.content;

import org.wildfly.mcp.api.Annotations;

public record TextContent(String type, String text, Annotations annotations) implements ContentBlock {

    public static TextContent of(String text) {
        return new TextContent("text", text, null);
    }

    public static TextContent of(String text, Annotations annotations) {
        return new TextContent("text", text, annotations);
    }
}
