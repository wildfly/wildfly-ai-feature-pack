/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.wasm.deployment;

import static org.wildfly.extension.wasm.Capabilities.WASM_TOOL_PROVIDER_CAPABILITY;
import java.util.List;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.DotName;
import org.jboss.modules.ModuleLoader;
import org.wildfly.wasm.api.WasmTool;
import org.wildfly.wasm.api.WasmToolService;
import org.wildfly.extension.wasm.WasmLogger;

public class WasmDependencyProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        ModuleLoader moduleLoader = org.jboss.modules.Module.getBootModuleLoader();
        moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, "jakarta.json.api").setOptional(false).setImportServices(true).build());
        ModuleDependency modDep = ModuleDependency.Builder.of(moduleLoader, "org.wildfly.extension.wasm.injection").setOptional(false).setExport(true).setImportServices(true).build();
        modDep.addImportFilter(s -> s.equals("META-INF"), true);
        moduleSpecification.addSystemDependency(modDep);
        final CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (index == null) {
            throw WasmLogger.ROOT_LOGGER.unableToResolveAnnotationIndex(deploymentUnit);
        }
        List<AnnotationInstance> annotations = index.getAnnotations(DotName.createSimple(WasmTool.class));
        processWasmTools(deploymentPhaseContext, annotations);
        annotations = index.getAnnotations(DotName.createSimple(WasmToolService.class));
        processWasmToolServices(deploymentPhaseContext, annotations);
    }

    private void processWasmTools(DeploymentPhaseContext deploymentPhaseContext, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        for (AnnotationInstance annotation : annotations) {
            String annotationValue = annotation.value() != null ? annotation.value().asString() : null;
            String name;
            if (annotationValue != null && !annotationValue.isEmpty()) {
                name = annotationValue;
            } else if (annotation.target().kind() == Kind.FIELD) {
                name = annotation.target().asField().name();
            } else {
                name = annotation.target().asMethodParameter().name();
            }
            deploymentPhaseContext.addDeploymentDependency(WASM_TOOL_PROVIDER_CAPABILITY.getCapabilityServiceName(name), WasmAttachements.WASM_TOOL_CONFIGURATIONS);
        }
    }

    private void processWasmToolServices(DeploymentPhaseContext deploymentPhaseContext, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        for (AnnotationInstance annotation : annotations) {
            if (annotation.value("wasmToolConfigurationName") != null) {
                String name = annotation.value("wasmToolConfigurationName").asString();
                deploymentPhaseContext.addDeploymentDependency(WASM_TOOL_PROVIDER_CAPABILITY.getCapabilityServiceName(name), WasmAttachements.WASM_TOOL_CONFIGURATIONS);
            }
        }
    }
}
