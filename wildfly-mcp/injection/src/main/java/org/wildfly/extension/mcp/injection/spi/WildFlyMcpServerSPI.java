/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.spi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.mcpjava.server.Icon;
import org.mcpjava.server.Role;
import org.mcpjava.server.completion.CompletionResult;
import org.mcpjava.server.content.Annotations;
import org.mcpjava.server.content.AudioContent;
import org.mcpjava.server.content.ContentBlock;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.ResourceLink;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.PromptMessage;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.BlobResourceContents;
import org.mcpjava.server.resources.ResourceContents;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.TextResourceContents;
import org.mcpjava.server.spi.McpServerSPI;
import org.mcpjava.server.tools.ToolResponse;

/**
 * WildFly implementation of the MCP Server SPI, providing factory methods
 * for creating MCP protocol objects (content blocks, prompts, resources, tools).
 */
public final class WildFlyMcpServerSPI implements McpServerSPI {

    // ==================== Completion ====================

    @Override
    public CompletionResult.Builder completeResultBuilder() {
        return new CompletionResultBuilderImpl();
    }

    // ==================== Content ====================

    @Override
    public TextContent.Builder textContentBuilder(String text) {
        return new TextContentBuilderImpl(text);
    }

    @Override
    public AudioContent.Builder audioContentBuilder(byte[] data, String mimeType) {
        return new AudioContentBuilderImpl(data, mimeType);
    }

    @Override
    public ImageContent.Builder imageContentBuilder(byte[] data, String mimeType) {
        return new ImageContentBuilderImpl(data, mimeType);
    }

    @Override
    public EmbeddedResource.Builder textEmbeddedResourceBuilder(String text, String uri) {
        return new EmbeddedResourceBuilderImpl(TextResourceContents.of(uri, text), uri);
    }

    @Override
    public EmbeddedResource.Builder blobEmbeddedResourceBuilder(byte[] data, String uri) {
        return new EmbeddedResourceBuilderImpl(BlobResourceContents.of(uri, data), uri);
    }

    @Override
    public ResourceLink.Builder resourceLinkBuilder(String name, String uri) {
        return new ResourceLinkBuilderImpl(name, uri);
    }

    @Override
    public Annotations.Builder annotationsBuilder() {
        return new AnnotationsBuilderImpl();
    }

    // ==================== Prompts ====================

    @Override
    public PromptResponse.Builder promptResponseBuilder() {
        return new PromptResponseBuilderImpl();
    }

    // ==================== Resources ====================

    @Override
    public ResourceResponse.Builder resourceResponseBuilder() {
        return new ResourceResponseBuilderImpl();
    }

    @Override
    public TextResourceContents.Builder textResourceContentsBuilder(String uri, String text) {
        return new TextResourceContentsBuilderImpl(uri, text);
    }

    @Override
    public BlobResourceContents.Builder blobResourceContentsBuilder(String uri, byte[] data) {
        return new BlobResourceContentsBuilderImpl(uri, data);
    }

    // ==================== Tools ====================

    @Override
    public ToolResponse.Builder toolResponseBuilder() {
        return new ToolResponseBuilderImpl();
    }

    // ==================== Icons ====================

    @Override
    public Icon.Builder iconBuilder(String src) {
        return new IconBuilderImpl(src);
    }

    // ================================================================
    //  Record implementations
    // ================================================================

