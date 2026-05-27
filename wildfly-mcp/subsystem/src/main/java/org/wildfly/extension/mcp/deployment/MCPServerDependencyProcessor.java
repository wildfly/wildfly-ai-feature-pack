/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.deployment;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.deployment.MCPAttachments.MCP_REGISTRY_METADATA;

import java.lang.reflect.Type;
import java.util.ArrayList;
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
import org.jboss.jandex.DotName;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.modules.ModuleLoader;
import org.wildfly.extension.mcp.Capabilities;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSender;
import org.mcp_java.server.progress.Progress;
import org.mcp_java.server.tools.Tool;
import org.mcp_java.server.tools.ToolArg;
import org.mcp_java.server.prompts.Prompt;
import org.mcp_java.server.prompts.PromptArg;
import org.mcp_java.server.resources.Resource;
import org.mcp_java.server.resources.ResourceTemplate;
import org.mcp_java.server.resources.ResourceTemplateArg;
import org.mcp_java.server.completion.CompletePrompt;
import org.mcp_java.server.completion.CompleteResourceTemplate;
import org.mcp_java.server.completion.CompleteArg;

public class MCPServerDependencyProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        ModuleLoader moduleLoader = org.jboss.modules.Module.getBootModuleLoader();
        moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, "jakarta.json.api").setOptional(false).setImportServices(true).build());
        ModuleDependency modDep = ModuleDependency.Builder.of(moduleLoader, "org.wildfly.extension.mcp.injection").setOptional(false).setExport(true).setImportServices(true).build();
        modDep.addImportFilter(s -> s.equals("META-INF"), true);
        moduleSpecification.addSystemDependency(modDep);
        final CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (index == null) {
            throw ROOT_LOGGER.unableToResolveAnnotationIndex(deploymentUnit);
        }
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        List<AnnotationInstance> annotations = index.getAnnotations(DotName.createSimple(Tool.class));
        processTools(registry, annotations);
        annotations = index.getAnnotations(DotName.createSimple(Prompt.class));
        processPrompts(registry, annotations);
        annotations = index.getAnnotations(DotName.createSimple(Resource.class));
        processResources(registry, annotations);
        annotations = index.getAnnotations(DotName.createSimple(ResourceTemplate.class));
        processResourceTemplates(registry, annotations);
        annotations = index.getAnnotations(DotName.createSimple(CompletePrompt.class));
        processPromptCompletions(registry, annotations);
        annotations = index.getAnnotations(DotName.createSimple(CompleteResourceTemplate.class));
        processResourceTemplateCompletions(registry, annotations);
        deploymentUnit.putAttachment(MCP_REGISTRY_METADATA, registry);
        deploymentPhaseContext.addDeploymentDependency(Capabilities.MCP_SERVER_PROVIDER_CAPABILITY.getCapabilityServiceName(), MCPAttachments.MCP_ENDPOINT_CONFIGURATION);
    }

    private void processPrompts(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        DotName promptArg = DotName.createSimple(PromptArg.class);
        for (AnnotationInstance annotation : annotations) {
            String name = annotation.value("name") != null ? annotation.value("name").asString() : annotation.target().asMethod().name();
            String description = annotation.value("description") != null ? annotation.value("description").asString() : "";
            MethodInfo info = annotation.target().asMethod();
            List<ArgumentMetadata> arguments = buildArguments(info, promptArg);
            ROOT_LOGGER.debugf("Prompt detected on class %s with method %s with the following annotated parameters %s", info.declaringClass(), info.name(), arguments);
            MCPFeatureMetadata metadata = new MCPFeatureMetadata(MCPFeatureMetadata.Kind.PROMPT,
                    name,
                    new MethodMetadata(
                            annotation.target().asMethod().name(),
                            description,
                            null,
                            null,
                            arguments,
                            info.declaringClass().toString(),
                            annotation.target().asMethod().returnType().name().toString())
            );
            registry.addPrompt(name, metadata);
        }
    }

    private static final DotName ELICITATION_SENDER = DotName.createSimple(ElicitationSender.class);
    private static final DotName PROGRESS = DotName.createSimple(Progress.class);

    private void processTools(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        DotName toolArg = DotName.createSimple(ToolArg.class);
        for (AnnotationInstance annotation : annotations) {
            String name = annotation.value("name") != null ? annotation.value("name").asString() : annotation.target().asMethod().name();
            String description = annotation.value("description") != null ? annotation.value("description").asString() : "";
            MethodInfo info = annotation.target().asMethod();
            List<ArgumentMetadata> arguments = new ArrayList<>();
            // Iterate all parameters in declaration order so the MethodHandle signature matches exactly.
            // @ToolArg-annotated parameters become client-supplied arguments; ElicitationSender parameters
            // are included with their type so prepareTool() can build the correct MethodHandle, but they
            // are filtered out of the input schema in ToolMessageHandler.
            for (MethodParameterInfo param : info.parameters()) {
                DotName paramTypeName = param.type().name();
                if (ELICITATION_SENDER.equals(paramTypeName)) {
                    arguments.add(new ArgumentMetadata(param.name(), "", false, ElicitationSender.class));
                } else if (PROGRESS.equals(paramTypeName)) {
                    arguments.add(new ArgumentMetadata(param.name(), "", false, Progress.class));
                } else {
                    AnnotationInstance toolArgAnnotation = param.annotation(toolArg);
                    if (toolArgAnnotation != null) {
                        String paramName = toolArgAnnotation.value("name") != null ? toolArgAnnotation.value("name").asString() : param.name();
                        boolean required = toolArgAnnotation.value("required") == null ? true : toolArgAnnotation.value("required").asBoolean();
                        String paramDescription = toolArgAnnotation.value("description") != null ? toolArgAnnotation.value("description").asString() : "";
                        Type type = JandexReflection.loadType(param.type());
                        arguments.add(new ArgumentMetadata(paramName, paramDescription, required, type));
                    }
                }
            }
            ROOT_LOGGER.debugf("Tool detected on class %s with method %s with the following annotated parameters %s", info.declaringClass(), info.name(), arguments);
            MCPFeatureMetadata metadata = new MCPFeatureMetadata(MCPFeatureMetadata.Kind.TOOL,
                    name,
                    new MethodMetadata(
                            annotation.target().asMethod().name(),
                            description,
                            null,
                            null,
                            arguments,
                            info.declaringClass().toString(),
                            annotation.target().asMethod().returnType().name().toString())
            );
            registry.addTool(name, metadata);
        }
    }

    private void processResources(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        for (AnnotationInstance annotation : annotations) {
            String name = annotation.value("name") != null ? annotation.value("name").asString() : annotation.target().asMethod().name();
            String description = annotation.value("description") != null ? annotation.value("description").asString() : "";
            String uri = annotation.value("uri") != null ? annotation.value("uri").asString() : "";
            String mimeType = annotation.value("mimeType") != null ? annotation.value("mimeType").asString() : "";
            MethodInfo info = annotation.target().asMethod();
            ROOT_LOGGER.debugf("Resource detected on class %s with method %s", info.declaringClass(), info.name());
            MCPFeatureMetadata metadata = new MCPFeatureMetadata(MCPFeatureMetadata.Kind.RESOURCE,
                    name,
                    new MethodMetadata(
                            annotation.target().asMethod().name(),
                            description,
                            uri,
                            mimeType,
                            List.of(),
                            info.declaringClass().toString(),
                            annotation.target().asMethod().returnType().name().toString())
            );
            registry.addResource(uri, metadata);
        }
    }

    private void processResourceTemplates(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        DotName resourceTemplateArg = DotName.createSimple(ResourceTemplateArg.class);
        for (AnnotationInstance annotation : annotations) {
            String name = annotation.value("name") != null ? annotation.value("name").asString() : annotation.target().asMethod().name();
            String description = annotation.value("description") != null ? annotation.value("description").asString() : "";
            String uriTemplate = annotation.value("uriTemplate") != null ? annotation.value("uriTemplate").asString() : "";
            String mimeType = annotation.value("mimeType") != null ? annotation.value("mimeType").asString() : "";
            MethodInfo info = annotation.target().asMethod();
            List<ArgumentMetadata> arguments = buildArguments(info, resourceTemplateArg);
            ROOT_LOGGER.debugf("ResourceTemplate detected on class %s with method %s with the following annotated parameters %s", info.declaringClass(), info.name(), arguments);
            MCPFeatureMetadata metadata = new MCPFeatureMetadata(MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE,
                    name,
                    new MethodMetadata(
                            annotation.target().asMethod().name(),
                            description,
                            uriTemplate,
                            mimeType,
                            arguments,
                            info.declaringClass().toString(),
                            annotation.target().asMethod().returnType().name().toString())
            );
            registry.addResourceTemplate(uriTemplate, metadata);
        }
    }

    private void processPromptCompletions(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        DotName completeArgDotName = DotName.createSimple(CompleteArg.class);
        for (AnnotationInstance annotation : annotations) {
            String promptName = annotation.value().asString();
            MethodInfo info = annotation.target().asMethod();
            List<AnnotationInstance> params = info.annotations(completeArgDotName);
            String argName = null;
            List<ArgumentMetadata> arguments = new ArrayList<>();
            for (AnnotationInstance param : params) {
                argName = param.value("name") != null ? param.value("name").asString() : param.target().asMethodParameter().name();
                Class<?> type = JandexReflection.loadRawType(param.target().asMethodParameter().type());
                arguments.add(new ArgumentMetadata(argName, "", true, type));
            }
            if (argName == null && !info.parameters().isEmpty()) {
                argName = info.parameterName(0);
                arguments.add(new ArgumentMetadata(argName, "", true, String.class));
            }
            String completionKey = promptName + "_" + argName;
            ROOT_LOGGER.debugf("CompletePrompt detected on class %s with method %s for prompt %s arg %s", info.declaringClass(), info.name(), promptName, argName);
            MCPFeatureMetadata metadata = new MCPFeatureMetadata(MCPFeatureMetadata.Kind.PROMPT_COMPLETE,
                    completionKey,
                    new MethodMetadata(
                            info.name(),
                            "",
                            null,
                            null,
                            arguments,
                            info.declaringClass().toString(),
                            info.returnType().name().toString())
            );
            registry.addPromptCompletion(completionKey, metadata);
        }
    }

    private void processResourceTemplateCompletions(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        DotName completeArgDotName = DotName.createSimple(CompleteArg.class);
        for (AnnotationInstance annotation : annotations) {
            String templateName = annotation.value().asString();
            MethodInfo info = annotation.target().asMethod();
            List<AnnotationInstance> params = info.annotations(completeArgDotName);
            String argName = null;
            List<ArgumentMetadata> arguments = new ArrayList<>();
            for (AnnotationInstance param : params) {
                argName = param.value("name") != null ? param.value("name").asString() : param.target().asMethodParameter().name();
                Class<?> type = JandexReflection.loadRawType(param.target().asMethodParameter().type());
                arguments.add(new ArgumentMetadata(argName, "", true, type));
            }
            if (argName == null && !info.parameters().isEmpty()) {
                argName = info.parameterName(0);
                arguments.add(new ArgumentMetadata(argName, "", true, String.class));
            }
            String completionKey = templateName + "_" + argName;
            ROOT_LOGGER.debugf("CompleteResourceTemplate detected on class %s with method %s for template %s arg %s", info.declaringClass(), info.name(), templateName, argName);
            MCPFeatureMetadata metadata = new MCPFeatureMetadata(MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE_COMPLETE,
                    completionKey,
                    new MethodMetadata(
                            info.name(),
                            "",
                            null,
                            null,
                            arguments,
                            info.declaringClass().toString(),
                            info.returnType().name().toString())
            );
            registry.addResourceTemplateCompletion(completionKey, metadata);
        }
    }

    private List<ArgumentMetadata> buildArguments(MethodInfo info, DotName argAnnotation) {
        List<AnnotationInstance> params = info.annotations(argAnnotation);
        List<ArgumentMetadata> arguments = new ArrayList<>();
        for (AnnotationInstance param : params) {
            String paramName = param.value("name") != null ? param.value("name").asString() : param.target().asMethodParameter().name();
            boolean required = param.value("required") == null || param.value("required").asBoolean();
            String paramDescription = param.value("description") != null ? param.value("description").asString() : "";
            Type type = JandexReflection.loadType(param.target().asMethodParameter().type());
            arguments.add(new ArgumentMetadata(paramName, paramDescription, required, type));
        }
        return arguments;
    }
}
