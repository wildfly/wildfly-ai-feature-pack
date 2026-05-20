/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;

import static org.wildfly.extension.ai.AIAttributeDefinitions.CREDENTIAL_REFERENCE;

import org.wildfly.extension.ai.chat.OpenAIChatLanguageModelProviderRegistrar;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceXMLChoice;
import org.jboss.as.controller.persistence.xml.ResourceXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;
import org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.GithubModelChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.MistralAIChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.OllamaChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.embedding.model.InMemoryEmbeddingModelProviderRegistrar;
import org.wildfly.extension.ai.embedding.model.OllamaEmbeddingModelProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.ChromaEmbeddingStoreProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.InMemoryEmbeddingStoreProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar;

import org.wildfly.extension.ai.rag.retriever.EmbeddingStoreContentRetrieverProviderRegistrar;
import org.wildfly.extension.ai.rag.retriever.WebSearchContentContentRetrieverProviderRegistrar;
import org.wildfly.extension.ai.embedding.store.WeaviateEmbeddingStoreProviderRegistrar;
import org.wildfly.extension.ai.mcp.client.McpToolProviderProviderRegistrar;
import org.wildfly.extension.ai.mcp.client.McpClientSseProviderRegistrar;
import org.wildfly.extension.ai.mcp.client.McpClientStdioProviderRegistrar;
import org.wildfly.extension.ai.memory.ChatMemoryProviderRegistrar;
import org.wildfly.extension.ai.rag.retriever.Neo4JContentRetrieverProviderRegistrar;

/**
 * Enumeration of AI subsystem schema versions.
 */
enum AISubsystemSchema implements SubsystemResourceXMLSchema<AISubsystemSchema> {
    VERSION_1_0(1, 0),;
    static final AISubsystemSchema CURRENT = VERSION_1_0;
    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);

    private final VersionedNamespace<IntVersion, AISubsystemSchema> namespace;

    AISubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(AISubsystemRegistrar.NAME, Stability.EXPERIMENTAL, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, AISubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        ResourceXMLChoice providerChoices = this.factory.choice().withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                .addElement(this.chatLanguageModels())
                .addElement(this.embeddingModels())
                .addElement(this.embeddingStores())
                .addElement(this.contentRetrievers())
                .addElement(this.chatMemories())
                .addElement(this.mcp())
                .build();
        return this.factory.subsystemElement(AISubsystemRegistrar.REGISTRATION)
                .withContent(providerChoices)
                .build();
    }

    private ResourceXMLElement chatLanguageModels() {
        return this.factory.element(this.resolve("chat-language-models"))
                .withContent(
                        this.factory
                                .choice()
                                .withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                                .addElement(this.factory.namedElement(GeminiChatLanguageModelProviderRegistrar.REGISTRATION).addAttributes(GeminiChatLanguageModelProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(GithubModelChatLanguageModelProviderRegistrar.REGISTRATION).addAttributes(GithubModelChatLanguageModelProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(OllamaChatLanguageModelProviderRegistrar.REGISTRATION).addAttributes(OllamaChatLanguageModelProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(OpenAIChatLanguageModelProviderRegistrar.REGISTRATION).addAttributes(OpenAIChatLanguageModelProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(MistralAIChatLanguageModelProviderRegistrar.REGISTRATION).addAttributes(MistralAIChatLanguageModelProviderRegistrar.ATTRIBUTES).build())
                                .build()
                ).build();
    }

    private ResourceXMLElement embeddingModels() {
        return this.factory.element(this.resolve("embedding-models"))
                .withContent(
                        this.factory
                                .choice()
                                .withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                                .addElement(this.factory.namedElement(OllamaEmbeddingModelProviderRegistrar.REGISTRATION).addAttributes(OllamaEmbeddingModelProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(InMemoryEmbeddingModelProviderRegistrar.REGISTRATION).addAttributes(InMemoryEmbeddingModelProviderRegistrar.ATTRIBUTES).build())
                                .build()
                ).build();
    }

    private ResourceXMLElement embeddingStores() {
        return this.factory.element(this.resolve("embedding-stores"))
                .withContent(
                        this.factory
                                .choice()
                                .withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                                .addElement(this.factory.namedElement(ChromaEmbeddingStoreProviderRegistrar.REGISTRATION).addAttributes(ChromaEmbeddingStoreProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(InMemoryEmbeddingStoreProviderRegistrar.REGISTRATION).addAttributes(InMemoryEmbeddingStoreProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(Neo4jEmbeddingStoreProviderRegistrar.REGISTRATION).addAttributes(Neo4jEmbeddingStoreProviderRegistrar.ATTRIBUTES)
                                        .withContent(this.factory.choice().withCardinality(XMLCardinality.Single.OPTIONAL).addElement(CREDENTIAL_REFERENCE).build()).build())
                                .addElement(this.factory.namedElement(WeaviateEmbeddingStoreProviderRegistrar.REGISTRATION).addAttributes(WeaviateEmbeddingStoreProviderRegistrar.ATTRIBUTES).build())
                                .build()
                ).build();
    }

    private ResourceXMLElement contentRetrievers() {
        return this.factory.element(this.resolve("content-retrievers"))
                .withContent(
                        this.factory
                                .choice()
                                .withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                                .addElement(this.factory.namedElement(EmbeddingStoreContentRetrieverProviderRegistrar.REGISTRATION).addAttributes(EmbeddingStoreContentRetrieverProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(Neo4JContentRetrieverProviderRegistrar.REGISTRATION).addAttributes(Neo4JContentRetrieverProviderRegistrar.ATTRIBUTES)
                                        .withContent(this.factory.choice().withCardinality(XMLCardinality.Single.OPTIONAL).addElement(CREDENTIAL_REFERENCE).build()).build())
                                .addElement(this.factory.namedElement(WebSearchContentContentRetrieverProviderRegistrar.REGISTRATION).addAttributes(WebSearchContentContentRetrieverProviderRegistrar.ATTRIBUTES)
                                        .withContent(this.factory.choice()
                                                .addElement(WebSearchContentContentRetrieverProviderRegistrar.GOOGLE_SEARCH_ENGINE)
                                                .addElement(WebSearchContentContentRetrieverProviderRegistrar.TAVILY_SEARCH_ENGINE)
                                                .build())
                                        .build())
                                .build()
                ).build();
    }


    private ResourceXMLElement chatMemories() {
        return this.factory.element(this.resolve("chat-memories"))
                .withContent(
                        this.factory
                                .choice()
                                .withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                                .addElement(this.factory.namedElement(ChatMemoryProviderRegistrar.REGISTRATION).addAttributes(ChatMemoryProviderRegistrar.ATTRIBUTES).build())
                                .build()
                ).build();
    }
    private ResourceXMLElement mcp() {
        return this.factory.element(this.resolve("mcp"))
                .withContent(
                        this.factory
                                .choice()
                                .withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                                .addElement(this.factory.namedElement(McpToolProviderProviderRegistrar.REGISTRATION).addAttributes(McpToolProviderProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(McpClientSseProviderRegistrar.REGISTRATION).addAttributes(McpClientSseProviderRegistrar.ATTRIBUTES).build())
                                .addElement(this.factory.namedElement(McpClientStdioProviderRegistrar.REGISTRATION).addAttributes(McpClientStdioProviderRegistrar.ATTRIBUTES).build())
                                .build()
                ).build();
    }
}
