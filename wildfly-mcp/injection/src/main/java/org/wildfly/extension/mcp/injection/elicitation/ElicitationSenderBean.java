/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import jakarta.enterprise.context.RequestScoped;

import org.wildfly.mcp.api.elicitation.Elicitation;
import org.wildfly.mcp.api.elicitation.ElicitationSender;

import static org.wildfly.extension.mcp.injection.MCPLogger.ROOT_LOGGER;

@RequestScoped
public class ElicitationSenderBean implements ElicitationSender {

    @Override
    public Elicitation.Response send(Elicitation elicitation) throws Exception {
        return delegate().send(elicitation);
    }

    @Override
    public boolean isFormSupported() {
        return delegate().isFormSupported();
    }

    @Override
    public boolean isUrlSupported() {
        return delegate().isUrlSupported();
    }

    @Override
    public void notifyElicitationComplete(String elicitationId) {
        delegate().notifyElicitationComplete(elicitationId);
    }

    private ElicitationSender delegate() {
        ElicitationSender sender = ElicitationSenderHolder.get();
        if (sender == null) {
            throw ROOT_LOGGER.elicitationSenderNotAvailable();
        }
        return sender;
    }
}
