/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

@ApplicationPath("/my-app")
public class TestThirdPartyApplication extends Application {
    /**
     * Simulates a callback endpoint that a user's browser would hit
     * after completing an out-of-band authentication flow.
     */
    @Path("/callback")
    public static class TestCallbackEndpoint {

        @GET
        @Path("/{elicitationId}")
        public Response callback(@PathParam("elicitationId") String elicitationId) {
            boolean completed = TestMCPElicitationTool.completeOutOfBandInteraction(elicitationId);
            if (completed) {
                return Response.ok("Interaction completed for " + elicitationId).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No pending interaction for " + elicitationId)
                    .build();
        }
    }
}
