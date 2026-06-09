/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.prompt;

import java.util.List;

import org.wildfly.mcp.model.Role;
import org.wildfly.mcp.model.content.ContentBlock;

public record PromptMessage(Role role, List<ContentBlock> content) {

    public static PromptMessage user(List<ContentBlock> content) {
        return new PromptMessage(Role.USER, content);
    }

    public static PromptMessage assistant(List<ContentBlock> content) {
        return new PromptMessage(Role.ASSISTANT, content);
    }
}
