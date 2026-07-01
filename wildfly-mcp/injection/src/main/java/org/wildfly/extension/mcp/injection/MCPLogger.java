/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.mcp.injection;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.WARN;

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

    @LogMessage(level = WARN)
    @Message(id = 2, value = "Vetoing user-defined %s bean %s as this bean is provided by the MCP subsystem and must not be overridden by deployments")
    void vetoedCDIBean(String interfaceName, String className);

    @Message(id = 3, value = "ElicitationSender is not available outside of an MCP invocation context")
    IllegalStateException elicitationSenderNotAvailable();

    @Message(id = 4, value = "Deployment must not produce %s bean — it is provided by the MCP subsystem")
    IllegalStateException deploymentMustNotProduceBean(String className);

    @Message(id = 5, value = "Progress is not available outside of an MCP invocation context")
    IllegalStateException progressNotAvailable();
}
