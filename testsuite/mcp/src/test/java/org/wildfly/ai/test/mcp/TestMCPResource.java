/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.wildfly.mcp.api.resource.ResourceContents;

public class TestMCPResource {

    @Resource(uri = "test://info", mimeType = "text/plain", name = "test-info")
    ResourceContents info() {
        return ResourceContents.text("test://info", "WildFly MCP Test Resource");
    }

    @Resource(uri = "test://status", mimeType = "application/json", name = "test-status")
    ResourceContents status() {
        return ResourceContents.text("test://status", "{\"status\":\"running\",\"version\":\"1.0\"}");
    }

    @ResourceTemplate(uriTemplate = "test://weather/{city}", mimeType = "application/json", name = "test-weather")
    ResourceContents weather(@ResourceTemplateArg(name = "city") String city) {
        return ResourceContents.text("test://weather/" + city, "{\"city\":\"" + city + "\",\"temp\":\"22C\"}");
    }
}
