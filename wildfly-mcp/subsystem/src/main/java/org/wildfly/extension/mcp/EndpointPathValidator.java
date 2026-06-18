/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates that an endpoint path contains only alphanumeric characters (a-z, A-Z, 0-9),
 * hyphens (-), and underscores (_).
 */
public class EndpointPathValidator implements ParameterValidator {

    private static final Pattern VALID_PATH = Pattern.compile("^[a-zA-Z0-9\\-_]+$");

    static final EndpointPathValidator INSTANCE = new EndpointPathValidator();

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        if (value.isDefined() && value.getType() == ModelType.STRING) {
            String path = value.asString();
            if (!VALID_PATH.matcher(path).matches()) {
                throw new OperationFailedException(MCPLogger.ROOT_LOGGER.invalidEndpointPath(path, parameterName));
            }
        }
    }

}
