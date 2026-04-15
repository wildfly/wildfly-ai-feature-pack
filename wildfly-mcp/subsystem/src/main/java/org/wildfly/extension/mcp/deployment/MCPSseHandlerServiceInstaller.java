/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.deployment;

import static org.jboss.as.server.security.VirtualDomainMarkerUtility.virtualDomainName;
import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import org.wildfly.extension.mcp.MCPEndpointConfiguration;
import java.util.List;

import io.undertow.Handlers;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.jboss.as.server.deployment.Attachments;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.dmr.ModelNode;
import org.jboss.metadata.javaee.spec.ParamValueMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.wildfly.elytron.web.undertow.server.ElytronContextAssociationHandler;
import org.wildfly.elytron.web.undertow.server.ElytronHttpExchange;
import org.wildfly.extension.mcp.Capabilities;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.server.MCPServerSentConnectionCallBack;
import org.wildfly.extension.mcp.server.MCPStreamableConnectionCallBack;
import org.wildfly.extension.mcp.server.MessagesHttpHandler;
import org.wildfly.extension.mcp.server.StreamableHttpHandler;
import org.wildfly.extension.undertow.DeploymentDefinition;
import org.wildfly.extension.undertow.Host;
import org.wildfly.extension.undertow.UndertowExtension;
import org.wildfly.httpclient.common.ElytronIdentityHandler;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismConfigurationSelector;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.http.HttpAuthenticationFactory;
import org.wildfly.security.http.HttpServerAuthenticationMechanism;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;
import org.wildfly.security.http.oidc.Oidc;
import org.wildfly.security.http.oidc.OidcClientConfiguration;
import org.wildfly.security.http.oidc.OidcClientConfigurationBuilder;
import org.wildfly.security.http.oidc.OidcClientContext;
import org.wildfly.security.http.oidc.OidcMechanismFactory;
import org.wildfly.subsystem.service.DeploymentServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

public class MCPSseHandlerServiceInstaller implements DeploymentServiceInstaller {

