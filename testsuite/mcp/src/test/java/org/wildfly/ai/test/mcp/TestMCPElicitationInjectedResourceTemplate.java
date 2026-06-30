/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import jakarta.inject.Inject;

import org.mcpjava.server.resources.ResourceContents;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.resources.TextResourceContents;
import org.wildfly.mcp.api.elicitation.Elicitation;
import org.wildfly.mcp.api.elicitation.ElicitationSender;

public class TestMCPElicitationInjectedResourceTemplate {

    @Inject
    ElicitationSender elicitationSender;

    @ResourceTemplate(uriTemplate = "test://secret/{id}", mimeType = "text/plain", name = "secret-data")
    ResourceContents secretData(@ResourceTemplateArg(name = "id") String id) throws Exception {
        if (!elicitationSender.isFormSupported()) {
            return TextResourceContents.of("test://secret/" + id, "access-denied");
        }
        Elicitation.FormBuilder form = Elicitation.formBuilder("Confirm access to secret " + id + "?")
                .timeout(30_000);
        form.addBoolean("confirm");
        Elicitation.Response response = elicitationSender.send(form.build());
        if (response.isAccepted() && response.getBoolean("confirm").orElse(false)) {
            return TextResourceContents.of("test://secret/" + id, "secret-value-for-" + id);
        }
        return TextResourceContents.of("test://secret/" + id, "access-denied");
    }
}
