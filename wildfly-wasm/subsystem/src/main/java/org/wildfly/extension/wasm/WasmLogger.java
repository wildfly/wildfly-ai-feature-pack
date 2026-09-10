/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.wasm;

import static org.jboss.logging.Logger.Level.ERROR;
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

@MessageLogger(projectCode = "WFWASM", length = 5)
public interface WasmLogger extends BasicLogger {

    WasmLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), WasmLogger.class, "org.wildfly.extension.wasm");

    @LogMessage(level = WARN)
    @Message(id = 1, value = "The deployment does not have Jakarta Dependency Injection enabled.")
    void cdiRequired();

    @Message(id = 2, value = "Unable to resolve annotation index for deployment unit: %s")
    DeploymentUnitProcessingException unableToResolveAnnotationIndex(DeploymentUnit deploymentUnit);

    @LogMessage(level = ERROR)
    @Message(id = 3, value = "Couldn't resolve the MCP tool qualifier: registering the WASM service extension without MCP integration")
    void errorResolvingMCPToolQualifier(@Cause Throwable cause);

    @LogMessage(level = ERROR)
    @Message(id = 4, value = "Couldn't register the deployment resources for the WASM tool configurations")
    void errorRegisteringWasmToolDeploymentResources(@Cause Throwable cause);

}
