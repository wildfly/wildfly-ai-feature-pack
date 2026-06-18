/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.wasm.deployment;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.AttachmentList;
import org.wildfly.extension.wasm.injection.WasmToolConfiguration;

public class WasmAttachements {
    static final AttachmentKey<AttachmentList<WasmToolConfiguration>> WASM_TOOL_CONFIGURATIONS = AttachmentKey.createList(WasmToolConfiguration.class);
}
