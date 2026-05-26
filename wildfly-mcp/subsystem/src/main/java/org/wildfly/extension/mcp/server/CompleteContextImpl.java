/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.Map;
import org.mcp_java.model.common.CompleteContext;

record CompleteContextImpl(Map<String, String> arguments) implements CompleteContext {
}
