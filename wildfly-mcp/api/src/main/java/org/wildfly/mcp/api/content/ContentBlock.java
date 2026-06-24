/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.content;

import org.wildfly.mcp.api.Annotations;

public interface ContentBlock {
    String type();
    Annotations annotations();
}
