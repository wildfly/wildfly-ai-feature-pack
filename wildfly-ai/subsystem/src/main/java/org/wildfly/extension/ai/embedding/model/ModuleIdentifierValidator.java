/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.embedding.model;

import static org.wildfly.extension.ai.AILogger.ROOT_LOGGER;

import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
//TODO; replace with one from WF35 core once it is available
public class ModuleIdentifierValidator extends ModelTypeValidator {

    ModuleIdentifierValidator(boolean allowsUndefined, boolean allowsExpression) {
        super(ModelType.STRING, allowsUndefined, allowsExpression);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            String module = value.asString();
            try {
                ModuleIdentifierUtil.parseCanonicalModuleIdentifier(module);
            } catch (IllegalArgumentException e) {
                throw ROOT_LOGGER.invalidModuleIdentifier(e, module, e.getMessage());
            }
        }
    }
}
