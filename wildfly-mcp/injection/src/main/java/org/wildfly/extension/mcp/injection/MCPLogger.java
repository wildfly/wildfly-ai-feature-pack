/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.mcp.injection;

import static org.jboss.logging.Logger.Level.ERROR;

import java.lang.invoke.MethodHandles;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFMCPINJC", length = 5)
public interface MCPLogger extends BasicLogger {

    MCPLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), MCPLogger.class, "org.wildfly.extension.mcp.injection");

    @LogMessage(level = ERROR)
    @Message(id = 1, value = "Unexpected error")
    void unexpectedError(@Cause Throwable cause);
}
