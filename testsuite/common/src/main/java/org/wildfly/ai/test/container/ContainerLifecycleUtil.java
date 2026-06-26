/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.container;

import org.testcontainers.containers.GenericContainer;

import java.util.logging.Logger;

/**
 * Shared utility for container lifecycle management.
 */
public class ContainerLifecycleUtil {

    private static final Logger LOG = Logger.getLogger(ContainerLifecycleUtil.class.getName());

    private ContainerLifecycleUtil() {
    }

    /**
     * Registers a JVM shutdown hook that stops the given container when the build finishes.
     * Does nothing if the container is {@code null} (i.e. a local instance was reused).
     *
     * @param container the container to stop, or {@code null} for a locally-running service
     * @param name      human-readable name used in log messages (e.g. "Ollama", "LGTM")
     */
    public static void registerShutdownHook(GenericContainer<?> container, String name) {
        String threadName = name.toLowerCase().replace(" ", "-") + "-container-shutdown";
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (container != null && container.isRunning()) {
                LOG.info("Stopping " + name + " container...");
                try {
                    container.stop();
                    LOG.info(name + " container stopped successfully");
                } catch (Exception e) {
                    LOG.warning("Failed to stop " + name + " container: " + e.getMessage());
                }
            }
        }, threadName));
    }
}
