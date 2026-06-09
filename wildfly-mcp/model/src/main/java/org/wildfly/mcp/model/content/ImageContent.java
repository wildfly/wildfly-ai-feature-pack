/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.content;

import org.wildfly.mcp.model.Annotations;

public record ImageContent(String type, String data, String mimeType, Annotations annotations) implements ContentBlock {

    public static ImageContent of(String data, String mimeType) {
        return new ImageContent("image", data, mimeType, null);
    }

    public static ImageContent of(String data, String mimeType, Annotations annotations) {
        return new ImageContent("image", data, mimeType, annotations);
    }
}
