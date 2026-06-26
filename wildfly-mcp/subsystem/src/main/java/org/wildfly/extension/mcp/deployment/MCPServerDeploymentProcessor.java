/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.deployment;

import static org.wildfly.extension.mcp.Capabilities.OPENTELEMETRY_CAPABILITY_NAME;
import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.mcp.model.tool.ToolAnnotations;

public class MCPServerDeploymentProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        final CapabilityServiceSupport support = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        boolean isObservable = support.hasCapability(OPENTELEMETRY_CAPABILITY_NAME);
        if (!isObservable) {
            ROOT_LOGGER.debug("No opentelemetry support available");
        } else {
            ROOT_LOGGER.debug("OpenTelemetry is active for MCP");
            deploymentUnit.putAttachment(MCPAttachments.MCP_OBSERVABLE, isObservable);
        }
        if (DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
            new MCPSseHandlerServiceInstaller().install(deploymentPhaseContext);
            populateDeploymentModel(deploymentUnit);
        }
    }

    private void populateDeploymentModel(DeploymentUnit deploymentUnit) {
        WildFlyMCPRegistry registry = deploymentUnit.getAttachment(MCPAttachments.MCP_REGISTRY_METADATA);
        if (registry == null) {
            return;
        }
        DeploymentResourceSupport drs = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_RESOURCE_SUPPORT);

        for (MCPFeatureMetadata tool : registry.listTools()) {
            ModelNode model = drs.getDeploymentSubModel("mcp",
                    PathElement.pathElement(MCPDeploymentRegistrar.TOOL, tool.name()));
            setIfPresent(model, MCPDeploymentRegistrar.DESCRIPTION, tool.description());
            ToolAnnotations toolAnnotations = tool.toolAnnotations();
            setIfPresent(model, MCPDeploymentRegistrar.TITLE, toolAnnotations != null ? toolAnnotations.title() : null);
        }

        for (MCPFeatureMetadata prompt : registry.listPrompts()) {
            ModelNode model = drs.getDeploymentSubModel("mcp",
                    PathElement.pathElement(MCPDeploymentRegistrar.PROMPT, prompt.name()));
            setIfPresent(model, MCPDeploymentRegistrar.DESCRIPTION, prompt.description());
        }

        for (MCPFeatureMetadata resource : registry.listResources()) {
            MethodMetadata method = resource.method();
            if (method == null || method.uri() == null) {
                continue;
            }
            ModelNode model = drs.getDeploymentSubModel("mcp",
                    PathElement.pathElement(MCPDeploymentRegistrar.RESOURCE, method.uri()));
            setIfPresent(model, MCPDeploymentRegistrar.DESCRIPTION, resource.description());
            setIfPresent(model, MCPDeploymentRegistrar.MIME_TYPE, method.mimeType());
            setIfPresent(model, MCPDeploymentRegistrar.TITLE, resource.title());
        }

        for (MCPFeatureMetadata template : registry.listResourceTemplates()) {
            MethodMetadata method = template.method();
            if (method == null || method.uri() == null) {
                continue;
            }
            ModelNode model = drs.getDeploymentSubModel("mcp",
                    PathElement.pathElement(MCPDeploymentRegistrar.RESOURCE_TEMPLATE, method.uri()));
            setIfPresent(model, MCPDeploymentRegistrar.DESCRIPTION, template.description());
            setIfPresent(model, MCPDeploymentRegistrar.MIME_TYPE, method.mimeType());
            setIfPresent(model, MCPDeploymentRegistrar.TITLE, template.title());
        }
    }

    private static void setIfPresent(ModelNode model, SimpleAttributeDefinition attr, String value) {
        if (value != null && !value.isEmpty()) {
            model.get(attr.getName()).set(value);
        }
    }
}
