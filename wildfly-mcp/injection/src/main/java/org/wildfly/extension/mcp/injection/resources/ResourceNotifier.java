/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.resources;

/**
 * Injected into tool methods to allow notifying subscribed clients that a
 * resource has been updated.
 *
 * <p>When a client subscribes to a resource via {@code resources/subscribe},
 * calling {@link #notifyResourceUpdated(String)} with the resource URI sends
 * a {@code notifications/resources/updated} notification to all subscribers.</p>
 *
 * <p>Example tool method signature:</p>
 * <pre>{@code
 * @Tool(description = "Updates a data file")
 * public String updateData(String content, ResourceNotifier notifier) {
 *     // write data...
 *     notifier.notifyResourceUpdated("file:///data/report.csv");
 *     return "Updated";
 * }
 * }</pre>
 */
public interface ResourceNotifier {

    /**
     * Send a {@code notifications/resources/updated} notification to all clients
     * that have subscribed to the given resource URI.
     *
     * @param uri the URI of the resource that was updated
     */
    void notifyResourceUpdated(String uri);
}
