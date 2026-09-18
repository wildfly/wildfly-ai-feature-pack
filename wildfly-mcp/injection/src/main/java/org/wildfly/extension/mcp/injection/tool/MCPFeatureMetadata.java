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

    public static Builder builder(Kind kind, String name, MethodMetadata method) {
        return new Builder(kind, name, method);
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

    public static class Builder {

        private final Kind kind;
        private final String name;
        private final MethodMetadata method;
        private ToolAnnotations toolAnnotations;
        private boolean structuredContent;
        private String inputSchemaGenerator;
        private String outputSchemaGenerator;
        private String outputSchemaFrom;
        private String title;
        private int size = -1;
        private Set<Role> audience;
        private Double priority;
        Builder(Kind kind, String name, MethodMetadata method) {
            this.kind = kind;
            this.name = name;
            this.method = method;
        }

        public Builder toolAnnotations(ToolAnnotations toolAnnotations) {
            this.toolAnnotations = toolAnnotations;
            return this;
        }

        public Builder structuredContent(boolean structuredContent) {
            this.structuredContent = structuredContent;
            return this;
        }

        public Builder inputSchemaGenerator(String inputSchemaGenerator) {
            this.inputSchemaGenerator = inputSchemaGenerator;
            return this;
        }

        public Builder outputSchemaGenerator(String outputSchemaGenerator) {
            this.outputSchemaGenerator = outputSchemaGenerator;
            return this;
        }

        public Builder outputSchemaFrom(String outputSchemaFrom) {
            this.outputSchemaFrom = outputSchemaFrom;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder audience(Set<Role> audience) {
            this.audience = audience;
            return this;
        }

        public Builder priority(double priority) {
            this.priority = priority;
            return this;
        }

        public MCPFeatureMetadata build() {
            return new MCPFeatureMetadata(
                    kind, name, method, toolAnnotations, structuredContent,
                    Optional.ofNullable(inputSchemaGenerator),
                    Optional.ofNullable(outputSchemaGenerator),
                    Optional.ofNullable(outputSchemaFrom),
                    title, size,
                    Optional.ofNullable(audience),
                    priority != null ? OptionalDouble.of(priority) : OptionalDouble.empty());
        }
    }
}
