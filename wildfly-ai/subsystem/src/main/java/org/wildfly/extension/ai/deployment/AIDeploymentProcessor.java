/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.deployment;

import static org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME;
import static org.wildfly.extension.ai.AIAttributeDefinitions.STREAMING;
import static org.wildfly.extension.ai.AILogger.ROOT_LOGGER;
import static org.wildfly.extension.ai.Capabilities.OPENTELEMETRY_CAPABILITY_NAME;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.inject.spi.Extension;
import java.util.List;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.weld.WeldCapability;
import org.wildfly.extension.ai.AIModelDeploymentRegistrar;
import org.wildfly.extension.ai.injection.WildFlyBeanRegistry;
import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.ai.injection.memory.WildFlyChatMemoryProviderConfig;
import org.wildfly.extension.ai.injection.retriever.WildFlyContentRetrieverConfig;

/**
 * Deployment processor for AI service registration and CDI integration.
 *
 * <p>This processor runs during the {@code POST_MODULE} phase, after dependencies
 * have been resolved by {@link AIDependencyProcessor}. It performs the actual
 * registration of AI services for CDI injection in the deployment.</p>
 *
 * <h3>Processing Steps</h3>
 * <ol>
 *   <li><b>Validation</b> - Verifies CDI (Weld) support is available</li>
 *   <li><b>Service Retrieval</b> - Retrieves AI service instances from deployment attachments</li>
 *   <li><b>Bean Registration</b> - Registers services with {@link WildFlyBeanRegistry} for CDI</li>
 *   <li><b>Runtime Metadata</b> - Updates deployment management model with service info</li>
 *   <li><b>CDI Extension Registration</b> - Registers LangChain4j CDI extensions</li>
 * </ol>
 *
 * <h3>Supported Service Types</h3>
 * <ul>
 *   <li><b>Chat Models</b> - {@link WildFlyChatModelConfig} (standard and streaming)</li>
 *   <li><b>Embedding Models</b> - {@link EmbeddingModel}</li>
 *   <li><b>Embedding Stores</b> - {@link EmbeddingStore}</li>
 *   <li><b>Content Retrievers</b> - {@link WildFlyContentRetrieverConfig}</li>
 *   <li><b>Tool Providers</b> - {@link ToolProvider} (MCP/function calling)</li>
 *   <li><b>Chat Memory</b> - {@link WildFlyChatMemoryProviderConfig}</li>
 * </ul>
 *
 * <h3>OpenTelemetry Integration</h3>
 * <p>If the OpenTelemetry capability is available, the processor logs this information
 * but delegates actual instrumentation to the LangChain4j CDI extensions.</p>
 *
 * <h3>Management Model Updates</h3>
 * <p>For each registered chat model, the processor updates the deployment's management
 * model with runtime attributes like streaming status, making this information available
 * through the WildFly CLI and management APIs.</p>
 *
 * @see AIDependencyProcessor
 * @see WildFlyBeanRegistry
 * @see AIAttachments
 */
public class AIDeploymentProcessor implements DeploymentUnitProcessor {

    /**
     * Registers AI services for CDI injection in the deployment.
     *
     * <p>This method retrieves AI service instances that were attached by
     * {@link AIDependencyProcessor}, registers them with {@link WildFlyBeanRegistry}
     * for CDI availability, and updates the deployment's management model.</p>
     *
     * <p>The method processes services in this order:</p>
     * <ol>
     *   <li>Chat models (with management model updates)</li>
     *   <li>Embedding models</li>
     *   <li>Embedding stores</li>
     *   <li>Content retrievers</li>
     *   <li>Tool providers</li>
     *   <li>Chat memory providers</li>
     * </ol>
     *
     * <p>After all services are registered, LangChain4j CDI portable extensions
     * are registered with the Weld capability to enable AI service discovery.</p>
     *
     * @param deploymentPhaseContext the deployment phase context
     * @throws DeploymentUnitProcessingException if CDI support is unavailable
     */
    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        try {
            final CapabilityServiceSupport support = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
            final WeldCapability weldCapability = support.getCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class);
            if (weldCapability != null && !weldCapability.isPartOfWeldDeployment(deploymentUnit)) {
                ROOT_LOGGER.cdiRequired();
            }
            List<WildFlyChatModelConfig> requiredChatModels = deploymentUnit.getAttachmentList(AIAttachments.CHAT_MODELS);
            if (!support.hasCapability(OPENTELEMETRY_CAPABILITY_NAME)) {
                ROOT_LOGGER.info("No opentelemetry support available");
            } else {
                ROOT_LOGGER.debug("OpenTelemetry is active for AI");
            }
            List<String> chatLanguageModelNames = deploymentUnit.getAttachmentList(AIAttachments.CHAT_MODEL_KEYS);
            List<EmbeddingModel> requiredEmbeddingModels = deploymentUnit.getAttachmentList(AIAttachments.EMBEDDING_MODELS);
            List<String> requiredEmbeddingModelNames = deploymentUnit.getAttachmentList(AIAttachments.EMBEDDING_MODEL_KEYS);
            List<EmbeddingStore<?>> requiredEmbeddingStores = deploymentUnit.getAttachmentList(AIAttachments.EMBEDDING_STORES);
            List<String> requiredEmbeddingStoreNames = deploymentUnit.getAttachmentList(AIAttachments.EMBEDDING_STORE_KEYS);
            List<WildFlyContentRetrieverConfig> requiredContentRetrievers = deploymentUnit.getAttachmentList(AIAttachments.CONTENT_RETRIEVERS);
            List<String> requiredContentRetrieverNames = deploymentUnit.getAttachmentList(AIAttachments.CONTENT_RETRIEVER_KEYS);
            List<ToolProvider> requiredToolProviders = deploymentUnit.getAttachmentList(AIAttachments.TOOL_PROVIDERS);
            List<String> requiredToolProviderNames = deploymentUnit.getAttachmentList(AIAttachments.TOOL_PROVIDER_KEYS);
            List<WildFlyChatMemoryProviderConfig> requiredChatMemoryProviders = deploymentUnit.getAttachmentList(AIAttachments.CHAT_MEMORY_PROVIDERS);
            List<String> requiredChatMemoryProviderNames = deploymentUnit.getAttachmentList(AIAttachments.CHAT_MEMORY_PROVIDER_KEYS);

