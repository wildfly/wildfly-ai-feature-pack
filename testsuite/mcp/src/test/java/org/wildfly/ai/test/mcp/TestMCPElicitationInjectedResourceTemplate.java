/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import jakarta.inject.Inject;

import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.wildfly.mcp.model.elicitation.Elicitation;
import org.wildfly.mcp.model.elicitation.ElicitationSender;
import org.wildfly.mcp.model.resource.ResourceContents;

public class TestMCPElicitationInjectedResourceTemplate {

    @Inject
    ElicitationSender elicitationSender;

    @ResourceTemplate(uriTemplate = "test://secret/{id}", mimeType = "text/plain", name = "secret-data")
    ResourceContents secretData(@ResourceTemplateArg(name = "id") String id) throws Exception {
        if (!elicitationSender.isFormSupported()) {
            return ResourceContents.text("test://secret/" + id, "access-denied");
        }
        Elicitation.FormBuilder form = Elicitation.formBuilder("Confirm access to secret " + id + "?")
                .timeout(30_000);
        form.addBoolean("confirm");
        Elicitation.Response response = elicitationSender.send(form.build());
        if (response.isAccepted() && response.getBoolean("confirm").orElse(false)) {
            return ResourceContents.text("test://secret/" + id, "secret-value-for-" + id);
        }
        return ResourceContents.text("test://secret/" + id, "access-denied");
    }
}
