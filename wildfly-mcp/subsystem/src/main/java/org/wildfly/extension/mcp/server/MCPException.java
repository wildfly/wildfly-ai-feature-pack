/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import java.io.Serial;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.api.Responder;

class MCPException extends Exception {

    @Serial
    private static final long serialVersionUID = 3142589829095593984L;

    private final int jsonRpcError;


    MCPException(String message, int jsonRpcError) {
        super(message);
        this.jsonRpcError = jsonRpcError;
    }

    int getJsonRpcError() {
        return jsonRpcError;
    }

    static void sendError(MCPException e, String id, Responder responder) {
        ROOT_LOGGER.errorProcessingRequest(e);
        responder.sendError(id, e.getJsonRpcError(), e.getMessage());
    }

    public static MCPException missingRequiredArgument(String argument) {
        return new MCPException(ROOT_LOGGER.missingRequiredArgument(argument), JsonRPC.INVALID_PARAMS);
    }
}
