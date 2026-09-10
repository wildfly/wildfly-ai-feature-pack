/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.wasm.deployment;

import static org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME;

import static org.wildfly.extension.wasm.Capabilities.MCP_CAPABILITY_NAME;
import java.lang.annotation.Annotation;
import java.util.Optional;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.weld.WeldCapability;
import org.wildfly.extension.wasm.WasmModuleRegistrar;
import org.wildfly.extension.wasm.WasmLogger;
import org.wildfly.extension.wasm.injection.WasmPortableExtension;
import org.wildfly.extension.wasm.injection.WasmServicePortableExtension;
import org.wildfly.extension.wasm.injection.WildFlyWasmRegistry;

public class WasmCDIProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final CapabilityServiceSupport support = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        WildFlyWasmRegistry wasmRegistry = new WildFlyWasmRegistry();
        deploymentUnit.getAttachmentList(WasmAttachements.WASM_TOOL_CONFIGURATIONS).forEach(c -> wasmRegistry.registerWasmTool(c));
        final Optional<WeldCapability> weldCapability = support.getOptionalCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class);
        if (weldCapability != null && weldCapability.isPresent() && !weldCapability.get().isPartOfWeldDeployment(deploymentUnit)) {
            WasmLogger.ROOT_LOGGER.cdiRequired();
        } else {
            weldCapability.get().registerExtensionInstance(new WasmPortableExtension(wasmRegistry), deploymentUnit);
            if (support.hasCapability(MCP_CAPABILITY_NAME)) {
                try {
                    Annotation mcpToolQualifier = Annotation.class.cast(Class.forName("org.wildfly.extension.mcp.injection.tool.MCPTool$MCPToolLiteral").getDeclaredField("INSTANCE").get(null));
                    weldCapability.get().registerExtensionInstance(new WasmServicePortableExtension(mcpToolQualifier), deploymentUnit);
                } catch (ClassNotFoundException | NoSuchFieldException | SecurityException | IllegalAccessException | IllegalArgumentException ex) {
                    WasmLogger.ROOT_LOGGER.errorResolvingMCPToolQualifier(ex);
                    weldCapability.get().registerExtensionInstance(new WasmServicePortableExtension(), deploymentUnit);
                }
            } else {
                weldCapability.get().registerExtensionInstance(new WasmServicePortableExtension(), deploymentUnit);
            }
        }
        try {
            final DeploymentResourceSupport deploymentResourceSupport = deploymentUnit
                    .getAttachment(org.jboss.as.server.deployment.Attachments.DEPLOYMENT_RESOURCE_SUPPORT);
                     deploymentUnit.getAttachmentList(WasmAttachements.WASM_TOOL_CONFIGURATIONS).forEach(c -> {
                                 deploymentResourceSupport.getDeploymentSubModel("wasm", PathElement.pathElement(WasmModuleRegistrar.NAME, c.name()));
                                     });
        } catch (Exception e) {
            WasmLogger.ROOT_LOGGER.errorRegisteringWasmToolDeploymentResources(e);
        }
    }
}
