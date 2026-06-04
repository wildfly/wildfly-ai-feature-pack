/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import java.util.List;
import org.mcp_java.model.tool.ToolAnnotations;

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
 * @param inputSchemaGenerator optional class name of a {@link ToolSchemaGenerator} CDI bean for input schema; empty string if not provided
 * @param outputSchemaGenerator optional class name of a {@link ToolSchemaGenerator} CDI bean for output schema; empty string if not provided
 * @param outputSchemaFrom optional class name to generate output schema from; empty string if not provided
 */
public record MCPFeatureMetadata(Kind kind, String name, MethodMetadata method, ToolAnnotations toolAnnotations,
        boolean structuredContent, String inputSchemaGenerator, String outputSchemaGenerator, String outputSchemaFrom) {

    public MCPFeatureMetadata {
        inputSchemaGenerator = inputSchemaGenerator != null ? inputSchemaGenerator : "";
        outputSchemaGenerator = outputSchemaGenerator != null ? outputSchemaGenerator : "";
        outputSchemaFrom = outputSchemaFrom != null ? outputSchemaFrom : "";
    }

    /** Convenience constructor for non-tool kinds (prompts, resources, completions) that don't carry ToolAnnotations. */
    public MCPFeatureMetadata(Kind kind, String name, MethodMetadata method) {
        this(kind, name, method, null, false, "", "", "");
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
