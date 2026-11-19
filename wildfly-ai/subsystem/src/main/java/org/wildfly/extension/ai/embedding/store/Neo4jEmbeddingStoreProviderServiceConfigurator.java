/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.embedding.store;

import static org.wildfly.extension.ai.AIAttributeDefinitions.BOLT_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CREDENTIAL_REFERENCE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.USERNAME;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_STORE_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.DATABASE_NAME;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.DIMENSION;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.EMBEDDING_PROPERTY;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.ID_PROPERTY;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.INDEX_NAME;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.LABEL;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.METADATA_PREFIX;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.RETRIEVAL_QUERY;
import static org.wildfly.extension.ai.embedding.store.Neo4jEmbeddingStoreProviderRegistrar.TEXT_PROPERTY;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.ai.CredentialReferenceUtil;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

public class Neo4jEmbeddingStoreProviderServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String boltUrl = BOLT_URL.resolveModelAttribute(context, model).asString();
        String databaseName = DATABASE_NAME.resolveModelAttribute(context, model).asStringOrNull();
        Integer dimension = DIMENSION.resolveModelAttribute(context, model).asIntOrNull();
        String embeddingProperty = EMBEDDING_PROPERTY.resolveModelAttribute(context, model).asStringOrNull();
        String idProperty = ID_PROPERTY.resolveModelAttribute(context, model).asStringOrNull();
        String indexName = INDEX_NAME.resolveModelAttribute(context, model).asStringOrNull();
        String label = LABEL.resolveModelAttribute(context, model).asStringOrNull();
        String metadataPrefix = METADATA_PREFIX.resolveModelAttribute(context, model).asStringOrNull();
        String retrievalQuery = RETRIEVAL_QUERY.resolveModelAttribute(context, model).asStringOrNull();
        String textProperty = TEXT_PROPERTY.resolveModelAttribute(context, model).asStringOrNull();
        String userName = USERNAME.resolveModelAttribute(context, model).asString();
        ServiceDependency<CredentialSource> credentialRef = ServiceDependency.from(CredentialReference.getCredentialSourceDependency(context, CREDENTIAL_REFERENCE, model));
        Supplier<EmbeddingStore<TextSegment>> factory = new Supplier<>() {
            @Override
            public EmbeddingStore<TextSegment> get() {
                String password = CredentialReferenceUtil.getPassword(credentialRef.get());
                return Neo4jEmbeddingStore.builder()
                        .withBasicAuth(boltUrl, userName, password)
                        .dimension(dimension)
                        .databaseName(databaseName)
                        .embeddingProperty(embeddingProperty)
                        .idProperty(idProperty)
                        .indexName(indexName)
                        .label(label)
                        .metadataPrefix(metadataPrefix)
                        .retrievalQuery(retrievalQuery)
                        .textProperty(textProperty)
                        .build();
            }
        };

        return CapabilityServiceInstaller.builder(EMBEDDING_STORE_PROVIDER_CAPABILITY, factory).requires(credentialRef).blocking().asActive().build();
    }

}
