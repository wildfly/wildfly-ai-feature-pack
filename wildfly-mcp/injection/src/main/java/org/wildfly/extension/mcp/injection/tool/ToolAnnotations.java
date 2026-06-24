/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

public record ToolAnnotations(String title, Boolean readOnlyHint, Boolean destructiveHint,
                               Boolean idempotentHint, Boolean openWorldHint) {
}
