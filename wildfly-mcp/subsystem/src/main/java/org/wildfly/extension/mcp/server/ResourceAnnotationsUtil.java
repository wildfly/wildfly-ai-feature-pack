/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.injection.MCPFieldNames.ANNOTATIONS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.AUDIENCE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PRIORITY;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import org.wildfly.mcp.model.Annotations;

final class ResourceAnnotationsUtil {

    private ResourceAnnotationsUtil() {
    }

    static void addAnnotations(JsonObjectBuilder builder, Annotations annotations) {
        if (annotations == null) {
            return;
        }
        JsonObjectBuilder ann = Json.createObjectBuilder();
        boolean hasContent = false;
        if (annotations.audience() != null) {
            ann.add(AUDIENCE, Json.createArrayBuilder().add(annotations.audience()));
            hasContent = true;
        }
        if (annotations.priority() != null) {
            ann.add(PRIORITY, annotations.priority());
            hasContent = true;
        }
        if (hasContent) {
            builder.add(ANNOTATIONS, ann);
        }
    }
}
