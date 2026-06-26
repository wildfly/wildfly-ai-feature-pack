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
import org.mcpjava.server.MetaCarrier;
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
        return new EmbeddedResourceBuilderImpl(text, uri);
    }

    @Override
    public EmbeddedResource.Builder blobEmbeddedResourceBuilder(byte[] data, String uri) {
        return new EmbeddedResourceBuilderImpl(data, uri);
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
                                        Optional<Boolean> hasMore,
                                        Map<String, Object> metadata) implements CompletionResult {
    }

    private record ToolResponseImpl(List<ContentBlock> content, Optional<Object> structuredContent,
                                    boolean isError, Map<String, Object> metadata) implements ToolResponse {
    }

    private record IconImpl(String src, Optional<String> mimeType,
                            List<String> sizes, Optional<Icon.Theme> theme) implements Icon {
    }

    // ================================================================
    //  Shared builder support
    // ================================================================

    private static Map<String, Object> freezeMap(Map<String, Object> map) {
        return Collections.unmodifiableMap(new HashMap<>(map));
    }

    private static <E> List<E> freezeList(List<E> list) {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    @SuppressWarnings("unchecked")
    private abstract static class MetaBuilder<B extends MetaCarrier.Builder<B>> implements MetaCarrier.Builder<B> {
        final Map<String, Object> metadata = new HashMap<>();

        @Override
        public B putMetadata(String key, Object value) {
            metadata.put(key, value);
            return (B) this;
        }

        @Override
        public B setMetadata(Map<String, Object> m) {
            metadata.clear();
            metadata.putAll(m);
            return (B) this;
        }
    }

    @SuppressWarnings("unchecked")
    private abstract static class AnnotatedMetaBuilder<B extends MetaCarrier.Builder<B>> extends MetaBuilder<B> {
        Annotations annotations;

        public B setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return (B) this;
        }

        Optional<Annotations> optAnnotations() {
            return Optional.ofNullable(annotations);
        }
    }

    // ================================================================
    //  Builder implementations
    // ================================================================

    private static final class TextContentBuilderImpl
            extends AnnotatedMetaBuilder<TextContent.Builder> implements TextContent.Builder {
        private final String text;

        TextContentBuilderImpl(String text) {
            this.text = text;
        }

        @Override
        public TextContent build() {
            return new TextContentImpl(text, optAnnotations(), freezeMap(metadata));
        }
    }

    private static final class AudioContentBuilderImpl
            extends AnnotatedMetaBuilder<AudioContent.Builder> implements AudioContent.Builder {
        private final byte[] data;
        private final String mimeType;

        AudioContentBuilderImpl(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }

        @Override
        public AudioContent build() {
            return new AudioContentImpl(data, mimeType, optAnnotations(), freezeMap(metadata));
        }
    }

    private static final class ImageContentBuilderImpl
            extends AnnotatedMetaBuilder<ImageContent.Builder> implements ImageContent.Builder {
        private final byte[] data;
        private final String mimeType;

        ImageContentBuilderImpl(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }

        @Override
        public ImageContent build() {
            return new ImageContentImpl(data, mimeType, optAnnotations(), freezeMap(metadata));
        }
    }

    private static final class EmbeddedResourceBuilderImpl
            extends AnnotatedMetaBuilder<EmbeddedResource.Builder> implements EmbeddedResource.Builder {
        private final String uri;
        private final String text;
        private final byte[] data;
        private String mimeType;
        private final Map<String, Object> resourceMeta = new HashMap<>();

        EmbeddedResourceBuilderImpl(String text, String uri) {
            this.text = text;
            this.data = null;
            this.uri = uri;
        }

        EmbeddedResourceBuilderImpl(byte[] data, String uri) {
            this.text = null;
            this.data = data;
            this.uri = uri;
        }

        @Override
        public EmbeddedResource.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public EmbeddedResource.Builder putResourceMeta(String k, Object v) {
            resourceMeta.put(k, v);
            return this;
        }

        @Override
        public EmbeddedResource build() {
            final ResourceContents resource;
            if (text != null) {
                resource = new TextResourceContentsImpl(uri, text, Optional.ofNullable(mimeType), freezeMap(resourceMeta));
            } else {
                resource = new BlobResourceContentsImpl(uri, data, Optional.ofNullable(mimeType), freezeMap(resourceMeta));
            }
            return new EmbeddedResourceImpl(resource, optAnnotations(), freezeMap(metadata));
        }
    }

    private static final class ResourceLinkBuilderImpl
            extends AnnotatedMetaBuilder<ResourceLink.Builder> implements ResourceLink.Builder {
        private final String name;
        private final String uri;
        private String title;
        private String description;
        private String mimeType;
        private long size = -1;
        private boolean sizeSet = false;

        ResourceLinkBuilderImpl(String name, String uri) {
            this.name = name;
            this.uri = uri;
        }

        @Override
        public ResourceLink.Builder setTitle(String t) {
            this.title = t;
            return this;
        }

        @Override
        public ResourceLink.Builder setDescription(String d) {
            this.description = d;
            return this;
        }

        @Override
        public ResourceLink.Builder setMimeType(String m) {
            this.mimeType = m;
            return this;
        }

        @Override
        public ResourceLink.Builder setSize(long s) {
            this.size = s;
            this.sizeSet = true;
            return this;
        }

        @Override
        public ResourceLink build() {
            return new ResourceLinkImpl(name, title, uri,
                    Optional.ofNullable(description), Optional.ofNullable(mimeType),
                    optAnnotations(), sizeSet ? OptionalLong.of(size) : OptionalLong.empty(),
                    freezeMap(metadata));
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
        public Annotations.Builder setPriority(double p) {
            this.priority = OptionalDouble.of(p);
            return this;
        }

        @Override
        public Annotations.Builder setLastModified(Instant t) {
            this.lastModified = t;
            return this;
        }

        @Override
        public Annotations build() {
            return new AnnotationsImpl(Optional.ofNullable(audience).map(Set::copyOf), priority, Optional.ofNullable(lastModified));
        }
    }

    private static final class PromptResponseBuilderImpl
            extends MetaBuilder<PromptResponse.Builder> implements PromptResponse.Builder {
        private String description;
        private final List<PromptMessage> messages = new ArrayList<>();

        @Override
        public PromptResponse.Builder setDescription(String d) {
            this.description = d;
            return this;
        }

        @Override
        public PromptResponse.Builder addMessage(Role role, ContentBlock content) {
            messages.add(new PromptMessageImpl(role, content));
            return this;
        }

        @Override
        public PromptResponse build() {
            return new PromptResponseImpl(Optional.ofNullable(description), freezeList(messages), freezeMap(metadata));
        }
    }

    private static final class TextResourceContentsBuilderImpl
            extends MetaBuilder<TextResourceContents.Builder> implements TextResourceContents.Builder {
        private final String uri;
        private final String text;
        private String mimeType;

        TextResourceContentsBuilderImpl(String uri, String text) {
            this.uri = uri;
            this.text = text;
        }

        @Override
        public TextResourceContents.Builder setMimeType(String m) {
            this.mimeType = m;
            return this;
        }

        @Override
        public TextResourceContents build() {
            return new TextResourceContentsImpl(uri, text, Optional.ofNullable(mimeType), freezeMap(metadata));
        }
    }

    private static final class BlobResourceContentsBuilderImpl
            extends MetaBuilder<BlobResourceContents.Builder> implements BlobResourceContents.Builder {
        private final String uri;
        private final byte[] data;
        private String mimeType;

        BlobResourceContentsBuilderImpl(String uri, byte[] data) {
            this.uri = uri;
            this.data = data;
        }

        @Override
        public BlobResourceContents.Builder setMimeType(String m) {
            this.mimeType = m;
            return this;
        }

        @Override
        public BlobResourceContents build() {
            return new BlobResourceContentsImpl(uri, data, Optional.ofNullable(mimeType), freezeMap(metadata));
        }
    }

    private static final class ResourceResponseBuilderImpl
            extends MetaBuilder<ResourceResponse.Builder> implements ResourceResponse.Builder {
        private final List<ResourceContents> contents = new ArrayList<>();

        @Override
        public ResourceResponse.Builder addContents(ResourceContents c) {
            this.contents.add(c);
            return this;
        }

        @Override
        public ResourceResponse build() {
            return new ResourceResponseImpl(freezeList(contents), freezeMap(metadata));
        }
    }

    private static final class CompletionResultBuilderImpl
            extends MetaBuilder<CompletionResult.Builder> implements CompletionResult.Builder {
        private final List<String> values = new ArrayList<>();
        private OptionalInt total = OptionalInt.empty();
        private Boolean hasMore;

        @Override
        public CompletionResult.Builder addValue(String v) {
            values.add(v);
            return this;
        }

        @Override
        public CompletionResult.Builder addValues(Collection<String> v) {
            this.values.addAll(v);
            return this;
        }

        @Override
        public CompletionResult.Builder setTotal(int t) {
            this.total = OptionalInt.of(t);
            return this;
        }

        @Override
        public CompletionResult.Builder setHasMore(Boolean h) {
            this.hasMore = h;
            return this;
        }

        @Override
        public CompletionResult build() {
            return new CompletionResultImpl(freezeList(values), total, Optional.ofNullable(hasMore), freezeMap(metadata));
        }
    }

    private static final class ToolResponseBuilderImpl
            extends MetaBuilder<ToolResponse.Builder> implements ToolResponse.Builder {
        private final List<ContentBlock> content = new ArrayList<>();
        private Object structuredContent;
        private boolean isError;

        @Override
        public ToolResponse.Builder addContent(ContentBlock b) {
            content.add(b);
            return this;
        }

        @Override
        public ToolResponse.Builder addTextContent(String t) {
            content.add(TextContent.of(t));
            return this;
        }

        @Override
        public ToolResponse.Builder setStructuredContent(Object sc) {
            this.structuredContent = sc;
            return this;
        }

        @Override
        public ToolResponse.Builder setError(boolean e) {
            this.isError = e;
            return this;
        }

        @Override
        public ToolResponse build() {
            return new ToolResponseImpl(freezeList(content), Optional.ofNullable(structuredContent), isError, freezeMap(metadata));
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
        public Icon.Builder setMimeType(String m) {
            this.mimeType = m;
            return this;
        }

        @Override
        public Icon.Builder addSize(int w, int h) {
            sizes.add(w + "x" + h);
            return this;
        }

        @Override
        public Icon.Builder setAnySize() {
            sizes.add("any");
            return this;
        }

        @Override
        public Icon.Builder setTheme(Icon.Theme t) {
            this.theme = t;
            return this;
        }

        @Override
        public Icon build() {
            return new IconImpl(src, Optional.ofNullable(mimeType), freezeList(sizes), Optional.ofNullable(theme));
        }
    }
}