    private record TextContentImpl(String text, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements TextContent {
    }

    private record AudioContentImpl(byte[] data, String mimeType, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements AudioContent {
    }

    private record ImageContentImpl(byte[] data, String mimeType, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements ImageContent {
    }

    private record EmbeddedResourceImpl(ResourceContents resource, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements EmbeddedResource {
    }

    private record ResourceLinkImpl(String name, String title, String uri,
            Optional<String> description, Optional<String> mimeType,
            Optional<Annotations> annotations, OptionalLong size,
            Map<String, Object> metadata) implements ResourceLink {
    }

    private record AnnotationsImpl(Optional<Set<Role>> audience, OptionalDouble priority,
            Optional<Instant> lastModified) implements Annotations {
    }

    private record PromptMessageImpl(Role role, ContentBlock content) implements PromptMessage {
    }

    private record PromptResponseImpl(Optional<String> description, List<PromptMessage> messages,
            Map<String, Object> metadata) implements PromptResponse {
    }

    private record TextResourceContentsImpl(String uri, String text, Optional<String> mimeType,
            Map<String, Object> metadata) implements TextResourceContents {
    }

    private record BlobResourceContentsImpl(String uri, byte[] blob, Optional<String> mimeType,
            Map<String, Object> metadata) implements BlobResourceContents {
    }

    private record ResourceResponseImpl(List<ResourceContents> getContents,
            Map<String, Object> metadata) implements ResourceResponse {
    }

    private record CompletionResultImpl(List<String> values, OptionalInt total,
            Optional<Boolean> hasMore, Map<String, Object> metadata) implements CompletionResult {
    }

    private record ToolResponseImpl(List<ContentBlock> content, Optional<Object> structuredContent,
            boolean isError, Map<String, Object> metadata) implements ToolResponse {
    }

    private record IconImpl(String src, Optional<String> mimeType,
            List<String> sizes, Optional<Icon.Theme> theme) implements Icon {
    }

    // ================================================================
    //  Builder implementations
    // ================================================================

    private static final class TextContentBuilderImpl implements TextContent.Builder {
        private final String text;
        private Annotations annotations;
        private final Map<String, Object> metadata = new HashMap<>();

        TextContentBuilderImpl(String text) {
            this.text = text;
        }

        @Override
        public TextContent.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public TextContent.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public TextContent.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public TextContent build() {
            return new TextContentImpl(text, Optional.ofNullable(annotations),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class AudioContentBuilderImpl implements AudioContent.Builder {
        private final byte[] data;
        private final String mimeType;
        private Annotations annotations;
        private final Map<String, Object> metadata = new HashMap<>();

        AudioContentBuilderImpl(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }

        @Override
        public AudioContent.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public AudioContent.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public AudioContent.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public AudioContent build() {
            return new AudioContentImpl(data, mimeType, Optional.ofNullable(annotations),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class ImageContentBuilderImpl implements ImageContent.Builder {
        private final byte[] data;
        private final String mimeType;
        private Annotations annotations;
        private final Map<String, Object> metadata = new HashMap<>();

        ImageContentBuilderImpl(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }

        @Override
        public ImageContent.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public ImageContent.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public ImageContent.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public ImageContent build() {
            return new ImageContentImpl(data, mimeType, Optional.ofNullable(annotations),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class EmbeddedResourceBuilderImpl implements EmbeddedResource.Builder {
        private ResourceContents resource;
        private Annotations annotations;
        private String mimeType;
        private final Map<String, Object> metadata = new HashMap<>();

        EmbeddedResourceBuilderImpl(ResourceContents resource, String uri) {
            this.resource = resource;
        }

        @Override
        public EmbeddedResource.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public EmbeddedResource.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public EmbeddedResource.Builder putResourceMeta(String key, Object value) {
            // resource-level metadata not tracked in this implementation
            return this;
        }

        @Override
        public EmbeddedResource.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public EmbeddedResource.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public EmbeddedResource build() {
            return new EmbeddedResourceImpl(resource, Optional.ofNullable(annotations),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class ResourceLinkBuilderImpl implements ResourceLink.Builder {
        private final String name;
        private final String uri;
        private String title;
        private String description;
        private String mimeType;
        private Annotations annotations;
        private long size = -1;
        private boolean sizeSet = false;
        private final Map<String, Object> metadata = new HashMap<>();

        ResourceLinkBuilderImpl(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }

        @Override
        public ResourceLink.Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        @Override
        public ResourceLink.Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public ResourceLink.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public ResourceLink.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public ResourceLink.Builder setSize(long size) {
            this.size = size;
            this.sizeSet = true;
            return this;
        }

        @Override
        public ResourceLink.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public ResourceLink.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public ResourceLink build() {
            return new ResourceLinkImpl(name, title, uri,
                    Optional.ofNullable(description), Optional.ofNullable(mimeType),
                    Optional.ofNullable(annotations), sizeSet ? OptionalLong.of(size) : OptionalLong.empty(),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class AnnotationsBuilderImpl implements Annotations.Builder {
        private Set<Role> audience;
        private OptionalDouble priority = OptionalDouble.empty();
        private Instant lastModified;

        @Override
        public Annotations.Builder setAudience(Role... roles) {
            audience = new HashSet<>();
            Collections.addAll(audience, roles);
            return this;
        }

        @Override
        public Annotations.Builder setAudience(Set<Role> roles) {
            audience = new HashSet<>(roles);
            return this;
        }

        @Override
        public Annotations.Builder setPriority(double priority) {
            this.priority = OptionalDouble.of(priority);
            return this;
        }

        @Override
        public Annotations.Builder setLastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        @Override
        public Annotations build() {
            return new AnnotationsImpl(
                    Optional.ofNullable(audience).map(Set::copyOf),
                    priority,
                    Optional.ofNullable(lastModified));
        }
    }

    private static final class PromptResponseBuilderImpl implements PromptResponse.Builder {
        private String description;
        private final List<PromptMessage> messages = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();

        @Override
        public PromptResponse.Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public PromptResponse.Builder addMessage(Role role, ContentBlock content) {
            messages.add(new PromptMessageImpl(role, content));
            return this;
        }

        @Override
        public PromptResponse.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public PromptResponse.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public PromptResponse build() {
            return new PromptResponseImpl(Optional.ofNullable(description),
                    Collections.unmodifiableList(new ArrayList<>(messages)),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class TextResourceContentsBuilderImpl implements TextResourceContents.Builder {
        private final String uri;
        private final String text;
        private String mimeType;
        private final Map<String, Object> metadata = new HashMap<>();

        TextResourceContentsBuilderImpl(String uri, String text) {
            this.uri = uri;
            this.text = text;
        }

        @Override
        public TextResourceContents.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public TextResourceContents.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public TextResourceContents.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public TextResourceContents build() {
            return new TextResourceContentsImpl(uri, text, Optional.ofNullable(mimeType),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class BlobResourceContentsBuilderImpl implements BlobResourceContents.Builder {
        private final String uri;
        private final byte[] data;
        private String mimeType;
        private final Map<String, Object> metadata = new HashMap<>();

        BlobResourceContentsBuilderImpl(String uri, byte[] data) {
            this.uri = uri;
            this.data = data;
        }

        @Override
        public BlobResourceContents.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public BlobResourceContents.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public BlobResourceContents.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public BlobResourceContents build() {
            return new BlobResourceContentsImpl(uri, data, Optional.ofNullable(mimeType),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class ResourceResponseBuilderImpl implements ResourceResponse.Builder {
        private final List<ResourceContents> contents = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();

        @Override
        public ResourceResponse.Builder addContents(ResourceContents contents) {
            this.contents.add(contents);
            return this;
        }

        @Override
        public ResourceResponse.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public ResourceResponse.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public ResourceResponse build() {
            return new ResourceResponseImpl(
                    Collections.unmodifiableList(new ArrayList<>(contents)),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class CompletionResultBuilderImpl implements CompletionResult.Builder {
        private final List<String> values = new ArrayList<>();
        private OptionalInt total = OptionalInt.empty();
        private Boolean hasMore;
        private final Map<String, Object> metadata = new HashMap<>();

        @Override
        public CompletionResult.Builder addValue(String value) {
            values.add(value);
            return this;
        }

        @Override
        public CompletionResult.Builder addValues(Collection<String> values) {
            this.values.addAll(values);
            return this;
        }

        @Override
        public CompletionResult.Builder setTotal(int total) {
            this.total = OptionalInt.of(total);
            return this;
        }

        @Override
        public CompletionResult.Builder setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        @Override
        public CompletionResult.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public CompletionResult.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public CompletionResult build() {
            return new CompletionResultImpl(
                    Collections.unmodifiableList(new ArrayList<>(values)),
                    total, Optional.ofNullable(hasMore),
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class ToolResponseBuilderImpl implements ToolResponse.Builder {
        private final List<ContentBlock> content = new ArrayList<>();
        private Object structuredContent;
        private boolean isError;
        private final Map<String, Object> metadata = new HashMap<>();

        @Override
        public ToolResponse.Builder addContent(ContentBlock block) {
            content.add(block);
            return this;
        }

        @Override
        public ToolResponse.Builder addTextContent(String text) {
            content.add(TextContent.of(text));
            return this;
        }

        @Override
        public ToolResponse.Builder setStructuredContent(Object structuredContent) {
            this.structuredContent = structuredContent;
            return this;
        }

        @Override
        public ToolResponse.Builder setError(boolean isError) {
            this.isError = isError;
            return this;
        }

        @Override
        public ToolResponse.Builder putMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        @Override
        public ToolResponse.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        @Override
        public ToolResponse build() {
            return new ToolResponseImpl(
                    Collections.unmodifiableList(new ArrayList<>(content)),
                    Optional.ofNullable(structuredContent),
                    isError,
                    Collections.unmodifiableMap(new HashMap<>(metadata)));
        }
    }

    private static final class IconBuilderImpl implements Icon.Builder {
        private final String src;
        private String mimeType;
        private final List<String> sizes = new ArrayList<>();
        private Icon.Theme theme;

        IconBuilderImpl(String src) {
            this.src = src;
        }

        @Override
        public Icon.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public Icon.Builder addSize(int width, int height) {
            sizes.add(width + "x" + height);
            return this;
        }

        @Override
        public Icon.Builder setAnySize() {
            sizes.add("any");
            return this;
        }

        @Override
        public Icon.Builder setTheme(Icon.Theme theme) {
            this.theme = theme;
            return this;
        }

        @Override
        public Icon build() {
            return new IconImpl(src, Optional.ofNullable(mimeType),
                    Collections.unmodifiableList(new ArrayList<>(sizes)),
                    Optional.ofNullable(theme));
        }
    }
}
