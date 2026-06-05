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
import org.wildfly.extension.mcp.injection.elicitation.Elicitation;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSender;
import org.wildfly.extension.mcp.injection.elicitation.StringSchema;

public class TestMCPElicitationTool {

    private static final ConcurrentHashMap<String, CompletableFuture<Void>> pendingCallbacks = new ConcurrentHashMap<>();

    /**
     * Called by {@link TestThirdPartyApplication.TestCallbackEndpoint} when the simulated OAuth callback arrives,
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
        if (!elicitationSender.isFormSupported()) {
            return "Elicitation Form Mode not supported by client";
        }
        Elicitation elicitation = Elicitation.builder("What is your name?")
                .addSchemaProperty("name", new StringSchema(true, "Your Name", "Enter your first name"))
                .timeout(30_000)
                .build();
        Elicitation.Response response = elicitationSender.send(elicitation);
        return switch (response.action()) {
            case ACCEPT -> "Hello, " + response.getString("name") + "!";
            case CANCEL -> "Request was cancelled.";
            case DECLINE -> "You declined to provide your name.";
        };
    }

    @Tool(name = "add-with-confirmation", description = "Adds two numbers after user confirms the operation")
    String addWithConfirmation(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b,
            ElicitationSender elicitationSender) throws Exception {
        if (!elicitationSender.isFormSupported()) {
            return String.valueOf(a + b);
        }
        Elicitation elicitation = Elicitation.builder("Confirm: add " + a + " + " + b + "?")
                .addSchemaProperty("confirm", new org.wildfly.extension.mcp.injection.elicitation.BooleanSchema(true))
                .timeout(30_000)
                .build();
        Elicitation.Response response = elicitationSender.send(elicitation);
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

        String elicitationId = "auth-integration-test-" + UUID.randomUUID();

        Elicitation elicitation = Elicitation
                .urlBuilder("Please authenticate with your identity provider",
                        "/my-app/callback")
                .elicitationId(elicitationId)
                .build();

        Elicitation.Response response = elicitationSender.send(elicitation);
        switch (response.action()) {
            case CANCEL -> {
                return "Authentication cancelled";
            }
            case DECLINE -> {
                return "Authentication declined";
            }
        }

        // The client accepted — the user consented to open the URL.
        // Now block until the out-of-band interaction completes (i.e. the
        // OAuth callback endpoint is hit by the user's browser).
        CompletableFuture<Void> callbackFuture = new CompletableFuture<>();
        pendingCallbacks.put(elicitationId, callbackFuture);
        callbackFuture.get(30, TimeUnit.SECONDS);

        // The out-of-band interaction is complete — notify the client.
        elicitationSender.notifyElicitationComplete(elicitationId);

        // At this point the tool can perform any operation with the authenticated client.
        return "Tool finished";
    }
}
