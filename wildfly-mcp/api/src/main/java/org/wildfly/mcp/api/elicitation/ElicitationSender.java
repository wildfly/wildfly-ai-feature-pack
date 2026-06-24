/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.elicitation;

/**
 * Allows MCP methods (tools, prompts, resources, resource templates) to pause
 * execution and request additional user input from the MCP client.
 *
 * <p>The client must declare the {@code "elicitation"} capability during initialization.
 * Use {@link #isFormSupported()} or {@link #isUrlSupported()} to check before calling {@link #send}.</p>
 *
 * <p>An {@code ElicitationSender} can be obtained either by CDI injection or as a
 * tool method parameter:</p>
 *
 * <p><b>CDI injection (recommended):</b></p>
 * <pre>{@code
 * @Inject
 * ElicitationSender elicitationSender;
 *
 * @Tool(description = "Creates a user account")
 * public String createAccount(String email) throws Exception {
 *     if (elicitationSender.isFormSupported()) {
 *         Elicitation.FormBuilder form = Elicitation.formBuilder("Please confirm the account details");
 *         BooleanProperty confirm = form.addBoolean("confirm");
 *         Elicitation.Response response = elicitationSender.send(form.build());
 *         if (!response.isAccepted()) return "Cancelled";
 *     }
 *     // proceed...
 * }
 * }</pre>
 *
 * <p><b>Method parameter (only for tool invocation):</b></p>
 * <pre>{@code
 * @Tool(description = "Creates a user account")
 * public String createAccount(String email, ElicitationSender elicitationSender) throws Exception {
 *     // same usage as above
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
