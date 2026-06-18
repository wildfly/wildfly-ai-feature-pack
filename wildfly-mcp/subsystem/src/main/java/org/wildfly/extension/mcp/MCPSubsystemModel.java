/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.mcp;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * Enumeration of MCP subsystem model versions.
 */
enum MCPSubsystemModel implements SubsystemModel {
    VERSION_2_0_0(2, 0, 0),
    ;
    static final MCPSubsystemModel CURRENT = VERSION_2_0_0;

    private final ModelVersion version;

    MCPSubsystemModel(int major, int minor, int micro) {
        this.version = ModelVersion.create(major, minor, micro);
    }

    @Override
    public ModelVersion getVersion() {
        return this.version;
    }
}
