/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.mcpjava.server.Role;

/**
 * Metadata describing an MCP feature (tool, prompt, resource, or completion handler).
 *
 * @param kind the type of MCP feature (TOOL, PROMPT, RESOURCE, etc.)
 * @param name the unique name of the feature as exposed to MCP clients
 * @param method metadata about the Java method that implements this feature
 * @param toolAnnotations optional MCP tool annotations (title, hints) for tools; null for non-tool features
 * @param structuredContent whether the tool returns structured content that should include an outputSchema
 * @param inputSchemaGenerator optional class name of a {@link ToolSchemaGenerator} CDI bean for input schema
 * @param outputSchemaGenerator optional class name of a {@link ToolSchemaGenerator} CDI bean for output schema
 * @param outputSchemaFrom optional class name to generate output schema from
 * @param title optional human-readable title for resources and resource templates; null if not set
 * @param size optional size in bytes for resources; -1 if not set
 * @param audience optional intended audience roles for resources
 * @param priority optional priority for resources
 */
public record MCPFeatureMetadata(Kind kind, String name, MethodMetadata method, ToolAnnotations toolAnnotations,
        boolean structuredContent, Optional<String> inputSchemaGenerator, Optional<String> outputSchemaGenerator, Optional<String> outputSchemaFrom,
        String title, int size, Optional<Set<Role>> audience, OptionalDouble priority) {

    public MCPFeatureMetadata(Kind kind, String name, MethodMetadata method) {
        this(kind, name, method, null, false, Optional.empty(), Optional.empty(), Optional.empty(), null, -1, Optional.empty(), OptionalDouble.empty());
    }

    public MCPFeatureMetadata(Kind kind, String name, MethodMetadata method, ToolAnnotations toolAnnotations,
            boolean structuredContent, Optional<String> inputSchemaGenerator, Optional<String> outputSchemaGenerator, Optional<String> outputSchemaFrom) {
        this(kind, name, method, toolAnnotations, structuredContent, inputSchemaGenerator, outputSchemaGenerator, outputSchemaFrom, null, -1, Optional.empty(), OptionalDouble.empty());
    }

    public MCPFeatureMetadata(Kind kind, String name, MethodMetadata method, String title, int size, Optional<Set<Role>> audience, OptionalDouble priority) {
        this(kind, name, method, null, false, Optional.empty(), Optional.empty(), Optional.empty(), title, size, audience, priority);
    }

    public String description() {
        return method.description();
    }

    public List<ArgumentMetadata> arguments() {
        return method.arguments();
    }

    public enum Kind {
        PROMPT,
        TOOL,
        RESOURCE,
        RESOURCE_TEMPLATE,
        PROMPT_COMPLETE,
        RESOURCE_TEMPLATE_COMPLETE;
    }
}
