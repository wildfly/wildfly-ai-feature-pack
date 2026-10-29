/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;

import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;

import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.deployment.Phase;
import org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.MistralAIChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.OllamaChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.OpenAIChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.deployment.AIDependencyProcessor;
import org.wildfly.extension.ai.deployment.AIDeploymentProcessor;
import org.wildfly.extension.ai.embedding.model.InMemoryEmbeddingModelProviderRegistrar;
import org.wildfly.extension.ai.embedding.model.OllamaEmbeddingModelProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.ChromaEmbeddingStoreProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.InMemoryEmbeddingStoreProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar;
import org.wildfly.extension.ai.rag.retriever.EmbeddingStoreContentRetrieverProviderRegistrar;
import org.wildfly.extension.ai.rag.retriever.WebSearchContentContentRetrieverProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.WeaviateEmbeddingStoreProviderRegistrar;
import org.wildfly.extension.ai.mcp.client.McpClientSseProviderRegistrar;
import org.wildfly.extension.ai.mcp.client.McpClientStdioProviderRegistrar;
import org.wildfly.extension.ai.mcp.client.McpClientStreamableProviderRegistrar;
import org.wildfly.extension.ai.mcp.client.McpToolProviderProviderRegistrar;
import org.wildfly.extension.ai.memory.ChatMemoryProviderRegistrar;
import org.wildfly.extension.ai.rag.retriever.Neo4JContentRetrieverProviderRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;

/**
 * Registrar for the WildFly AI subsystem.
 *
 * <p>This class orchestrates the registration of all AI-related management resources
 * and deployment processors in the WildFly server. It acts as the central registration
 * point for the entire AI subsystem.</p>
 *
 * <p>The registrar performs the following registrations:</p>
 *
 * <h3>Deployment Processors</h3>
 * <ul>
 *   <li>{@link AIDependencyProcessor} - Adds AI module dependencies (Phase: DEPENDENCIES)</li>
 *   <li>{@link AIDeploymentProcessor} - Processes AI resources in deployments (Phase: POST_MODULE)</li>
 * </ul>
 *
 * <h3>Chat Model Providers</h3>
 * <ul>
 *   <li>Gemini - Google's Gemini AI models</li>
 *   <li>Ollama - Local LLM runtime</li>
 *   <li>OpenAI - ChatGPT and GPT models</li>
 *   <li>Mistral AI - Mistral's models</li>
 * </ul>
 *
 * <h3>Embedding Model Providers</h3>
 * <ul>
 *   <li>In-Memory - Local ONNX-based models (All-MiniLM-L6-v2)</li>
 *   <li>Ollama - Ollama-based embeddings</li>
 * </ul>
 *
 * <h3>Embedding Store Providers</h3>
 * <ul>
 *   <li>Chroma - ChromaDB vector database</li>
 *   <li>In-Memory - Volatile in-memory store</li>
 *   <li>Neo4j - Neo4j graph database</li>
 *   <li>Weaviate - Weaviate vector database</li>
 * </ul>
 *
 * <h3>Content Retriever Providers</h3>
 * <ul>
 *   <li>Embedding Store - Semantic search using embedding stores</li>
 *   <li>Neo4j - Graph-based retrieval</li>
 *   <li>Web Search - Google/Tavily web search integration</li>
 * </ul>
 *
 * <h3>MCP (Model Context Protocol) Providers</h3>
 * <ul>
 *   <li>Tool Providers - MCP function calling</li>
 *   <li>Streamable Clients - Generic streamable MCP connections</li>
 *   <li>SSE Clients - Server-Sent Events transport</li>
 *   <li>Stdio Clients - Standard input/output transport</li>
 * </ul>
 *
 * <h3>Chat Memory Providers</h3>
 * <ul>
 *   <li>Chat Memory - Conversation history management</li>
 * </ul>
 *
 * @see SubsystemResourceDefinitionRegistrar
 * @see AIExtension
 */
class AISubsystemRegistrar implements SubsystemResourceDefinitionRegistrar {

    /** Subsystem name used in configuration (subsystem xmlns="urn:wildfly:ai:1.0"). */
    public static final String NAME = "ai";

