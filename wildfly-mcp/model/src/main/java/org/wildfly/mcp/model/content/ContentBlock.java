/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.content;

import org.wildfly.mcp.model.Annotations;

public interface ContentBlock {
    String type();
    Annotations annotations();
}
