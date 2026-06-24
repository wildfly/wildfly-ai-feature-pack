/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.mcp.api;

import java.lang.invoke.MethodHandles;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFMCPAPI", length = 5)
public interface MCPApiLogger extends BasicLogger {

    MCPApiLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), MCPApiLogger.class, "org.wildfly.mcp.api");

    @Message(id = 1, value = "Parameter %s must not be null")
    String parameterMustNotBeNull(String name);

    @Message(id = 2, value = "Parameter %s must be positive")
    IllegalArgumentException parameterMustBePositive(String name);

    @Message(id = 3, value = "Parameter %s must not be empty")
    IllegalArgumentException parameterMustNotBeEmpty(String name);

    @Message(id = 4, value = "Parameter %s must have the same size as parameter %s")
    IllegalArgumentException parameterMustHaveSameSize(String parameter1, String parameter2);

    @Message(id = 5, value = "Parameter 'max' (%s) can not be less than 'min' (%s) ")
    IllegalArgumentException maxCanNotBeLessThanMin(Integer max, Integer min);
}
