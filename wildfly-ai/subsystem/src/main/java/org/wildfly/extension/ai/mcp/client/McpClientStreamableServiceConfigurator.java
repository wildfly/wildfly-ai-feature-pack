/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.mcp.client;

import static org.wildfly.extension.ai.AIAttributeDefinitions.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_REQUESTS;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_RESPONSES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.SSL_ENABLED;
import static org.wildfly.extension.ai.Capabilities.MCP_CLIENT_CAPABILITY;
import static org.wildfly.extension.ai.mcp.client.McpClientSseProviderRegistrar.SSE_PATH;
import static org.wildfly.extension.ai.mcp.client.McpClientSseProviderRegistrar.SSE_SOCKET_BINDING;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.Installer;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

public class McpClientStreamableServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String name = context.getCurrentAddressValue();
        String scheme = SSL_ENABLED.resolveModelAttribute(context, model).asBoolean() ? "https" : "http";
        String path = SSE_PATH.resolveModelAttribute(context, model).asString();
        Long connectTimeOut = CONNECT_TIMEOUT.resolveModelAttribute(context, model).asLong();
        Boolean logRequests = LOG_REQUESTS.resolveModelAttribute(context, model).asBooleanOrNull();
        Boolean logResponses = LOG_RESPONSES.resolveModelAttribute(context, model).asBooleanOrNull();
        String socketBindingName = SSE_SOCKET_BINDING.resolveModelAttribute(context, model).asString();
        ServiceDependency<OutboundSocketBinding> outboundSocketBinding = ServiceDependency.on(OutboundSocketBinding.SERVICE_DESCRIPTOR, socketBindingName);
        ServiceDependency<ExecutorService> executorService = ServiceDependency.on("org.wildfly.ee.concurrent.executor", ExecutorService.class, "default");
        Supplier<WildFlyMcpClient> factory = new Supplier<>() {
            @Override
            public WildFlyMcpClient get() {
                try {
                    URI url = new URI(scheme, null, outboundSocketBinding.get().getUnresolvedDestinationAddress(), outboundSocketBinding.get().getDestinationPort(), path, null, null);
                    McpTransport transport = new StreamableHttpMcpTransport.Builder()
                            .logRequests(logRequests)
                            .logResponses(logResponses)
                            .timeout(connectTimeOut > 0L ? Duration.ofMillis(connectTimeOut) : null)
                            .url(url.toString())
                            .executor(executorService.get())
                            .build();
                    return new WildFlyMcpClient(new DefaultMcpClient.Builder()
                            .transport(transport)
                            .clientName(name)
                            .build());
                } catch (URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
        return CapabilityServiceInstaller.builder(MCP_CLIENT_CAPABILITY, factory)
                .requires(outboundSocketBinding)
                .requires(executorService)
                .blocking()
                .startWhen(Installer.StartWhen.INSTALLED)
                .build();
    }

}
