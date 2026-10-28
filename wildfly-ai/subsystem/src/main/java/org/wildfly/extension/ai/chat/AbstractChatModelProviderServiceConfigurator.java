/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.chat;

import static org.wildfly.extension.ai.Capabilities.CHAT_MODEL_PROVIDER_CAPABILITY;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.wildfly.security.credential.source.CredentialSource;
import org.jboss.as.ee.concurrent.adapter.ManagedExecutorServiceAdapter;
import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.opentelemetry.api.WildFlyOpenTelemetryConfig;
import org.wildfly.service.Installer;
import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

public abstract class AbstractChatModelProviderServiceConfigurator implements ResourceServiceConfigurator {

    private final ValueRegistry<String, WildFlyChatModelConfig> registry;

    AbstractChatModelProviderServiceConfigurator(ValueRegistry<String, WildFlyChatModelConfig> registry) {
        this.registry = registry;
    }

    ResourceServiceInstaller installService(final String name, Supplier<WildFlyChatModelConfig> factory) {
        return installService(name, factory, false);
    }

    ResourceServiceInstaller installService(final String name, Supplier<WildFlyChatModelConfig> factory,
            ServiceDependency<CredentialSource> credentialReference) {
        return installService(name, factory, false, null, credentialReference);
    }

    ResourceServiceInstaller installService(final String name, Supplier<WildFlyChatModelConfig> factory, boolean isObservable) {
        return installService(name, factory, isObservable, null);
    }

    ResourceServiceInstaller installService(final String name, Supplier<WildFlyChatModelConfig> factory, boolean isObservable,
            ServiceDependency<ManagedExecutorServiceAdapter> executorAdapter) {
        return installService(name, factory, isObservable, executorAdapter, null);
    }

    ResourceServiceInstaller installService(final String name, Supplier<WildFlyChatModelConfig> factory, boolean isObservable,
            ServiceDependency<ManagedExecutorServiceAdapter> executorAdapter, ServiceDependency<CredentialSource> credentialReference) {
        Consumer<WildFlyChatModelConfig> captor = registry.add(name);
        CapabilityServiceInstaller.BlockingBuilder builder = CapabilityServiceInstaller.BlockingBuilder.of(CHAT_MODEL_PROVIDER_CAPABILITY, factory)
                .withCaptor(captor)
                .startWhen(Installer.StartWhen.INSTALLED);
        final ServiceDependency<WildFlyOpenTelemetryConfig> openTelemetryConfig;
        if (isObservable) {
            openTelemetryConfig = ServiceDependency.on(WildFlyOpenTelemetryConfig.SERVICE_DESCRIPTOR);
            builder.requires(openTelemetryConfig);
        }
        if (executorAdapter != null) {
            builder.requires(executorAdapter);
        }
        if (credentialReference != null) {
            builder.requires(credentialReference);
        }
        ResourceServiceInstaller installer = (ResourceServiceInstaller) builder.build();
        Consumer<OperationContext> remover = ctx -> registry.remove(ctx.getCurrentAddressValue());
        return new ResourceServiceInstaller() {
            @Override
            public Consumer<OperationContext> install(OperationContext context) {
                return installer.install(context).andThen(remover);
            }
        };
    }
}
