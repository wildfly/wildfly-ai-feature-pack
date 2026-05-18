/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import org.mcp_java.server.McpLog;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.Responder;

class McpLogImpl implements McpLog {

    private final MCPConnection connection;
    private final Responder responder;
    private final String loggerName;

    McpLogImpl(MCPConnection connection, Responder responder, String loggerName) {
        this.connection = connection;
        this.responder = responder;
        this.loggerName = loggerName;
    }

    @Override
    public LogLevel level() {
        return connection.logLevel();
    }

    @Override
    public void send(LogLevel level, Object data) {
        if (level.ordinal() >= connection.logLevel().ordinal()) {
            JsonObjectBuilder params = Json.createObjectBuilder()
                    .add("level", level.name().toLowerCase())
                    .add("logger", loggerName)
                    .add("data", String.valueOf(data));
            responder.send(Messages.newNotification("notifications/message", params));
        }
    }

    @Override
    public void send(LogLevel level, String message, Object... args) {
        if (level.ordinal() >= connection.logLevel().ordinal()) {
            String formatted = args.length > 0 ? String.format(message, args) : message;
            send(level, (Object) formatted);
        }
    }

    @Override
    public void debug(String message, Object... args) {
        send(LogLevel.DEBUG, message, args);
    }

    @Override
    public void info(String message, Object... args) {
        send(LogLevel.INFO, message, args);
    }

    @Override
    public void error(String message, Object... args) {
        send(LogLevel.ERROR, message, args);
    }

    @Override
    public void error(Throwable throwable, String message, Object... args) {
        String formatted = args.length > 0 ? String.format(message, args) : message;
        String detail = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getName();
        String errorMessage = formatted + ": " + detail;
        send(LogLevel.ERROR, (Object) errorMessage);
    }
}
