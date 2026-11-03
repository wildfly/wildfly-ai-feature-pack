/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import static org.wildfly.extension.mcp.Capabilities.MCP_SERVER_PROVIDER_CAPABILITY;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.MESSAGES_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.PAGE_SIZE;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.SSE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.CREDENTIAL_REFERENCE;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.ALLOWED_ORIGINS;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.STREAMABLE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.TIMEOUT;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.REQUEST_TIMEOUT;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.CACHE_TTL;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.CACHE_SCOPE;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.service.Installer;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

public class MCPEndpointConfigurationProviderServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        final String ssePath = SSE_PATH.resolveModelAttribute(context, model).asString();
        final String messagesPath = MESSAGES_PATH.resolveModelAttribute(context, model).asString();
        final String streamablePath = STREAMABLE_PATH.resolveModelAttribute(context, model).asString();
        final int pageSize = PAGE_SIZE.resolveModelAttribute(context, model).asInt(0);
        final long timeout = TIMEOUT.resolve(context, model).getSeconds();
        final ServiceDependency<CredentialSource> requestStateSecretCredential = ServiceDependency.from(CredentialReference.getCredentialSourceDependency(context, CREDENTIAL_REFERENCE, model));
        final Duration requestTimeoutSeconds = REQUEST_TIMEOUT.resolve(context, model);
        List<String> originsList = ALLOWED_ORIGINS.unwrap(context, model);
        final Set<String> allowedOrigins = originsList.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(originsList));
        final Duration cacheTtlMs = CACHE_TTL.resolve(context, model);
        final CacheScope cacheScope = CacheScope.fromString(CACHE_SCOPE.resolveModelAttribute(context, model).asString());
        Supplier<MCPEndpointConfiguration> factory = new Supplier<>() {
            @Override
            public MCPEndpointConfiguration get() {
                byte[] requestStateSecret = resolveRequestStateSecret(requestStateSecretCredential);
                return new MCPEndpointConfiguration(ssePath, messagesPath, streamablePath, pageSize, timeout,
                        requestTimeoutSeconds, requestStateSecret, allowedOrigins, cacheTtlMs, cacheScope);
            }
        };
        return CapabilityServiceInstaller.BlockingBuilder.of(MCP_SERVER_PROVIDER_CAPABILITY, factory)
                .requires(requestStateSecretCredential)
                .startWhen(Installer.StartWhen.AVAILABLE)
                .build();
    }

    /**
     * Resolves the HMAC key used to sign requestState tokens from the {@code credential-reference}
     * attribute's {@link SecretKeyCredential}. Returns {@code null} when it isn't configured, in which case
     * requestState signing/verification is disabled.
     */
    private static byte[] resolveRequestStateSecret(ServiceDependency<CredentialSource> credentialDependency) {
        CredentialSource credentialSource = credentialDependency.get();
        if (credentialSource == null) {
            return null;
        }
        SecretKeyCredential credential;
        try {
            credential = credentialSource.getCredential(SecretKeyCredential.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (credential == null) {
            throw MCPLogger.ROOT_LOGGER.credentialReferenceMissingSecretKey();
        }
        return credential.getSecretKey().getEncoded();
    }

}
