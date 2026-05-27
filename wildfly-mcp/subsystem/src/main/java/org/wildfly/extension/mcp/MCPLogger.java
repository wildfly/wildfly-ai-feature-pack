/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.mcp;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

import java.lang.invoke.MethodHandles;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFMCP", length = 5)
public interface MCPLogger extends BasicLogger {

    MCPLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), MCPLogger.class, "org.wildfly.extension.mcp");

    @LogMessage(level = WARN)
    @Message(id = 1, value = "The deployment does not have Jakarta Dependency Injection enabled.")
    void cdiRequired();

    @Message(id = 2, value = "Unable to resolve annotation index for deployment unit: %s")
    DeploymentUnitProcessingException unableToResolveAnnotationIndex(DeploymentUnit deploymentUnit);

    @LogMessage(level = INFO)
    @Message(id = 3, value = "Registered MCP endpoint '%s' for host '%s'")
    void endpointRegistered(String path, String hostName);

    @LogMessage(level = INFO)
    @Message(id = 4, value = "Unregistered MCP endpoint '%s' for host '%s'")
    void endpointUnregistered(String path, String hostName);

    @LogMessage(level = ERROR)
    @Message(id = 5, value = "Invalid HTTP Method: %s")
    void invalidHttpMethod(String method);

    @LogMessage(level = ERROR)
    @Message(id = 6, value = "Invalid value for HTTP header: %s")
    void invalidAcceptHeaders(String header);

    @LogMessage(level = WARN)
    @Message(id = 8, value = "Managed executor service not available, using default executor service")
    void managedExecutorServiceNotAvailable();

    @LogMessage(level = ERROR)
    @Message(id = 9, value = "Invalid MCP-Protocol-Version header: expected '%s' but got '%s'")
    void invalidProtocolVersion(String expected, String actual);

    @LogMessage(level = ERROR)
    @Message(id = 10, value = "Failure sending message")
    void failureSendingMessage(@Cause Throwable cause);

    @LogMessage(level = WARN)
    @Message(id = 11, value = "Failed to send event to client: %s")
    void failedToSendEvent(String data);

    @LogMessage(level = WARN)
    @Message(id = 12, value = "Elicitation request %d timed out after %d ms")
    void elicitationTimedOut(long requestId, long timeoutMillis);

    @LogMessage(level = WARN)
    @Message(id = 13, value = "Connection id is missing: %s")
    void connectionIdMissing(String requestPath);

    @LogMessage(level = ERROR)
    @Message(id = 14, value = "Error invoking tool %s")
    void errorInvokingTool(@Cause Throwable cause, String toolName);

    @LogMessage(level = ERROR)
    @Message(id = 15, value = "Error processing MCP request")
    void errorProcessingRequest(@Cause Throwable cause);

    @LogMessage(level = ERROR)
    @Message(id = 16, value = "Error invoking prompt %s")
    void errorInvokingPrompt(@Cause Throwable cause, String promptName);

    @LogMessage(level = ERROR)
    @Message(id = 17, value = "Error invoking resource %s")
    void errorInvokingResource(@Cause Throwable cause, String resourceUri);

    @LogMessage(level = ERROR)
    @Message(id = 18, value = "Error invoking resource template %s")
    void errorInvokingResourceTemplate(@Cause Throwable cause, String resourceUri);

    @LogMessage(level = ERROR)
    @Message(id = 19, value = "Error invoking completion %s")
    void errorInvokingCompletion(@Cause Throwable cause, String name);
}