    @Override
    public void install(DeploymentPhaseContext context) {
        DeploymentUnit deploymentUnit = context.getDeploymentUnit();
        ModelNode model = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_RESOURCE_SUPPORT).getDeploymentSubsystemModel(UndertowExtension.SUBSYSTEM_NAME);
        WildFlyMCPRegistry registry = deploymentUnit.getAttachment(MCPAttachments.MCP_REGISTRY_METADATA);
        final org.jboss.modules.Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        final ClassLoader classLoader = module.getClassLoader();
        String serverName = model.get(DeploymentDefinition.SERVER.getName()).asString();
        String hostName = model.get(DeploymentDefinition.VIRTUAL_HOST.getName()).asString();
        String webContext = model.get(DeploymentDefinition.CONTEXT_ROOT.getName()).asString();
        ServiceDependency<Host> host = ServiceDependency.on(Host.SERVICE_DESCRIPTOR, serverName, hostName);
        final HttpServerAuthenticationMechanismFactory httpServerAuthenticationMechanismFactory = getHttpServerAuthenticationMechanismFactory(deploymentUnit);
        final boolean oidcSecured = httpServerAuthenticationMechanismFactory != null;
        final ServiceDependency<SecurityDomain> securityDomain;
        if (oidcSecured) {
            securityDomain = ServiceDependency.on(virtualDomainName(deploymentUnit));
        } else {
            securityDomain = null;
        }
        final MCPEndpointConfiguration configuration = deploymentUnit.getAttachment(MCPAttachments.MCP_ENDPOINT_CONFIGURATION);
        final String messagesEndpoint = "/".equals(webContext) ? webContext + configuration.messagesPath() : webContext + '/' + configuration.messagesPath();
        final ConnectionManager connectionManager = new ConnectionManager();
        final MCPServerSentConnectionCallBack mcpServerSentConnectionCallBack = new MCPServerSentConnectionCallBack(messagesEndpoint, connectionManager);
        final MCPStreamableConnectionCallBack mcpStreamableConnectionCallBack = new MCPStreamableConnectionCallBack(connectionManager);
        final int pageSize = configuration.pageSize();
        final MessagesHttpHandler messagesHttpHandler = new MessagesHttpHandler(connectionManager, registry, classLoader, serverName, deploymentUnit.getName(), pageSize);
        final String ssePath = "/".equals(webContext) ? webContext + configuration.ssePath() : webContext + '/' + configuration.ssePath();
        final String streamableEndpoint = "/".equals(webContext) ? webContext + configuration.streamablePath() : webContext + '/' + configuration.streamablePath();
        final ServerSentEventHandler sseHandler = Handlers.serverSentEvents(mcpServerSentConnectionCallBack);
        final StreamableHttpHandler streamableHttpHandler = new StreamableHttpHandler(connectionManager, registry, classLoader, serverName, deploymentUnit.getName(), Handlers.serverSentEvents(mcpStreamableConnectionCallBack), pageSize);
        Runnable start = new Runnable() {
            @Override
            public void run() {
                connectionManager.start(configuration.timeout(), lookupScheduledExecutorService());
                if (oidcSecured) {
                    HttpAuthenticationFactory httpAuthenticationFactory = HttpAuthenticationFactory.builder()
                            .setFactory(getHttpServerAuthenticationMechanismFactory(deploymentUnit))
                            .setSecurityDomain(securityDomain.get())
                            .setMechanismConfigurationSelector(MechanismConfigurationSelector.constantSelector(MechanismConfiguration.EMPTY))
                            .build();
                    host.get().registerHandler(ssePath, secureHandler(sseHandler, httpAuthenticationFactory));
                    host.get().registerHandler(messagesEndpoint, secureHandler(messagesHttpHandler, httpAuthenticationFactory));
                    host.get().registerHandler(streamableEndpoint, secureHandler(streamableHttpHandler, httpAuthenticationFactory));
                } else {
                    host.get().registerHandler(ssePath, sseHandler);
                    host.get().registerHandler(messagesEndpoint, messagesHttpHandler);
                    host.get().registerHandler(streamableEndpoint, streamableHttpHandler);
                }
                ROOT_LOGGER.endpointRegistered(ssePath, host.get().getName());
                ROOT_LOGGER.endpointRegistered(streamableEndpoint, host.get().getName());
            }
        };
        Runnable stop = new Runnable() {
            @Override
            public void run() {
                connectionManager.stop();
                host.get().unregisterHandler(ssePath);
                host.get().unregisterHandler(messagesEndpoint);
                ROOT_LOGGER.endpointUnregistered(ssePath, host.get().getName());
                ROOT_LOGGER.endpointUnregistered(streamableEndpoint, host.get().getName());
            }
        };
        if (oidcSecured) {
            ServiceInstaller.builder(start, stop)
                    .requires(List.of(host, ServiceDependency.on(Capabilities.MCP_SERVER_PROVIDER_DESCRIPTOR), securityDomain))
                    .asActive()
                    .build()
                    .install(context);
        } else {
            ServiceInstaller.builder(start, stop)
                    .requires(List.of(host, ServiceDependency.on(Capabilities.MCP_SERVER_PROVIDER_DESCRIPTOR)))
                    .asActive()
                    .build()
                    .install(context);
        }
    }

    private HttpServerAuthenticationMechanismFactory getHttpServerAuthenticationMechanismFactory(DeploymentUnit deploymentUnit) {
        WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if (warMetaData == null) {
            return null;
        }
        String json = getJsonConfiguration(warMetaData);
        if (json != null) {
            OidcClientConfiguration oidcClientConfiguration = OidcClientConfigurationBuilder.build(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            oidcClientConfiguration.setBearerOnly(true);
            OidcClientContext oidcClientContext = new OidcClientContext(oidcClientConfiguration);
            return new OidcMechanismFactory(oidcClientContext);
        }
        return null;
    }

    private String getJsonConfiguration(WarMetaData warMetaData) {
        JBossWebMetaData webMetaData = warMetaData.getMergedJBossWebMetaData();
        for (ParamValueMetaData param : webMetaData.getContextParams()) {
            if (Oidc.JSON_CONFIG_CONTEXT_PARAM.equals(param.getParamName())) {
                return param.getParamValue();
            }
        }
        return null;
    }

    private HttpHandler secureHandler(HttpHandler handler, HttpAuthenticationFactory httpAuthenticationFactory) {
        HttpHandler domainHandler = new AuthenticationCallHandler(handler);
        domainHandler = new AuthenticationConstraintHandler(domainHandler);
        Supplier<List<HttpServerAuthenticationMechanism>> mechanismSupplier = ()
                -> httpAuthenticationFactory.getMechanismNames().stream()
                        .map(s -> {
                            try {
                                return httpAuthenticationFactory.createMechanism(s);
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .collect(Collectors.toList());
        domainHandler = ElytronContextAssociationHandler.builder()
                .setNext(domainHandler)
                .setMechanismSupplier(mechanismSupplier)
                .setHttpExchangeSupplier(h -> new ElytronHttpExchange(h) {

            @Override
            public void authenticationComplete(SecurityIdentity securityIdentity, String mechanismName) {
                super.authenticationComplete(securityIdentity, mechanismName);
                h.putAttachment(ElytronIdentityHandler.IDENTITY_KEY, securityIdentity);
            }

        })
                .build();
        return domainHandler;
    }

    static ScheduledExecutorService lookupScheduledExecutorService() {
        InitialContext context = null;
        try {
            context = new InitialContext();
            return (ScheduledExecutorService) context.lookup("java:comp/DefaultManagedScheduledExecutorService");
        } catch (NamingException ex) {
            ROOT_LOGGER.warn("Managed scheduled executor service not available, using default scheduled executor service");
            return Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mcp-connection-cleanup");
                t.setDaemon(true);
                return t;
            });
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ex) {
                    ROOT_LOGGER.debug("Error closing initial context", ex);
                }
            }
        }
    }
}
