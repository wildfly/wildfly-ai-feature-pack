/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api;

public interface ListChangeNotifier {

    void notifyToolsChanged();

    void notifyPromptsChanged();

    void notifyResourcesChanged();
}
