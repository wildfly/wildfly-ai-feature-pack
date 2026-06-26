/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ANNOTATIONS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.AUDIENCE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PRIORITY;

import java.util.Base64;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import org.mcpjava.server.Role;
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
import org.mcpjava.server.resources.TextResourceContents;

public class ContentMapper {

    public static Collection<? extends ContentBlock> processResultAsText(Object result) {
        if (result instanceof Collection) {
            Collection collection = (Collection) result;
            if (isAssignableFrom(collection, ContentBlock.class)) {
                return (Collection<ContentBlock>) result;
            }
            return collection.stream().map(c -> TextContent.of(c.toString())).toList();
        }
        if (result.getClass().isArray()) {
            if (ContentBlock.class.isAssignableFrom(result.getClass().componentType())) {
                return Arrays.asList((ContentBlock[]) result);
            }
            return Arrays.stream((Object[]) result).map(c -> TextContent.of(c.toString())).toList();
        }
        if (result instanceof ContentBlock cb) {
            return List.of(cb);
        }
        return List.of(TextContent.of(result.toString()));
    }

    public static Collection<? extends PromptMessage> processResultAsPromptMessage(Object result) {
        if (result instanceof PromptResponse pr) {
            return pr.messages();
        }
        if (result instanceof Collection) {
            Collection collection = (Collection) result;
            if (isAssignableFrom(collection, PromptMessage.class)) {
                return (Collection<PromptMessage>) result;
            }
            return collection.stream().map(c -> newUserPromptMessage(c.toString())).toList();
        }
        if (result.getClass().isArray()) {
            if (PromptMessage.class.isAssignableFrom(result.getClass().componentType())) {
                return Arrays.asList((PromptMessage[]) result);
            }
            return Arrays.stream((Object[]) result).map(c -> newUserPromptMessage(c.toString())).toList();
        }
        if (result instanceof PromptMessage pm) {
            return List.of(pm);
        }
        return List.of(newUserPromptMessage(result.toString()));
    }

    public static Collection<? extends ResourceContents> processResultAsResourceText(String uri, Object result) {
        if (result instanceof Collection) {
            Collection collection = (Collection) result;
            if (isAssignableFrom(collection, ResourceContents.class)) {
                return (Collection<ResourceContents>) result;
            }
            return collection.stream().map(c -> TextResourceContents.of(uri, c.toString())).toList();
        }
        if (result.getClass().isArray()) {
            if (ResourceContents.class.isAssignableFrom(result.getClass().componentType())) {
                return Arrays.asList((ResourceContents[]) result);
            }
            return Arrays.stream((Object[]) result).map(c -> TextResourceContents.of(uri, c.toString())).toList();
        }
        if (result instanceof ResourceContents rc) {
            return List.of(rc);
        }
        return List.of(TextResourceContents.of(uri, result.toString()));
    }

    private static PromptMessage newUserPromptMessage(String text) {
        return PromptResponse.of(Role.USER, TextContent.of(text)).messages().get(0);
    }

    public static JsonObjectBuilder contentBlockToJson(ContentBlock block) {
        JsonObjectBuilder json = Json.createObjectBuilder();
        if (block instanceof TextContent tc) {
            json.add("type", "text");
            json.add("text", tc.text());
            addAnnotations(json, tc.annotations());
        } else if (block instanceof ImageContent ic) {
            json.add("type", "image");
            json.add("data", Base64.getEncoder().encodeToString(ic.data()));
            json.add("mimeType", ic.mimeType());
            addAnnotations(json, ic.annotations());
        } else if (block instanceof AudioContent ac) {
            json.add("type", "audio");
            json.add("data", Base64.getEncoder().encodeToString(ac.data()));
            json.add("mimeType", ac.mimeType());
            addAnnotations(json, ac.annotations());
        } else if (block instanceof EmbeddedResource er) {
            json.add("type", "resource");
            JsonObjectBuilder resourceJson = Json.createObjectBuilder();
            ResourceContents rc = er.resource();
            resourceJson.add("uri", rc.uri());
            rc.mimeType().ifPresent(m -> resourceJson.add("mimeType", m));
            if (rc instanceof TextResourceContents trc) {
                resourceJson.add("text", trc.text());
            } else if (rc instanceof BlobResourceContents brc) {
                resourceJson.add("blob", Base64.getEncoder().encodeToString(brc.blob()));
            }
            json.add("resource", resourceJson);
            addAnnotations(json, er.annotations());
        } else if (block instanceof ResourceLink rl) {
            json.add("type", "resource_link");
            json.add("name", rl.name());
            json.add("uri", rl.uri());
            String title = rl.title();
            if (title != null && !title.equals(rl.name())) {
                json.add("title", title);
            }
            rl.description().ifPresent(d -> json.add("description", d));
            rl.mimeType().ifPresent(m -> json.add("mimeType", m));
            var size = rl.size();
            if (size.isPresent()) {
                json.add("size", size.getAsLong());
            }
            addAnnotations(json, rl.annotations());
        } else {
            ROOT_LOGGER.warnUnhandledContentBlockType(block.getClass().getName());
        }
        return json;
    }

    private static void addAnnotations(JsonObjectBuilder json, Optional<Annotations> annotations) {
        annotations.ifPresent(a -> {
            JsonObjectBuilder annJson = Json.createObjectBuilder();
            boolean hasContent = false;
            if (a.audience().isPresent()) {
                var arr = Json.createArrayBuilder();
                a.audience().get().forEach(r -> arr.add(r.name().toLowerCase()));
                annJson.add(AUDIENCE, arr);
                hasContent = true;
            }
            if (a.priority().isPresent()) {
                annJson.add(PRIORITY, a.priority().getAsDouble());
                hasContent = true;
            }
            if (a.lastModified().isPresent()) {
                annJson.add("lastModified", a.lastModified().get().toString());
                hasContent = true;
            }
            if (hasContent) {
                json.add(ANNOTATIONS, annJson);
            }
        });
    }

    private static boolean isAssignableFrom(Collection<?> collection, Class<?> targetType) {
        if (collection.isEmpty()) {
            return false;
        }
        return collection.stream().allMatch(targetType::isInstance);
    }
}
