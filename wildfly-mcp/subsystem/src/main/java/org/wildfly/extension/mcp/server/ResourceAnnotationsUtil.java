/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.injection.MCPFieldNames.ANNOTATIONS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.AUDIENCE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PRIORITY;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import org.wildfly.mcp.model.Annotations;
import org.wildfly.mcp.model.Role;

final class ResourceAnnotationsUtil {

    private ResourceAnnotationsUtil() {
    }

    static void addAnnotations(JsonObjectBuilder builder, Annotations annotations) {
        if (annotations == null) {
            return;
        }
        JsonObjectBuilder ann = Json.createObjectBuilder();
        boolean hasContent = false;
        if (annotations.audience().isPresent()) {
            JsonArrayBuilder audienceArray = Json.createArrayBuilder();
            for (Role role : annotations.audience().get()) {
                audienceArray.add(role.getValue());
            }
            ann.add(AUDIENCE, audienceArray);
            hasContent = true;
        }
        if (annotations.priority().isPresent()) {
            ann.add(PRIORITY, annotations.priority().getAsDouble());
            hasContent = true;
        }
        if (hasContent) {
            builder.add(ANNOTATIONS, ann);
        }
    }
}
