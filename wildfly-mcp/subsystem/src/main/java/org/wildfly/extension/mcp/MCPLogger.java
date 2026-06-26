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
import org.wildfly.mcp.api.elicitation.Elicitation;

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

    @LogMessage(level = WARN)
    @Message(id = 5, value = "Invalid HTTP Method: %s")
    void invalidHttpMethod(String method);

    @LogMessage(level = WARN)
    @Message(id = 6, value = "Invalid value for HTTP header: %s")
    void invalidAcceptHeaders(String header);

    @LogMessage(level = WARN)
    @Message(id = 8, value = "Managed executor service not available, using default executor service")
    void managedExecutorServiceNotAvailable();

    @LogMessage(level = WARN)
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

    @LogMessage(level = INFO)
    @Message(id = 20, value = "Closing stale MCP connection [%s] due to inactivity timeout")
    void closingStaleConnection(String id);

    @LogMessage(level = ERROR)
    @Message(id = 21, value = "Failed to close MCP connection %s")
    void errorClosingConnection(@Cause Throwable cause, String id);

    @LogMessage(level = WARN)
    @Message(id = 22, value = "Failed to broadcast notification to connection %s")
    void errorBroadcastingNotification(@Cause Throwable cause, String connectionId);

    @LogMessage(level = WARN)
    @Message(id = 23, value = "Failed to send shutdown notification to connection %s")
    void errorBroadcastingShutdownNotification(@Cause Throwable cause, String connectionId);

    @LogMessage(level = WARN)
    @Message(id = 24, value = "Failed to serialize structuredContent for tool %s")
    void errorSerializingStructuredContent(@Cause Throwable cause, String toolName);

    @Message(id = 25, value = "Missing required argument: %s")
    String missingRequiredArgument(String argument);

    @Message(id = 26, value = "Message params must be present")
    String missingRequiredMessage();

    @LogMessage(level = WARN)
    @Message(id = 27, value = "Could not generate output schema for return type %s: %s")
    void errorGeneratingOutputSchema(@Cause Throwable cause, String returnType, String errorMessage);

    @Message(id = 28, value = "Invalid tool name: %s")
    String invalidToolName(String tool);

    @Message(id = 29, value = "Tool invocation failed")
    String errorInvokingTool();

    @Message(id = 30, value = "pageSize must not be negative: %d")
    IllegalArgumentException invalidPageSize(int pageSize);

    @Message(id = 31, value = "Invalid prompt name: %s")
    String invalidPromptName(String prompt);

    @Message(id = 32, value = "Invalid resource name: %s")
    String invalidResourceName(String resourceUri);

    @Message(id = 33, value = "Resource URI not defined")
    String resourceUriNotDefined();

    @Message(id = 34, value = "No resource template matches URI: %s")
    String noMatchingResourceTemplate(String resourceUri);

    @LogMessage(level = ERROR)
    @Message(id = 35, value = "Skipping tool '%s' from listing (schema generation failed): %s")
    void errorSkippingToolFromListing(String toolName, String reason);

    @LogMessage(level = WARN)
    @Message(id = 36, value = "Schema generator class '%s' does not implement ToolSchemaGenerator")
    void warnSchemaGeneratorInvalidType(String className);

    @LogMessage(level = WARN)
    @Message(id = 37, value = "Failed to resolve schema generator '%s'")
    void warnFailedToResolveSchemaGenerator(@Cause Throwable cause, String className);

    @Message(id = 38, value = "Client does not support %s mode elicitation")
    IllegalStateException elicitationModeNotSupported(Elicitation.Mode mode);

    @Message(id = 39, value = "Invalid endpoint path '%s' for parameter '%s': only alphanumeric characters (a-z, A-Z, 0-9), hyphens (-), and underscores (_) are allowed")
    String invalidEndpointPath(String value, String parameterName);

    @Message(id = 40, value = "Connection was already shut down")
    String connectionAlreadyShutdown();

    @Message(id = 41, value = "Unable to obtain the connection to be closed: %s")
    String unableToObtainConnectionToClose(String id);

    @Message(id = 42, value = "Unsupported method: %s")
    String unsupportedMethod(String method);

    @LogMessage(level = WARN)
    @Message(id = 43, value = "OpenTelemetry MCP listener initialization failed; tracing and metrics are disabled")
    void openTelemtryListenerInitializationFailure(@Cause Throwable cause);

    @Message(id = 44, value = "Unhandled content block type: %s")
    @LogMessage(level = WARN)
    void warnUnhandledContentBlockType(String className);
}
