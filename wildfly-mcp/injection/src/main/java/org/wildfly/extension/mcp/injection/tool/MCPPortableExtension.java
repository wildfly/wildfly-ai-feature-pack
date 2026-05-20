/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.SingletonLiteral;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.AnnotationLiteral;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.wildfly.extension.mcp.injection.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;

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
