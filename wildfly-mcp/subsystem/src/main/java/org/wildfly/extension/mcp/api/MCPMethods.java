/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import java.util.Set;

/**
 * MCP protocol method names and version constant.
 */
public final class MCPMethods {

    public static final String PROTOCOL_VERSION = "2025-11-25";
    public static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(PROTOCOL_VERSION, "2025-06-18", "2025-03-26");
    public static final String INITIALIZE = "initialize";
    public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    public static final String NOTIFICATIONS_CANCEL = "notifications/cancelled";
    public static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    public static final String PROMPTS_LIST = "prompts/list";
    public static final String PROMPTS_GET = "prompts/get";
    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";
    public static final String RESOURCES_LIST = "resources/list";
    public static final String RESOURCE_TEMPLATES_LIST = "resources/templates/list";
    public static final String RESOURCES_READ = "resources/read";
    public static final String RESOURCES_SUBSCRIBE = "resources/subscribe";
    public static final String RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    public static final String PING = "ping";
    public static final String COMPLETION_COMPLETE = "completion/complete";
    public static final String Q_CLOSE = "q/close";

    private MCPMethods() {}
}
