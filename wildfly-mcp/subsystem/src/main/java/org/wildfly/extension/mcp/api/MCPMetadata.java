package org.wildfly.extension.mcp.api;

import java.util.ArrayList;
import java.util.List;
import org.mcpjava.server.completion.CompletionResult;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.tools.ToolResponse;

public class MCPMetadata {

    private final List<FeatureMetadata<PromptResponse>> prompts;
    private final List<FeatureMetadata<CompletionResult>> promptCompletions;
    private final List<FeatureMetadata<ToolResponse>> tools;
    private final List<FeatureMetadata<ResourceResponse>> resources;
    private final List<FeatureMetadata<ResourceResponse>> resourceTemplates;
    private final List<FeatureMetadata<CompletionResult>> resourceTemplateCompletions;

    public MCPMetadata() {
        this.prompts = new ArrayList<>();
        this.promptCompletions = new ArrayList<>();
        this.tools = new ArrayList<>();
        this.resources = new ArrayList<>();
        this.resourceTemplates = new ArrayList<>();
        this.resourceTemplateCompletions = new ArrayList<>();
    }

    public void registerPrompt(FeatureMetadata<PromptResponse> prompt) {
        this.prompts.add(prompt);
    }

    public void registerPromptCompletions(FeatureMetadata<CompletionResult> promptCompletion) {
        this.promptCompletions.add(promptCompletion);
    }

    public void registerTool(FeatureMetadata<ToolResponse> tool) {
        this.tools.add(tool);
    }

    public void registerResource(FeatureMetadata<ResourceResponse> resource) {
        this.resources.add(resource);
    }

    public void registerResourceTemplate(FeatureMetadata<ResourceResponse> resourceTemplate) {
        this.resourceTemplates.add(resourceTemplate);
    }

    public void registerResourceTemplateCompletion(FeatureMetadata<CompletionResult> resourceTemplateCompletion) {
        this.resourceTemplateCompletions.add(resourceTemplateCompletion);
    }

    public List<FeatureMetadata<PromptResponse>> prompts() {
        return prompts;
    }

    public List<FeatureMetadata<CompletionResult>> promptCompletions() {
        return promptCompletions;
    }

    public List<FeatureMetadata<ToolResponse>> tools() {
        return tools;
    }

    public List<FeatureMetadata<ResourceResponse>> resources() {
        return resources;
    }

    public List<FeatureMetadata<ResourceResponse>> resourceTemplates() {
        return resourceTemplates;
    }

    public List<FeatureMetadata<CompletionResult>> resourceTemplateCompletions() {
        return resourceTemplateCompletions;
    }

}
