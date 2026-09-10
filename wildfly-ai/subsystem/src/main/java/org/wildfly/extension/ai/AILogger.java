/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.ai;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

import java.lang.invoke.MethodHandles;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFAI", length = 5)
public interface AILogger extends BasicLogger {

    AILogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), AILogger.class, "org.wildfly.extension.ai");

    @LogMessage(level = WARN)
    @Message(id = 1, value = "The deployment does not have Jakarta Dependency Injection enabled.")
    void cdiRequired();

    @Message(id = 2, value = "Unable to resolve annotation index for deployment unit: %s")
    DeploymentUnitProcessingException unableToResolveAnnotationIndex(DeploymentUnit deploymentUnit);

    @Message(id = 3, value = "Couldn't access the Chat Language Model called %s")
    OperationFailedException chatLanguageModelServiceUnavailable(String chatLanguageModelName);

    @LogMessage(level = INFO)
    @Message(id = 4, value = "No OpenTelemetry support available for AI")
    void openTelemetryNotAvailable();

    @LogMessage(level = ERROR)
    @Message(id = 5, value = "Couldn't load the EmbeddingModel %s from module %s")
    void errorLoadingEmbeddingModel(@Cause Throwable cause, String embeddingModelClassName, String moduleName);

    @LogMessage(level = ERROR)
    @Message(id = 6, value = "Couldn't get the class type for %s to be able to check what to inject")
    void errorResolvingInjectionType(@Cause Throwable cause, String className);

    @Message(id = 7, value = "Invalid module identifier '%s': %s")
    OperationFailedException invalidModuleIdentifier(@Cause Throwable cause, String module, String reason);

    @LogMessage(level = ERROR)
    @Message(id = 8, value = "Couldn't register the deployment resource for the Chat Language Model %s")
    void errorRegisteringChatModelDeploymentResource(@Cause Throwable cause, String chatLanguageModelName);

}
