/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.completion;

import java.util.Map;

public interface CompleteContext {
    Map<String, String> arguments();
}
