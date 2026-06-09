/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.wildfly.mcp.model.content.ContentBlock;
import org.wildfly.mcp.model.content.TextContent;
import org.wildfly.mcp.model.prompt.PromptMessage;
import org.wildfly.mcp.model.resource.ResourceContents;

public class ContentMapper {

    public static Collection<? extends ContentBlock> processResultAsText(Object result) {
        if (result instanceof Collection) {
            Collection collection = (Collection) result;
            if (isContentBlock(collection)) {
                return (Collection<ContentBlock>) result;
            }
            return collection.stream().map(c -> TextContent.of(c.toString())).toList();
        }
        if (result.getClass().isArray()) {
            if (ContentBlock.class.isAssignableFrom(result.getClass().arrayType())) {
                return Arrays.asList((ContentBlock[]) result);
            }
            return Arrays.stream((Object[]) result).map(c -> TextContent.of(c.toString())).toList();
        }
        if (ContentBlock.class.isAssignableFrom(result.getClass())) {
            return List.of((ContentBlock) result);
        }
        return List.of(TextContent.of(result.toString()));
    }

    private static boolean isContentBlock(Collection result) {
        Type resultType = result.getClass().getGenericSuperclass();
        if (resultType instanceof ParameterizedType) {
            Type realType = ((ParameterizedType) resultType).getActualTypeArguments()[0];
            return ContentBlock.class.isAssignableFrom(realType.getClass());
        }
        return false;
    }

    public static Collection<? extends PromptMessage> processResultAsPromptMessage(Object result) {
        if (result instanceof Collection) {
            Collection collection = (Collection) result;
            if (isPromptMessage(collection)) {
                return (Collection<PromptMessage>) result;
            }
            return collection.stream().map(c -> PromptMessage.user(List.of(TextContent.of(c.toString())))).toList();
        }
        if (result.getClass().isArray()) {
            if (PromptMessage.class.isAssignableFrom(result.getClass().arrayType())) {
                return Arrays.asList((PromptMessage[]) result);
            }
            return Arrays.stream((Object[]) result).map(c -> PromptMessage.user(List.of(TextContent.of(c.toString())))).toList();
        }
        if (PromptMessage.class.isAssignableFrom(result.getClass())) {
            return List.of((PromptMessage) result);
        }
        return List.of(PromptMessage.user(List.of(TextContent.of(result.toString()))));
    }

    private static boolean isPromptMessage(Collection result) {
        Type resultType = result.getClass().getGenericSuperclass();
        if (resultType instanceof ParameterizedType) {
            Type realType = ((ParameterizedType) resultType).getActualTypeArguments()[0];
            return PromptMessage.class.isAssignableFrom(realType.getClass());
        }
        return false;
    }

    public static Collection<? extends ResourceContents> processResultAsResourceText(String uri, Object result) {
        if (result instanceof Collection) {
            Collection collection = (Collection) result;
            if (isResourceContents(collection)) {
                return (Collection<ResourceContents>) result;
            }
            return collection.stream().map(c -> ResourceContents.text(uri, c.toString())).toList();
        }
        if (result.getClass().isArray()) {
            if (ResourceContents.class.isAssignableFrom(result.getClass().arrayType())) {
                return Arrays.asList((ResourceContents[]) result);
            }
            return Arrays.stream((Object[]) result).map(c -> ResourceContents.text(uri, c.toString())).toList();
        }
        if (ResourceContents.class.isAssignableFrom(result.getClass())) {
            return List.of((ResourceContents) result);
        }
        return List.of(ResourceContents.text(uri, result.toString()));
    }

    private static boolean isResourceContents(Collection result) {
        Type resultType = result.getClass().getGenericSuperclass();
        if (resultType instanceof ParameterizedType) {
            Type realType = ((ParameterizedType) resultType).getActualTypeArguments()[0];
            return ResourceContents.class.isAssignableFrom(realType.getClass());
        }
        return false;
    }
}