            final DeploymentResourceSupport deploymentResourceSupport = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.DEPLOYMENT_RESOURCE_SUPPORT);

            if (!requiredChatModels.isEmpty() || !requiredEmbeddingModels.isEmpty() || !requiredEmbeddingStores.isEmpty() || !requiredContentRetrievers.isEmpty()
                    || !requiredToolProviders.isEmpty()|| !requiredChatMemoryProviders.isEmpty()) {
                if (!requiredChatModels.isEmpty()) {
                    for (int i = 0; i < requiredChatModels.size(); i++) {
                        WildFlyBeanRegistry.registerChatModel(chatLanguageModelNames.get(i), requiredChatModels.get(i));
                        try {
                            deploymentResourceSupport.getDeploymentSubModel("ai", PathElement.pathElement(AIModelDeploymentRegistrar.NAME, chatLanguageModelNames.get(i)))
                                    .get(STREAMING.getName()).set(requiredChatModels.get(i).isStreaming());
                        } catch (Exception e) {
                            ROOT_LOGGER.error(e);
                        }
                    }
                }
                if (!requiredEmbeddingModels.isEmpty()) {
                    for (int i = 0; i < requiredEmbeddingModels.size(); i++) {
                        WildFlyBeanRegistry.registerEmbeddingModel(requiredEmbeddingModelNames.get(i), requiredEmbeddingModels.get(i));
                    }
                }
                if (!requiredEmbeddingStores.isEmpty()) {
                    for (int i = 0; i < requiredEmbeddingStores.size(); i++) {
                        WildFlyBeanRegistry.registerEmbeddingStore(requiredEmbeddingStoreNames.get(i), requiredEmbeddingStores.get(i));
                    }
                }
                if (!requiredContentRetrievers.isEmpty()) {
                    for (int i = 0; i < requiredContentRetrievers.size(); i++) {
                        WildFlyBeanRegistry.registerContentRetriever(requiredContentRetrieverNames.get(i), requiredContentRetrievers.get(i));
                    }
                }
                if (!requiredToolProviders.isEmpty()) {
                    for (int i = 0; i < requiredToolProviders.size(); i++) {
                        WildFlyBeanRegistry.registerToolProvider(requiredToolProviderNames.get(i), requiredToolProviders.get(i));
                    }
                }
                if (!requiredChatMemoryProviders.isEmpty()) {
                    for (int i = 0; i < requiredChatMemoryProviders.size(); i++) {
                        WildFlyBeanRegistry.registerChatMemoryProvider(requiredChatMemoryProviderNames.get(i), requiredChatMemoryProviders.get(i));
                    }
                }
                for (Extension extension : WildFlyBeanRegistry.getCDIExtensions()) {
                    support.getOptionalCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class).get()
                            .registerExtensionInstance(extension, deploymentUnit);
                }
            }
        } catch (CapabilityServiceSupport.NoSuchCapabilityException e) {
            ROOT_LOGGER.cdiRequired();
        }
    }

    /**
     * Performs cleanup when a deployment is removed.
     *
     * <p>Currently no cleanup is required as AI service beans are managed by the
     * CDI container and will be automatically destroyed during undeployment.</p>
     *
     * @param context the deployment unit being undeployed
     */
    @Override
    public void undeploy(DeploymentUnit context) {
    }
}
