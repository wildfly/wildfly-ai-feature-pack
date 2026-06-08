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

    @Message(id = 2, value = "Parameter %s must not be null")
    String parameterMustNotBeNull(String name);

    @Message(id = 3, value = "Parameter %s must be positive")
    IllegalArgumentException parameterMustBePositive(String name);

    @Message(id = 4, value = "At least one schema property must be added to the Elicitation Form")
    IllegalArgumentException mustHaveAtLeastOneSchemaProperty();

    @Message(id = 5, value = "Parameter %s must not be empty")
    IllegalArgumentException parameterMustNotBeEmpty(String name);

    @Message(id = 6, value = "Parameter %s must have the same size as parameter %s")
    IllegalArgumentException parameterMustHaveSameSize(String parameter1, String parameter2);

    @Message(id = 7, value = "Parameter 'max' (%s) can not be less than 'min' (%s) ")
    IllegalArgumentException maxCanNotBeLessThanMin(Integer max, Integer min);
}
