/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.ai.injection.memory.WildFlyChatMemoryProviderConfig;
import org.wildfly.extension.ai.mcp.client.WildFlyMcpClient;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.extension.ai.injection.retriever.WildFlyContentRetrieverConfig;

/**
 * WildFly capability definitions for the AI subsystem.
 *
 * <p>This interface defines all runtime capabilities provided and required by the
 * AI subsystem. Capabilities enable loose coupling between subsystems while ensuring
 * proper service dependencies and availability.</p>
 *
 * <h3>AI Service Capabilities</h3>
 * <p>Each AI service type provides a capability with the following characteristics:</p>
 * <ul>
 *   <li><b>Dynamic naming</b> - Capability names include the bean identifier
 *       (e.g., "org.wildfly.ai.chatmodel.ollama")</li>
 *   <li><b>Multiple registrations</b> - Allows multiple instances of the same service type</li>
 *   <li><b>Service descriptors</b> - Define service types for dependency injection</li>
 * </ul>
 *
 * <h3>Provided Capabilities</h3>
 * <table border="1">
 *   <tr>
 *     <th>Capability Name</th>
 *     <th>Service Type</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>org.wildfly.ai.chatmodel.{name}</td>
 *     <td>{@link WildFlyChatModelConfig}</td>
 *     <td>Chat model configurations (standard and streaming)</td>
 *   </tr>
 *   <tr>
 *     <td>org.wildfly.ai.embedding.model.{name}</td>
 *     <td>{@link EmbeddingModel}</td>
 *     <td>Text embedding models</td>
 *   </tr>
 *   <tr>
 *     <td>org.wildfly.ai.embedding.store.{name}</td>
 *     <td>{@link EmbeddingStore}</td>
 *     <td>Vector database backends</td>
 *   </tr>
 *   <tr>
 *     <td>org.wildfly.ai.rag.retriever.{name}</td>
 *     <td>{@link WildFlyContentRetrieverConfig}</td>
 *     <td>Content retrievers for RAG</td>
 *   </tr>
 *   <tr>
 *     <td>org.wildfly.ai.tool-provider.{name}</td>
 *     <td>{@link ToolProvider}</td>
 *     <td>MCP and function calling tools</td>
 *   </tr>
 *   <tr>
 *     <td>org.wildfly.ai.chatmemory.{name}</td>
 *     <td>{@link WildFlyChatMemoryProviderConfig}</td>
 *     <td>Chat memory providers</td>
 *   </tr>
 *   <tr>
 *     <td>org.wildfly.ai.mcp.client.{name}</td>
 *     <td>{@link WildFlyMcpClient}</td>
 *     <td>MCP client connections</td>
 *   </tr>
 * </table>
 *
 * <h3>Required Capabilities</h3>
 * <p>The AI subsystem requires these capabilities from other subsystems:</p>
 * <ul>
 *   <li><b>org.wildfly.ee.concurrent.executor</b> - Managed executors for async operations</li>
 *   <li><b>org.wildfly.extension.opentelemetry</b> - Optional observability integration</li>
 *   <li><b>org.wildfly.network.outbound-socket-binding</b> - Network configuration for remote services</li>
 * </ul>
 *
 * @see RuntimeCapability
 * @see UnaryServiceDescriptor
 */
public interface Capabilities {
    UnaryServiceDescriptor<WildFlyChatMemoryProviderConfig> CHAT_MEMORY_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.chatmemory", WildFlyChatMemoryProviderConfig.class);
    RuntimeCapability<Void> CHAT_MEMORY_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(CHAT_MEMORY_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    UnaryServiceDescriptor<WildFlyChatModelConfig> CHAT_MODEL_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.chatmodel", WildFlyChatModelConfig.class);
    RuntimeCapability<Void> CHAT_MODEL_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(CHAT_MODEL_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    UnaryServiceDescriptor<EmbeddingModel> EMBEDDING_MODEL_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.embedding.model", EmbeddingModel.class);
    RuntimeCapability<Void> EMBEDDING_MODEL_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(EMBEDDING_MODEL_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    UnaryServiceDescriptor<EmbeddingStore> EMBEDDING_STORE_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.embedding.store", EmbeddingStore.class);
    RuntimeCapability<Void> EMBEDDING_STORE_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(EMBEDDING_STORE_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    UnaryServiceDescriptor<ToolProvider> TOOL_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.tool-provider", ToolProvider.class);
    RuntimeCapability<Void> TOOL_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(TOOL_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    UnaryServiceDescriptor<WildFlyContentRetrieverConfig> CONTENT_RETRIEVER_PROVIDER_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.rag.retriever", WildFlyContentRetrieverConfig.class);
    RuntimeCapability<Void> CONTENT_RETRIEVER_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(CONTENT_RETRIEVER_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    UnaryServiceDescriptor<WildFlyMcpClient> MCP_CLIENT_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.ai.mcp.client", WildFlyMcpClient.class);
    RuntimeCapability<Void> MCP_CLIENT_CAPABILITY = RuntimeCapability.Builder.of(MCP_CLIENT_DESCRIPTOR).setAllowMultipleRegistrations(true).build();

    String MANAGED_EXECUTOR_CAPABILITY_NAME = "org.wildfly.ee.concurrent.executor";
    String OPENTELEMETRY_CAPABILITY_NAME = "org.wildfly.extension.opentelemetry";
    String OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.outbound-socket-binding";
}