    static final SubsystemResourceRegistration REGISTRATION = SubsystemResourceRegistration.of(NAME, Stability.EXPERIMENTAL);
    static final ParentResourceDescriptionResolver RESOLVER = new SubsystemResourceDescriptionResolver(NAME, AISubsystemRegistrar.class);

    /** Phase priority for AI dependency injection (Phase.DEPENDENCIES). */
    private static final int PHASE_DEPENDENCIES_AI = 0x1930;

    /** Phase priority for AI deployment processing (Phase.POST_MODULE). */
    private static final int PHASE_POST_MODULE_AI = 0x3840;

    /**
     * Registers all AI subsystem resources and deployment processors.
     *
     * <p>This method is called during server startup to register:</p>
     * <ol>
     *   <li>Subsystem model definition</li>
     *   <li>Deployment chain contributors (processors)</li>
     *   <li>All AI provider resource registrars</li>
     *   <li>Deployment model for runtime AI resources</li>
     * </ol>
     *
     * @param parent the parent subsystem registration
     * @param context the management resource registration context
     * @return the management resource registration for this subsystem
     */
    @Override
    public ManagementResourceRegistration register(SubsystemRegistration parent, ManagementResourceRegistrationContext context) {
        parent.setHostCapable();
        ManagementResourceRegistration registration = parent.registerSubsystemModel(ResourceDefinition.builder(REGISTRATION, RESOLVER).build());
        ResourceDescriptor descriptor = ResourceDescriptor
                .builder(RESOLVER)
                .withDeploymentChainContributor(target -> {
                    target.addDeploymentProcessor(NAME, Phase.DEPENDENCIES, PHASE_DEPENDENCIES_AI, new AIDependencyProcessor());
                    target.addDeploymentProcessor(NAME, Phase.POST_MODULE, PHASE_POST_MODULE_AI, new AIDeploymentProcessor());
                })
                .build();
        ManagementResourceRegistrar.of(descriptor).register(registration);
        new ChatMemoryProviderRegistrar(RESOLVER).register(registration, context);

        new GeminiChatLanguageModelProviderRegistrar(RESOLVER).register(registration, context);
        new OllamaChatLanguageModelProviderRegistrar(RESOLVER).register(registration, context);
        new OpenAIChatLanguageModelProviderRegistrar(RESOLVER).register(registration, context);
        new MistralAIChatLanguageModelProviderRegistrar(RESOLVER).register(registration, context);

        new InMemoryEmbeddingModelProviderRegistrar(RESOLVER).register(registration, context);
        new OllamaEmbeddingModelProviderRegistrar(RESOLVER).register(registration, context);

        new ChromaEmbeddingStoreProviderRegistrar(RESOLVER).register(registration, context);
        new InMemoryEmbeddingStoreProviderRegistrar(RESOLVER).register(registration, context);
        new Neo4jEmbeddingStoreProviderRegistrar(RESOLVER).register(registration, context);
        new WeaviateEmbeddingStoreProviderRegistrar(RESOLVER).register(registration, context);

        new EmbeddingStoreContentRetrieverProviderRegistrar(RESOLVER).register(registration, context);
        new Neo4JContentRetrieverProviderRegistrar(RESOLVER).register(registration, context);
        new WebSearchContentContentRetrieverProviderRegistrar(RESOLVER).register(registration, context);

        new McpToolProviderProviderRegistrar(RESOLVER).register(registration, context);
        new McpClientStreamableProviderRegistrar(RESOLVER).register(registration, context);
        new McpClientSseProviderRegistrar(RESOLVER).register(registration, context);
        new McpClientStdioProviderRegistrar(RESOLVER).register(registration, context);

        ParentResourceDescriptionResolver deploymentResolver = RESOLVER.createChildResolver(pathElement(DEPLOYMENT));
        ManagementResourceRegistration deploymentRegistration = parent.registerDeploymentModel(ResourceDefinition.builder(REGISTRATION, deploymentResolver).asRuntime().asNonFeature().build());
        new AIModelDeploymentRegistrar(deploymentResolver).register(deploymentRegistration, context);
        return registration;
    }
}
