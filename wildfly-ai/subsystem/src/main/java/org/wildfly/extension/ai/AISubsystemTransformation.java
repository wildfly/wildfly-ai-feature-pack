/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;

import static org.wildfly.extension.ai.rag.retriever.WebSearchContentContentRetrieverProviderRegistrar.GOOGLE_SEARCH_ENGINE;
import static org.wildfly.extension.ai.rag.retriever.WebSearchContentContentRetrieverProviderRegistrar.TAVILY_SEARCH_ENGINE;

import java.util.Map;
import java.util.function.Function;
import org.jboss.as.controller.AttributeDefinition;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.MistralAIChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.chat.OpenAIChatLanguageModelProviderRegistrar;
import org.wildfly.extension.ai.rag.retriever.WebSearchContentContentRetrieverProviderRegistrar;

/**
 * Creates the transformation descriptions for the AI subsystem resource.
 */
public class AISubsystemTransformation implements Function<ModelVersion, TransformationDescription> {

    private final AttributeDefinition OLD_API_KEY = new SimpleAttributeDefinitionBuilder("api-key", ModelType.STRING, false)
                                .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
                                .setAllowExpression(true)
                                .setStability(Stability.EXPERIMENTAL)
                                .build();
    @Override
    public TransformationDescription apply(ModelVersion version) {
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createSubsystemInstance();
        if (version.equals(ModelVersion.create(2, 0, 0))) {
            builder.rejectChildResource(PathElement.pathElement("github-chat-model"));
            for (PathElement path : new PathElement[]{GeminiChatLanguageModelProviderRegistrar.PATH,
                OpenAIChatLanguageModelProviderRegistrar.PATH, MistralAIChatLanguageModelProviderRegistrar.PATH}) {
                builder.addChildResource(path).getAttributeBuilder().addRejectCheck(RejectAttributeChecker.DEFINED,
                       OLD_API_KEY );
            }
            RejectAttributeChecker nestedApiKey = new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(
                    Map.of("api-key", RejectAttributeChecker.DEFINED));
            builder.addChildResource(WebSearchContentContentRetrieverProviderRegistrar.PATH).getAttributeBuilder()
                    .addRejectCheck(nestedApiKey, GOOGLE_SEARCH_ENGINE, TAVILY_SEARCH_ENGINE);
        }
        return builder.build();
    }
}
