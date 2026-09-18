/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

/**
 * MCP protocol method names and version constant.
 */
public final class MCPMethods {

    public static final String PROTOCOL_VERSION = "2025-11-25";
    public static final String LATEST_PROTOCOL_VERSION = "2026-07-28";
    public static final String INITIALIZE = "initialize";
    public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    public static final String NOTIFICATIONS_CANCEL = "notifications/cancelled";
    public static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    public static final String NOTIFICATIONS_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";
    public static final String NOTIFICATIONS_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
    public static final String NOTIFICATIONS_PROMPTS_LIST_CHANGED = "notifications/prompts/list_changed";
    public static final String NOTIFICATIONS_RESOURCES_LIST_CHANGED = "notifications/resources/list_changed";
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
    public static final String LOGGING_SET_LEVEL = "logging/setLevel";
    public static final String COMPLETION_COMPLETE = "completion/complete";
    public static final String SERVER_DISCOVER = "server/discover";
    public static final String SUBSCRIPTIONS_LISTEN = "subscriptions/listen";
    public static final String NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED = "notifications/subscriptions/acknowledged";
    public static final String Q_CLOSE = "q/close";

    public static final int HEADER_MISMATCH = -32020;
    public static final int MISSING_REQUIRED_CLIENT_CAPABILITY = -32021;
    public static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    private MCPMethods() {}
}
