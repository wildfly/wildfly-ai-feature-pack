/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.wasm;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.extension.wasm.injection.WasmToolConfiguration;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

public interface Capabilities {
    String MCP_CAPABILITY_NAME = "org.wildfly.ai.mcp.server";
    UnaryServiceDescriptor<WasmToolConfiguration> WASM_TOOL_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.mcp.server.wasm.tool", WasmToolConfiguration.class);
    RuntimeCapability<Void> WASM_TOOL_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(WASM_TOOL_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();
}
