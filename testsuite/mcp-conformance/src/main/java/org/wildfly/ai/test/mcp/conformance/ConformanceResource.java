/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp.conformance;

import static org.wildfly.ai.test.mcp.conformance.ConformanceFixtures.MINIMAL_PNG;

import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.TextResourceContents;

public class ConformanceResource {

    @Resource(uri = "test://info", mimeType = "text/plain", name = "test-info")
    TextResourceContents info() {
        return TextResourceContents.of("test://info", "MCP Conformance Test Resource");
    }

    @Resource(uri = "test://static-text", mimeType = "text/plain", name = "static-text",
            description = "A static text resource for conformance testing")
    TextResourceContents staticText() {
        return TextResourceContents.of("test://static-text",
                "This is the content of the static text resource.");
    }

    @Resource(uri = "test://static-binary", mimeType = "image/png", name = "static-binary",
            description = "A static binary resource for conformance testing")
    ResourceResponse staticBinary() {
        return ResourceResponse.of("test://static-binary", MINIMAL_PNG, "image/png");
    }
}
