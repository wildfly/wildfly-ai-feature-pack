/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.ai;

import org.wildfly.subsystem.SubsystemConfiguration;
import org.wildfly.subsystem.SubsystemExtension;
import org.wildfly.subsystem.SubsystemPersistence;

/**
 * WildFly extension providing AI and LLM integration capabilities.
 *
 * <p>This extension enables WildFly application servers to integrate with various
 * AI services and Large Language Models (LLMs) through the LangChain4j framework.
 * It provides a subsystem for configuring and managing:</p>
 * <ul>
 *   <li>Chat models (OpenAI, Ollama, Gemini, Mistral AI)</li>
 *   <li>Embedding models (local and remote)</li>
 *   <li>Embedding stores (in-memory, Neo4j, Weaviate, Chroma)</li>
 *   <li>Content retrievers for RAG (Retrieval-Augmented Generation)</li>
 *   <li>Chat memory providers</li>
 *   <li>MCP (Model Context Protocol) clients</li>
 * </ul>
 *
 * <p>The extension integrates with WildFly's CDI container to make AI components
 * available for injection into applications.</p>
 *
 * @see AISubsystemRegistrar
 * @see AISubsystemSchema
 */
public class AIExtension extends SubsystemExtension<AISubsystemSchema> {

    /**
     * Creates a new instance of the AI extension.
     *
     * <p>Configures the subsystem with the current model version and schema,
     * registering all AI-related resources and capabilities.</p>
     */
    public AIExtension() {
        super(SubsystemConfiguration.of(AISubsystemRegistrar.NAME, AISubsystemModel.CURRENT, AISubsystemRegistrar::new), SubsystemPersistence.of(AISubsystemSchema.CURRENT));
    }
}
