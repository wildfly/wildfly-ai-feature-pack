/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.deployment;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.deployment.MCPAttachments.MCP_REGISTRY_METADATA;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.ANNOTATIONS;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.AUDIENCE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESCRIPTION;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.DESTRUCTIVE_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.IDEMPOTENT_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.MIME_TYPE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.NAME;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.OPEN_WORLD_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.PRIORITY;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.READ_ONLY_HINT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.REQUIRED;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.SIZE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.STRUCTURED_CONTENT;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.TITLE;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.URI;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.URI_TEMPLATE;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.modules.ModuleLoader;
import org.wildfly.extension.mcp.Capabilities;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.mcpjava.server.Role;
import org.wildfly.extension.mcp.injection.tool.ToolAnnotations;
import org.wildfly.mcp.api.tool.InputSchema;
import org.wildfly.mcp.api.tool.OutputSchema;
import org.wildfly.mcp.api.elicitation.ElicitationSender;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.completion.CompletePrompt;
import org.mcpjava.server.completion.CompleteResourceTemplate;
import org.mcpjava.server.completion.CompleteArg;
import org.wildfly.mcp.api.completion.CompleteContext;

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
            String name = annotation.value(NAME) != null ? annotation.value(NAME).asString() : annotation.target().asMethod().name();
            String description = annotation.value(DESCRIPTION) != null ? annotation.value(DESCRIPTION).asString() : "";
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
    private static final DotName COMPLETE_CONTEXT = DotName.createSimple(CompleteContext.class);
    private static final DotName INPUT_SCHEMA = DotName.createSimple(InputSchema.class);
    private static final DotName OUTPUT_SCHEMA = DotName.createSimple(OutputSchema.class);
    private static final String GENERATOR = "generator";
    private static final String FROM = "from";

    private void processTools(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        DotName toolArg = DotName.createSimple(ToolArg.class);
        for (AnnotationInstance annotation : annotations) {
            String name = annotation.value(NAME) != null ? annotation.value(NAME).asString() : annotation.target().asMethod().name();
            String description = annotation.value(DESCRIPTION) != null ? annotation.value(DESCRIPTION).asString() : "";
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
                        String paramName = toolArgAnnotation.value(NAME) != null ? toolArgAnnotation.value(NAME).asString() : param.name();
                        boolean required = toolArgAnnotation.value(REQUIRED) == null ? true : toolArgAnnotation.value(REQUIRED).asBoolean();
                        String paramDescription = toolArgAnnotation.value(DESCRIPTION) != null ? toolArgAnnotation.value(DESCRIPTION).asString() : "";
                        Type type = JandexReflection.loadType(param.type());
                        arguments.add(new ArgumentMetadata(paramName, paramDescription, required, type));
                    }
                }
            }
            String title = annotation.value(TITLE) != null ? annotation.value(TITLE).asString() : "";
            boolean structuredContent = annotation.value(STRUCTURED_CONTENT) != null && annotation.value(STRUCTURED_CONTENT).asBoolean();
            String inputSchemaGenerator = "";
            AnnotationInstance inputSchemaAnnotation = info.annotation(INPUT_SCHEMA);
            if (inputSchemaAnnotation != null && inputSchemaAnnotation.value(GENERATOR) != null) {
                inputSchemaGenerator = inputSchemaAnnotation.value(GENERATOR).asClass().name().toString();
            }
            String outputSchemaGenerator = "";
            String outputSchemaFrom = "";
            AnnotationInstance outputSchemaAnnotation = info.annotation(OUTPUT_SCHEMA);
            if (outputSchemaAnnotation != null) {
                if (outputSchemaAnnotation.value(GENERATOR) != null) {
                    String genClass = outputSchemaAnnotation.value(GENERATOR).asClass().name().toString();
                    if (!void.class.getName().equals(genClass)) {
                        outputSchemaGenerator = genClass;
                    }
                }
                if (outputSchemaAnnotation.value(FROM) != null) {
                    String fromClass = outputSchemaAnnotation.value(FROM).asClass().name().toString();
                    if (!void.class.getName().equals(fromClass)) {
                        outputSchemaFrom = fromClass;
                    }
                }
            }
            ToolAnnotations toolAnnotations = null;
            if (annotation.value(ANNOTATIONS) != null) {
                AnnotationInstance annotationInst = annotation.value(ANNOTATIONS).asNested();
                String hintTitle = annotationInst.value(TITLE) != null ? annotationInst.value(TITLE).asString() : title;
                toolAnnotations = new ToolAnnotations(
                        hintTitle.isEmpty() ? null : hintTitle,
                        extractBoxedBooleanHint(annotationInst, READ_ONLY_HINT),
                        extractBoxedBooleanHint(annotationInst, DESTRUCTIVE_HINT),
                        extractBoxedBooleanHint(annotationInst, IDEMPOTENT_HINT),
                        extractBoxedBooleanHint(annotationInst, OPEN_WORLD_HINT));
            } else if (!title.isEmpty()) {
                toolAnnotations = new ToolAnnotations(title, null, null, null, null);
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
                            annotation.target().asMethod().returnType().name().toString()),
                    toolAnnotations, structuredContent,
                    Optional.ofNullable(inputSchemaGenerator).filter(s -> !s.isEmpty()),
                    Optional.ofNullable(outputSchemaGenerator).filter(s -> !s.isEmpty()),
                    Optional.ofNullable(outputSchemaFrom).filter(s -> !s.isEmpty())
            );
            registry.addTool(name, metadata);
        }
    }

    private void processResources(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        for (AnnotationInstance annotation : annotations) {
            String name = annotation.value(NAME) != null ? annotation.value(NAME).asString() : annotation.target().asMethod().name();
            String description = annotation.value(DESCRIPTION) != null ? annotation.value(DESCRIPTION).asString() : "";
            String uri = annotation.value(URI) != null ? annotation.value(URI).asString() : "";
            String mimeType = annotation.value(MIME_TYPE) != null ? annotation.value(MIME_TYPE).asString() : "";
            String title = annotation.value(TITLE) != null ? annotation.value(TITLE).asString() : null;
            if (title != null && title.isEmpty()) {
                title = null;
            }
            int size = annotation.value(SIZE) != null ? annotation.value(SIZE).asInt() : -1;
            MethodInfo info = annotation.target().asMethod();
            ResourceAnnotationValues resourceAnnotations = extractResourceAnnotations(annotation);
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
                            annotation.target().asMethod().returnType().name().toString()),
                    title, size, resourceAnnotations.audience(), resourceAnnotations.priority()
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
            String name = annotation.value(NAME) != null ? annotation.value(NAME).asString() : annotation.target().asMethod().name();
            String description = annotation.value(DESCRIPTION) != null ? annotation.value(DESCRIPTION).asString() : "";
            String uriTemplate = annotation.value(URI_TEMPLATE) != null ? annotation.value(URI_TEMPLATE).asString() : "";
            String mimeType = annotation.value(MIME_TYPE) != null ? annotation.value(MIME_TYPE).asString() : "";
            String title = annotation.value(TITLE) != null ? annotation.value(TITLE).asString() : null;
            if (title != null && title.isEmpty()) {
                title = null;
            }
            MethodInfo info = annotation.target().asMethod();
            List<ArgumentMetadata> arguments = buildArguments(info, resourceTemplateArg);
            ResourceAnnotationValues resourceAnnotations = extractResourceAnnotations(annotation);
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
                            annotation.target().asMethod().returnType().name().toString()),
                    title, -1, resourceAnnotations.audience(), resourceAnnotations.priority()
            );
            registry.addResourceTemplate(uriTemplate, metadata);
        }
    }

    private void processPromptCompletions(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        processCompletions(registry, annotations, MCPFeatureMetadata.Kind.PROMPT_COMPLETE, "CompletePrompt", "prompt",
                (key, metadata) -> registry.addPromptCompletion(key, metadata));
    }

    private void processResourceTemplateCompletions(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations) {
        processCompletions(registry, annotations, MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE_COMPLETE, "CompleteResourceTemplate", "template",
                (key, metadata) -> registry.addResourceTemplateCompletion(key, metadata));
    }

    private void processCompletions(WildFlyMCPRegistry registry, List<AnnotationInstance> annotations,
            MCPFeatureMetadata.Kind kind, String logPrefix, String logLabel,
            java.util.function.BiConsumer<String, MCPFeatureMetadata> registrar) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        DotName completeArgDotName = DotName.createSimple(CompleteArg.class);
        for (AnnotationInstance annotation : annotations) {
            String refName = annotation.value().asString();
            MethodInfo info = annotation.target().asMethod();
            String argName = null;
            List<ArgumentMetadata> arguments = new ArrayList<>();
            for (MethodParameterInfo param : info.parameters()) {
                DotName paramTypeName = param.type().name();
                if (COMPLETE_CONTEXT.equals(paramTypeName)) {
                    arguments.add(new ArgumentMetadata(param.name(), "", false, CompleteContext.class));
                } else {
                    AnnotationInstance completeArgAnnotation = param.annotation(completeArgDotName);
                    if (completeArgAnnotation != null) {
                        argName = completeArgAnnotation.value(NAME) != null ? completeArgAnnotation.value(NAME).asString() : param.name();
                        Class<?> type = JandexReflection.loadRawType(param.type());
                        arguments.add(new ArgumentMetadata(argName, "", true, type));
                    } else if (argName == null) {
                        argName = info.parameterName(param.position());
                        arguments.add(new ArgumentMetadata(argName, "", true, String.class));
                    }
                }
            }
            String completionKey = refName + "_" + argName;
            ROOT_LOGGER.debugf("%s detected on class %s with method %s for %s %s arg %s", logPrefix, info.declaringClass(), info.name(), logLabel, refName, argName);
            MCPFeatureMetadata metadata = new MCPFeatureMetadata(kind,
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
            registrar.accept(completionKey, metadata);
        }
    }

    private static Boolean extractBoxedBooleanHint(AnnotationInstance annotation, String name) {
        return annotation.value(name) == null ? null : annotation.value(name).asBoolean();
    }

    private record ResourceAnnotationValues(Optional<Set<Role>> audience, OptionalDouble priority) {
        static final ResourceAnnotationValues EMPTY = new ResourceAnnotationValues(Optional.empty(), OptionalDouble.empty());
    }

    private ResourceAnnotationValues extractResourceAnnotations(AnnotationInstance annotation) {
        AnnotationValue annotationsValue = annotation.value(ANNOTATIONS);
        if (annotationsValue == null) {
            return ResourceAnnotationValues.EMPTY;
        }
        AnnotationInstance nested = annotationsValue.asNested();
        Optional<Set<Role>> audience = nested.value(AUDIENCE) != null
                ? Optional.of(Set.of(Role.valueOf(nested.value(AUDIENCE).asEnum())))
                : Optional.empty();
        OptionalDouble priority = nested.value(PRIORITY) != null
                ? OptionalDouble.of(nested.value(PRIORITY).asDouble())
                : OptionalDouble.empty();
        return new ResourceAnnotationValues(audience, priority);
    }

    private List<ArgumentMetadata> buildArguments(MethodInfo info, DotName argAnnotation) {
        List<AnnotationInstance> params = info.annotations(argAnnotation);
        List<ArgumentMetadata> arguments = new ArrayList<>();
        for (AnnotationInstance param : params) {
            String paramName = param.value(NAME) != null ? param.value(NAME).asString() : param.target().asMethodParameter().name();
            boolean required = param.value(REQUIRED) == null || param.value(REQUIRED).asBoolean();
            String paramDescription = param.value(DESCRIPTION) != null ? param.value(DESCRIPTION).asString() : "";
            Type type = JandexReflection.loadType(param.target().asMethodParameter().type());
            arguments.add(new ArgumentMetadata(paramName, paramDescription, required, type));
        }
        return arguments;
    }
}
