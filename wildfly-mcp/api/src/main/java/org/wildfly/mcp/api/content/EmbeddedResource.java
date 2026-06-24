/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.content;

import org.wildfly.mcp.api.Annotations;
import org.wildfly.mcp.api.resource.ResourceContents;

public record EmbeddedResource(String type, ResourceContents resource, Annotations annotations) implements ContentBlock {

    public static EmbeddedResource of(ResourceContents resource) {
        return new EmbeddedResource("resource", resource, null);
    }

    public static EmbeddedResource of(ResourceContents resource, Annotations annotations) {
        return new EmbeddedResource("resource", resource, annotations);
    }
}
