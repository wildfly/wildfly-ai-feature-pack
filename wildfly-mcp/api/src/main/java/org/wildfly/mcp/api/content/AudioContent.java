/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.content;

import org.wildfly.mcp.api.Annotations;

public record AudioContent(String type, String data, String mimeType, Annotations annotations) implements ContentBlock {

    public static AudioContent of(String data, String mimeType) {
        return new AudioContent("audio", data, mimeType, null);
    }

    public static AudioContent of(String data, String mimeType, Annotations annotations) {
        return new AudioContent("audio", data, mimeType, annotations);
    }
}
