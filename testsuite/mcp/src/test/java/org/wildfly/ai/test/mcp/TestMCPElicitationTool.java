/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.mcp_java.server.tools.Tool;
import org.mcp_java.server.tools.ToolArg;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationRequest;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationResponse;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSender;
import org.wildfly.extension.mcp.injection.elicitation.StringSchema;
import org.wildfly.extension.mcp.injection.elicitation.UrlElicitationRequest;

public class TestMCPElicitationTool {

    private static final ConcurrentHashMap<String, CompletableFuture<Void>> pendingCallbacks = new ConcurrentHashMap<>();

    /**
     * Called by {@link TestThirdPartyApplication.TestOAuthCallbackEndpoint} when the simulated OAuth callback arrives,
     * unblocking the tool thread that is waiting for the out-of-band interaction to complete.
     */
    static boolean completeOutOfBandInteraction(String elicitationId) {
        CompletableFuture<Void> future = pendingCallbacks.remove(elicitationId);
        if (future != null) {
            future.complete(null);
            return true;
        }
        return false;
    }

    @Tool(name = "greet-with-name", description = "Asks the user for their name via elicitation and greets them")
    String greetWithName(ElicitationSender elicitationSender) throws Exception {
        if (!elicitationSender.isSupported()) {
            return "Elicitation not supported by client";
        }
        ElicitationRequest request = ElicitationRequest.builder("What is your name?")
                .addSchemaProperty("name", new StringSchema(true, "Your Name", "Enter your first name"))
                .timeout(30_000)
                .build();
        ElicitationResponse response = elicitationSender.send(request);
        if (response.isAccepted()) {
            return "Hello, " + response.getString("name") + "!";
        } else if (response.isDeclined()) {
            return "You declined to provide your name.";
        } else {
            return "Request was cancelled.";
        }
    }

    @Tool(name = "add-with-confirmation", description = "Adds two numbers after user confirms the operation")
    String addWithConfirmation(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b,
            ElicitationSender elicitationSender) throws Exception {
        if (!elicitationSender.isSupported()) {
            return String.valueOf(a + b);
        }
        ElicitationRequest request = ElicitationRequest.builder("Confirm: add " + a + " + " + b + "?")
                .addSchemaProperty("confirm", new org.wildfly.extension.mcp.injection.elicitation.BooleanSchema(true))
                .timeout(30_000)
                .build();
        ElicitationResponse response = elicitationSender.send(request);
        if (response.isAccepted() && Boolean.TRUE.equals(response.getBoolean("confirm"))) {
            return "Result: " + (a + b);
        }
        return "Operation not confirmed.";
    }

    @Tool(name = "authenticate-via-url", description = "Directs user to authenticate via an external URL")
    String authenticateViaUrl(ElicitationSender elicitationSender) throws Exception {
        if (!elicitationSender.isUrlSupported()) {
            return "URL elicitation not supported by client";
        }

        String elicitationId = "auth-integration-test" + UUID.randomUUID();

        UrlElicitationRequest request = UrlElicitationRequest.builder("Please authenticate with your identity provider")
                .url("/my-app/oauth/authorize")
                .elicitationId(elicitationId)
                .timeout(30_000)
                .build();

        ElicitationResponse response = elicitationSender.sendUrl(request);
        if (!response.isAccepted()) {
            return response.isDeclined() ? "Authentication declined" : "Authentication cancelled";
        }

        // The client accepted — the user consented to open the URL.
        // Now block until the out-of-band interaction completes (i.e. the
        // OAuth callback endpoint is hit by the user's browser).
        CompletableFuture<Void> callbackFuture = new CompletableFuture<>();
        pendingCallbacks.put(elicitationId, callbackFuture);
        callbackFuture.get(30, TimeUnit.SECONDS);

        // The out-of-band interaction is complete — notify the client.
        elicitationSender.notifyElicitationComplete(elicitationId);
        return "Authentication successful";
    }
}
