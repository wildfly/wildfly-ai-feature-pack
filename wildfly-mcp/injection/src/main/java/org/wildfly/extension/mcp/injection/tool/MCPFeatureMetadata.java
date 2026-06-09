/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import java.util.List;
import java.util.Optional;
import org.wildfly.mcp.model.Annotations;
import org.wildfly.mcp.model.tool.ToolAnnotations;

/**
 * Metadata describing an MCP feature (tool, prompt, resource, or completion handler).
 * <p>
 * This record encapsulates all information needed to expose a Java method as an MCP feature,
 * including its kind, name, method signature, optional tool annotations, and schema information.
 * </p>
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
 * @param annotations optional MCP annotations (audience, priority) for resources; null if not set
 */
public record MCPFeatureMetadata(Kind kind, String name, MethodMetadata method, ToolAnnotations toolAnnotations,
        boolean structuredContent, Optional<String> inputSchemaGenerator, Optional<String> outputSchemaGenerator, Optional<String> outputSchemaFrom,
        String title, int size, Annotations annotations) {

    /** Convenience constructor for non-tool kinds (prompts, completions) that don't carry tool or resource-specific fields. */
    public MCPFeatureMetadata(Kind kind, String name, MethodMetadata method) {
        this(kind, name, method, null, false, Optional.empty(), Optional.empty(), Optional.empty(), null, -1, null);
    }

    /** Convenience constructor for tool kinds that carry ToolAnnotations and schema fields but no resource-specific fields. */
    public MCPFeatureMetadata(Kind kind, String name, MethodMetadata method, ToolAnnotations toolAnnotations,
            boolean structuredContent, Optional<String> inputSchemaGenerator, Optional<String> outputSchemaGenerator, Optional<String> outputSchemaFrom) {
        this(kind, name, method, toolAnnotations, structuredContent, inputSchemaGenerator, outputSchemaGenerator, outputSchemaFrom, null, -1, null);
    }

    /** Convenience constructor for resource kinds that carry title, size, and annotations but no tool-specific fields. */
    public MCPFeatureMetadata(Kind kind, String name, MethodMetadata method, String title, int size, Annotations annotations) {
        this(kind, name, method, null, false, Optional.empty(), Optional.empty(), Optional.empty(), title, size, annotations);
    }

    /** Returns the human-readable description of the feature, taken from the method metadata. */
    public String description() {
        return method.description();
    }

    /** Returns the list of declared arguments for the feature's underlying method. */
    public List<ArgumentMetadata> arguments() {
        return method.arguments();
    }

    /** MCP feature kinds that can be registered by a deployment. */
    public enum Kind {
        PROMPT,
        TOOL,
        RESOURCE,
        RESOURCE_TEMPLATE,
        PROMPT_COMPLETE,
        RESOURCE_TEMPLATE_COMPLETE;

        /**
         * Returns {@code true} if this kind requires a URI (i.e. {@link #RESOURCE} or {@link #RESOURCE_TEMPLATE}).
         *
         * @return {@code true} for resource kinds, {@code false} otherwise
         */
        public boolean requiresUri() {
            return this == RESOURCE || this == RESOURCE_TEMPLATE;
        }
    }
}
