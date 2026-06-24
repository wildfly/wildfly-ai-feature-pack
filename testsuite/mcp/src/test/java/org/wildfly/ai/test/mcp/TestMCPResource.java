/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.resources.TextResourceContents;

public class TestMCPResource {

    @Resource(uri = "test://info", mimeType = "text/plain", name = "test-info")
    TextResourceContents info() {
        return TextResourceContents.of("test://info", "WildFly MCP Test Resource");
    }

    @Resource(uri = "test://status", mimeType = "application/json", name = "test-status")
    TextResourceContents status() {
        return TextResourceContents.of("test://status", "{\"status\":\"running\",\"version\":\"1.0\"}");
    }

    @ResourceTemplate(uriTemplate = "test://weather/{city}", mimeType = "application/json", name = "test-weather")
    TextResourceContents weather(@ResourceTemplateArg(name = "city") String city) {
        return TextResourceContents.of("test://weather/" + city, "{\"city\":\"" + city + "\",\"temp\":\"22C\"}");
    }
}
