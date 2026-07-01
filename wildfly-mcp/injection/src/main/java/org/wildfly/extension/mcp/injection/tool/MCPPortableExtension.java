/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.SingletonLiteral;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.mcpjava.server.progress.Progress;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSenderBean;
import org.wildfly.extension.mcp.injection.progress.ProgressBean;
import org.wildfly.mcp.api.elicitation.ElicitationSender;

import static org.wildfly.extension.mcp.injection.MCPLogger.ROOT_LOGGER;

public class MCPPortableExtension implements Extension {
    
    private final WildFlyMCPRegistry registry;
    private final ClassLoader deploymentClassLoader;

    public MCPPortableExtension(WildFlyMCPRegistry registry, ClassLoader deploymentClassLoader) {
        this.registry = registry;
        this.deploymentClassLoader = deploymentClassLoader;
    }
    
    public void atd(@Observes AfterTypeDiscovery atd) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        Map<Class<?>, Set<AnnotationLiteral>> beanClasses = new HashMap<>();
        Map<Class<?>, String> ids = new HashMap<>();
        for (MCPFeatureMetadata tool : registry.listTools()) {
            String className = tool.method().declaringClassName();
            ROOT_LOGGER.debugf("Adding %s to CDI for discovery", className);
            try {
                Class<?> clazz = Class.forName(className, true, deploymentClassLoader);
                registry.prepareTool(tool.name(), clazz);
                updateAnnotations(beanClasses, clazz, MCPTool.MCPToolLiteral.INSTANCE);
                ids.putIfAbsent(clazz, tool.name() + "-" + tool.method().name());
            } catch (ClassNotFoundException ex) {
                ROOT_LOGGER.unexpectedError(ex);
            }
        }
        for (MCPFeatureMetadata prompt : registry.listPrompts()) {
            String className = prompt.method().declaringClassName();
            ROOT_LOGGER.debugf("Adding %s to CDI for discovery", className);
            try {
                Class clazz = Class.forName(className, true, deploymentClassLoader);
                registry.preparePrompt(prompt.name(), clazz);
                updateAnnotations(beanClasses, clazz, MCPPrompt.MCPPromptLiteral.INSTANCE);
                ids.putIfAbsent(clazz, prompt.name() + "-" + prompt.method().name());
            } catch (ClassNotFoundException ex) {
                ROOT_LOGGER.unexpectedError(ex);
            }
        }
        for (MCPFeatureMetadata resource : registry.listResources()) {
            String className = resource.method().declaringClassName();
            ROOT_LOGGER.debugf("Adding %s to CDI for discovery", className);
            try {
                Class clazz = Class.forName(className, true, deploymentClassLoader);
                registry.prepareResource(resource.method().uri(), clazz);
                updateAnnotations(beanClasses, clazz, MCPResource.MCPResourceLiteral.INSTANCE);
                ids.putIfAbsent(clazz, resource.method().uri() + "-" + resource.method().name());
            } catch (ClassNotFoundException ex) {
                ROOT_LOGGER.unexpectedError(ex);
            }
        }
        for (MCPFeatureMetadata resourceTemplate : registry.listResourceTemplates()) {
            String className = resourceTemplate.method().declaringClassName();
            ROOT_LOGGER.debugf("Adding %s to CDI for discovery", className);
            try {
                Class clazz = Class.forName(className, true, deploymentClassLoader);
                registry.prepareResourceTemplate(resourceTemplate.method().uri(), clazz);
                updateAnnotations(beanClasses, clazz, MCPResource.MCPResourceLiteral.INSTANCE);
                ids.putIfAbsent(clazz, resourceTemplate.method().uri() + "-" + resourceTemplate.method().name());
            } catch (ClassNotFoundException ex) {
                ROOT_LOGGER.unexpectedError(ex);
            }
        }
        for (MCPFeatureMetadata completion : registry.listPromptCompletions()) {
            String className = completion.method().declaringClassName();
            try {
                Class clazz = Class.forName(className, true, deploymentClassLoader);
                registry.preparePromptCompletion(completion.name(), clazz);
                updateAnnotations(beanClasses, clazz, MCPPrompt.MCPPromptLiteral.INSTANCE);
                ids.putIfAbsent(clazz, completion.name() + "-" + completion.method().name());
            } catch (ClassNotFoundException ex) {
                ROOT_LOGGER.unexpectedError(ex);
            }
        }
        for (MCPFeatureMetadata completion : registry.listResourceTemplateCompletions()) {
            String className = completion.method().declaringClassName();
            try {
                Class clazz = Class.forName(className, true, deploymentClassLoader);
                registry.prepareResourceTemplateCompletion(completion.name(), clazz);
                updateAnnotations(beanClasses, clazz, MCPResource.MCPResourceLiteral.INSTANCE);
                ids.putIfAbsent(clazz, completion.name() + "-" + completion.method().name());
            } catch (ClassNotFoundException ex) {
                ROOT_LOGGER.unexpectedError(ex);
            }
        }
        for (Map.Entry<Class<?>, Set<AnnotationLiteral>> bean : beanClasses.entrySet()) {
            AnnotatedTypeConfigurator config = atd.addAnnotatedType(bean.getKey(), ids.get(bean.getKey())).add(SingletonLiteral.INSTANCE);
            for (AnnotationLiteral annotation : bean.getValue()) {
                config.add(annotation);
            }
            ROOT_LOGGER.debugf("%s should be discoverable by CDI", bean.getKey().getName());
        }
        atd.addAnnotatedType(ElicitationSenderBean.class, "elicitation-sender-bean");
        atd.addAnnotatedType(ProgressBean.class, "progress-bean");
    }

    public <T extends ElicitationSender> void vetoUserElicitationSender(@Observes ProcessAnnotatedType<T> event) {
        if (!ElicitationSenderBean.class.equals(event.getAnnotatedType().getJavaClass())) {
            ROOT_LOGGER.vetoedCDIBean(ElicitationSender.class.getName(), event.getAnnotatedType().getJavaClass().getName());
            event.veto();
        }
    }

    public <X> void vetoUserElicitationSenderProducer(@Observes ProcessProducerMethod<ElicitationSender, X> event) {
        String name = event.getAnnotatedProducerMethod().getJavaMember().toGenericString();
        ROOT_LOGGER.vetoedCDIBean(ElicitationSender.class.getName(), name);
        event.addDefinitionError(ROOT_LOGGER.deploymentMustNotProduceBean(name));
    }

    public <X> void vetoUserElicitationSenderField(@Observes ProcessProducerField<ElicitationSender, X> event) {
        String name = event.getAnnotatedProducerField().getJavaMember().toGenericString();
        ROOT_LOGGER.vetoedCDIBean(ElicitationSender.class.getName(), name);
        event.addDefinitionError(ROOT_LOGGER.deploymentMustNotProduceBean(name));
    }

    public <T extends Progress> void vetoUserProgress(@Observes ProcessAnnotatedType<T> event) {
        if (!ProgressBean.class.equals(event.getAnnotatedType().getJavaClass())) {
            ROOT_LOGGER.vetoedCDIBean(Progress.class.getName(), event.getAnnotatedType().getJavaClass().getName());
            event.veto();
        }
    }

    public <X> void vetoUserProgressProducer(@Observes ProcessProducerMethod<Progress, X> event) {
        String name = event.getAnnotatedProducerMethod().getJavaMember().toGenericString();
        ROOT_LOGGER.vetoedCDIBean(Progress.class.getName(), name);
        event.addDefinitionError(ROOT_LOGGER.deploymentMustNotProduceBean(name));
    }

    public <X> void vetoUserProgressField(@Observes ProcessProducerField<Progress, X> event) {
        String name = event.getAnnotatedProducerField().getJavaMember().toGenericString();
        ROOT_LOGGER.vetoedCDIBean(Progress.class.getName(), name);
        event.addDefinitionError(ROOT_LOGGER.deploymentMustNotProduceBean(name));
    }

    private void updateAnnotations(Map<Class<?>, Set<AnnotationLiteral>> beanClasses, Class<?> clazz, AnnotationLiteral... annotations) {
        if (!beanClasses.containsKey(clazz)) {
            beanClasses.put(clazz, new HashSet<AnnotationLiteral>());
        }
        for (AnnotationLiteral<?> annotation : annotations) {
            beanClasses.get(clazz).add(annotation);
        }
    }
}
