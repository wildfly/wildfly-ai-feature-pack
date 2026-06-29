/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.injection.MCPFieldNames.ANNOTATIONS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.AUDIENCE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PRIORITY;

import java.util.OptionalDouble;
import java.util.Optional;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import org.mcpjava.server.Role;

final class ResourceAnnotationsUtil {

    private ResourceAnnotationsUtil() {
    }

    static void addAnnotations(JsonObjectBuilder builder, Optional<Set<Role>> audience, OptionalDouble priority) {
        JsonObjectBuilder ann = Json.createObjectBuilder();
        boolean hasContent = false;
        if (audience.isPresent()) {
            JsonArrayBuilder audienceArray = Json.createArrayBuilder();
            for (Role role : audience.get()) {
                audienceArray.add(role.name().toLowerCase());
            }
            ann.add(AUDIENCE, audienceArray);
            hasContent = true;
        }
        if (priority.isPresent()) {
            ann.add(PRIORITY, priority.getAsDouble());
            hasContent = true;
        }
        if (hasContent) {
            builder.add(ANNOTATIONS, ann);
        }
    }
}
