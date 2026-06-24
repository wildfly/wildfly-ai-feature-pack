/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.resource;

import static java.util.Objects.requireNonNull;
import static org.wildfly.mcp.api.MCPApiLogger.ROOT_LOGGER;

public record ResourceContents(String uri, String mimeType, String text, String blob) {

    public ResourceContents {
        requireNonNull(uri, ROOT_LOGGER.parameterMustNotBeNull("uri"));
        if (text == null && blob == null) {
            throw ROOT_LOGGER.eitherTextOrBlobMustBeProvided();
        }
        if (text != null && blob != null) {
            throw ROOT_LOGGER.textAndBlobAreMutuallyExclusive();
        }
    }

    public static ResourceContents text(String uri, String text) {
        return new ResourceContents(uri, null, text, null);
    }

    public static ResourceContents text(String uri, String mimeType, String text) {
        return new ResourceContents(uri, mimeType, text, null);
    }

    public static ResourceContents blob(String uri, String blob) {
        return new ResourceContents(uri, null, null, blob);
    }

    public static ResourceContents blob(String uri, String mimeType, String blob) {
        return new ResourceContents(uri, mimeType, null, blob);
    }

    public boolean isText() {
        return text != null;
    }

    public boolean isBlob() {
        return blob != null;
    }
}
