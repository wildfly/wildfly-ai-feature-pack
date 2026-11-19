/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.rag.retriever;

import static org.wildfly.extension.ai.AIAttributeDefinitions.BOLT_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CREDENTIAL_REFERENCE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.USERNAME;
import static org.wildfly.extension.ai.Capabilities.CHAT_MODEL_PROVIDER_DESCRIPTOR;
import static org.wildfly.extension.ai.Capabilities.CONTENT_RETRIEVER_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.rag.retriever.Neo4JContentRetrieverProviderRegistrar.CHAT_LANGUAGE_MODEL;
import static org.wildfly.extension.ai.rag.retriever.Neo4JContentRetrieverProviderRegistrar.PROMPT_TEMPLATE;

import dev.langchain4j.model.input.PromptTemplate;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.ai.CredentialReferenceUtil;
import org.wildfly.extension.ai.injection.WildFlyBeanRegistry;
import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.ai.injection.retriever.Neo4JContentRetrieverConfig;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;
import org.wildfly.extension.ai.injection.retriever.WildFlyContentRetrieverConfig;
import org.wildfly.service.Installer;

public class Neo4JContentRetrieverProviderServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String boltUrl = BOLT_URL.resolveModelAttribute(context, model).asString();
        String userName = USERNAME.resolveModelAttribute(context, model).asStringOrNull();
        String template = PROMPT_TEMPLATE.resolveModelAttribute(context, model).asStringOrNull();
        String chatLanguageModelName = CHAT_LANGUAGE_MODEL.resolveModelAttribute(context, model).asString();
        PromptTemplate promptTemplate = template == null ? null : PromptTemplate.from(template);
        ServiceDependency<CredentialSource> credentialRef = ServiceDependency.from(CredentialReference.getCredentialSourceDependency(context, CREDENTIAL_REFERENCE, model));
        ServiceDependency<WildFlyChatModelConfig> chatLanguageModel = ServiceDependency.on(CHAT_MODEL_PROVIDER_DESCRIPTOR, chatLanguageModelName);
        Supplier<WildFlyContentRetrieverConfig> factory = new Supplier<>() {
            @Override
            public WildFlyContentRetrieverConfig get() {
                String password = CredentialReferenceUtil.getPassword(credentialRef.get());
                WildFlyBeanRegistry.registerChatModel(chatLanguageModelName, chatLanguageModel.get());
                return new Neo4JContentRetrieverConfig()
                        .boltUrl(boltUrl)
                        .chatLanguageModel(chatLanguageModelName)
                        .password(password)
                        .promptTemplate(promptTemplate)
                        .userName(userName);
            }
        };
        return CapabilityServiceInstaller.builder(CONTENT_RETRIEVER_PROVIDER_CAPABILITY, factory)
                .requires(credentialRef)
                .requires(chatLanguageModel)
                .blocking()
                .startWhen(Installer.StartWhen.INSTALLED)
                .build();
    }

}
