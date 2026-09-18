/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp.conformance;

import jakarta.json.Json;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;

public class ConformanceResourceTemplate {

    @ResourceTemplate(name = "template-data",
            title = "Template Data",
            description = "A resource template for conformance testing",
            uriTemplate = "test://template/{id}/data",
            mimeType = "application/json")
    ResourceResponse templateData(@ResourceTemplateArg(name = "id") String id) {
        String json = Json.createObjectBuilder()
                .add("id", id)
                .add("templateTest", true)
                .add("data", "Data for ID: " + id)
                .build()
                .toString();
        return ResourceResponse.of("test://template/" + id + "/data", json, "application/json");
    }
}
