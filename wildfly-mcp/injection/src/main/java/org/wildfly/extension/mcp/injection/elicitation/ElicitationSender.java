/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

/**
 * Injected into tool methods to allow pausing execution and requesting additional
 * user input from the MCP client.
 *
 * <p>The client must declare the {@code "elicitation"} capability during initialization.
 * Use {@link #isFormSupported()} or {@link #isUrlSupported()} ()}  to check before calling {@link #send}.</p>
 *
 * <p>Example tool method signature:</p>
 * <pre>{@code
 * @Tool(description = "Creates a user account")
 *  public default String createAccount(String email, ElicitationSender elicitation) throws Exception {
 *     if (elicitation.isFormSupported()) {
 *         Elicitation.Response response = elicitation.send(
 *             Elicitation.formBuilder("Please confirm the account details")
 *                 .addSchemaProperty("confirm", new BooleanSchema(true))
 *                 .build());
 *         if (!response.isAccepted()) return "Cancelled";
 *     }
 *     // proceed...
 * }
 * }</pre>
 */
public interface ElicitationSender {

    /**
     * Send an elicitation request to the client and block until the client responds
     * or the timeout (default 30 s) expires. Supports both form-mode and URL-mode
     * requests — the mode is determined by the request's {@link Elicitation#mode()}.
     *
     * @param elicitation the elicitation request (form or URL mode)
     * @return the client's response
     * @throws IllegalStateException if the client does not support the required elicitation mode
     * @throws java.util.concurrent.TimeoutException if the client does not respond within the timeout
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    Elicitation.Response send(Elicitation elicitation) throws Exception;

    /**
     * Returns {@code true} if the connected client declared Form mode support
     * within the {@code "elicitation"} capability.
     */
    boolean isFormSupported();

    /**
     * Returns {@code true} if the connected client declared URL mode support
     * within the {@code "elicitation"} capability.
     */
    default boolean isUrlSupported() {
        return false;
    }

    /**
     * Send a {@code notifications/elicitation/complete} notification to the client,
     * indicating that the out-of-band interaction for the given elicitation has finished.
     *
     * @param elicitationId the ID of the completed elicitation
     */
    default void notifyElicitationComplete(String elicitationId) {
    }
}
