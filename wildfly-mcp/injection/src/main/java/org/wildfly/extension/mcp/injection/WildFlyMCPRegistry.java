/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

import static org.wildfly.extension.mcp.injection.MCPLogger.ROOT_LOGGER;

public class WildFlyMCPRegistry {

    /**
     * Recommended pattern for tool names as defined in the MCP spec.
     * Tool names MUST only contain ASCII letters, digits, underscore, hyphen, and dot,
     * and be between 1 and 128 characters in length.
     */
    public static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\-]{1,128}$");

    private final Map<String, MCPFeatureMetadata> tools = new HashMap<>();
    private final Map<String, MCPFeatureMetadata> prompts = new HashMap<>();
    private final Map<String, MCPFeatureMetadata> resources = new HashMap<>();
    private final Map<String, MCPFeatureMetadata> resourceTemplates = new HashMap<>();
    private final Map<String, MCPFeatureMetadata> promptCompletions = new HashMap<>();
    private final Map<String, MCPFeatureMetadata> resourceTemplateCompletions = new HashMap<>();
    private final Map<String, MethodHandle> toolInvokers = new HashMap<>();
    private final Map<String, MethodHandle> promptInvokers = new HashMap<>();
    private final Map<String, MethodHandle> resourceInvokers = new HashMap<>();
    private final Map<String, MethodHandle> resourceTemplateInvokers = new HashMap<>();
    private final Map<String, MethodHandle> promptCompletionInvokers = new HashMap<>();
    private final Map<String, MethodHandle> resourceTemplateCompletionInvokers = new HashMap<>();
    private final MethodHandles.Lookup lookup = MethodHandles.lookup();

    public Iterable<MCPFeatureMetadata> listTools() {
        return tools.values();
    }

    public Iterable<MCPFeatureMetadata> listPrompts() {
        return prompts.values();
    }

    public Iterable<MCPFeatureMetadata> listResources() {
        return resources.values();
    }

    public void addTool(String name, MCPFeatureMetadata metadata) {
        if (!TOOL_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Tool name [" + name + "] does not match the required pattern: " + TOOL_NAME_PATTERN.pattern());
        }
        tools.put(name, metadata);
    }

    public void addPrompt(String name, MCPFeatureMetadata metadata) {
        prompts.put(name, metadata);
    }

    public void addResource(String uri, MCPFeatureMetadata metadata) {
        resources.put(uri, metadata);
    }

    public void addResourceTemplate(String uriTemplate, MCPFeatureMetadata metadata) {
        resourceTemplates.put(uriTemplate, metadata);
    }

    public void addPromptCompletion(String key, MCPFeatureMetadata metadata) {
        promptCompletions.put(key, metadata);
    }

    public void addResourceTemplateCompletion(String key, MCPFeatureMetadata metadata) {
        resourceTemplateCompletions.put(key, metadata);
    }

    public MCPFeatureMetadata getPromptCompletion(String key) {
        return promptCompletions.get(key);
    }

    public MCPFeatureMetadata getResourceTemplateCompletion(String key) {
        return resourceTemplateCompletions.get(key);
    }

    public Iterable<MCPFeatureMetadata> listPromptCompletions() {
        return promptCompletions.values();
    }

    public Iterable<MCPFeatureMetadata> listResourceTemplateCompletions() {
        return resourceTemplateCompletions.values();
    }

    public MethodHandle getPromptCompletionInvoker(String key) {
        return promptCompletionInvokers.get(key);
    }

    public MethodHandle getResourceTemplateCompletionInvoker(String key) {
        return resourceTemplateCompletionInvokers.get(key);
    }

    public MCPFeatureMetadata getTool(String tool) {
        return tools.get(tool);
    }

    public MCPFeatureMetadata getPrompt(String prompt) {
        return prompts.get(prompt);
    }

    public MCPFeatureMetadata getResource(String resource) {
        return resources.get(resource);
    }

    public Iterable<MCPFeatureMetadata> listResourceTemplates() {
        return resourceTemplates.values();
    }

    public MCPFeatureMetadata getResourceTemplate(String uriTemplate) {
        return resourceTemplates.get(uriTemplate);
    }

    public MCPFeatureMetadata findResourceTemplateByUri(String uri) {
        for (Map.Entry<String, MCPFeatureMetadata> entry : resourceTemplates.entrySet()) {
            if (matchesUriTemplate(entry.getKey(), uri)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesUriTemplate(String template, String uri) {
        String regex = template.replaceAll("\\{[^}]+}", "([^/]+)");
        return uri.matches(regex);
    }

    public MethodHandle getToolInvoker(String tool) {
        return toolInvokers.get(tool);
    }

    public MethodHandle getPromptInvoker(String prompt) {
        return promptInvokers.get(prompt);
    }

    public MethodHandle getResourceInvoker(String uri) {
        return resourceInvokers.get(uri);
    }

    public MethodHandle getResourceTemplateInvoker(String uriTemplate) {
        return resourceTemplateInvokers.get(uriTemplate);
    }

    public MethodHandle findResourceTemplateInvokerByUri(String uri) {
        for (Map.Entry<String, MCPFeatureMetadata> entry : resourceTemplates.entrySet()) {
            if (matchesUriTemplate(entry.getKey(), uri)) {
                return resourceTemplateInvokers.get(entry.getKey());
            }
        }
        return null;
    }

    public void prepareTool(String toolName, Class<?> clazz) {
        try {
            MethodMetadata method = tools.get(toolName).method();
            Class returnClass;
            switch (method.returnType()) {
                case "int":
                    returnClass = int.class;
                    break;
                case "float":
                    returnClass = float.class;
                    break;
                case "long":
                    returnClass = long.class;
                    break;
                case "double":
                    returnClass = double.class;
                    break;
                case "char":
                    returnClass = char.class;
                    break;
                default:
                    returnClass = Class.forName(method.returnType(), true, clazz.getClassLoader());
                    break;

            }
            MethodType mt = MethodType.methodType(returnClass, method.argumentTypes());
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, lookup);
            MethodHandle handle = privateLookup.findVirtual(clazz, method.name(), mt);
            toolInvokers.put(toolName, handle);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ex) {
            ROOT_LOGGER.unexpectedError(ex);
        }
    }

    public void preparePrompt(String promptName, Class<?> clazz) {
        try {
            MethodMetadata method = prompts.get(promptName).method();
            MethodType mt = MethodType.methodType(Class.forName(method.returnType(), true, clazz.getClassLoader()), method.argumentTypes());
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, lookup);
            MethodHandle handle = privateLookup.findVirtual(clazz, method.name(), mt);
            promptInvokers.put(promptName, handle);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ex) {
            ROOT_LOGGER.unexpectedError(ex);
        }
    }

    public void prepareResource(String resourceUri, Class<?> clazz) {
        try {
            MethodMetadata method = resources.get(resourceUri).method();
            MethodType mt = MethodType.methodType(Class.forName(method.returnType(), true, clazz.getClassLoader()), method.argumentTypes());
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, lookup);
            MethodHandle handle = privateLookup.findVirtual(clazz, method.name(), mt);
            resourceInvokers.put(resourceUri, handle);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ex) {
            ROOT_LOGGER.unexpectedError(ex);
        }
    }

    public void prepareResourceTemplate(String uriTemplate, Class<?> clazz) {
        try {
            MethodMetadata method = resourceTemplates.get(uriTemplate).method();
            MethodType mt = MethodType.methodType(Class.forName(method.returnType(), true, clazz.getClassLoader()), method.argumentTypes());
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, lookup);
            MethodHandle handle = privateLookup.findVirtual(clazz, method.name(), mt);
            resourceTemplateInvokers.put(uriTemplate, handle);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ex) {
            ROOT_LOGGER.unexpectedError(ex);
        }
    }

    public void preparePromptCompletion(String key, Class<?> clazz) {
        try {
            MethodMetadata method = promptCompletions.get(key).method();
            MethodType mt = MethodType.methodType(Class.forName(method.returnType(), true, clazz.getClassLoader()), method.argumentTypes());
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, lookup);
            MethodHandle handle = privateLookup.findVirtual(clazz, method.name(), mt);
            promptCompletionInvokers.put(key, handle);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ex) {
            ROOT_LOGGER.unexpectedError(ex);
        }
    }

    public void prepareResourceTemplateCompletion(String key, Class<?> clazz) {
        try {
            MethodMetadata method = resourceTemplateCompletions.get(key).method();
            MethodType mt = MethodType.methodType(Class.forName(method.returnType(), true, clazz.getClassLoader()), method.argumentTypes());
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, lookup);
            MethodHandle handle = privateLookup.findVirtual(clazz, method.name(), mt);
            resourceTemplateCompletionInvokers.put(key, handle);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ex) {
            ROOT_LOGGER.unexpectedError(ex);
        }
    }
}
