/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.mcp;

import org.mcp_java.model.resource.ResourceContents;
import org.mcp_java.server.resources.Resource;
import org.mcp_java.server.resources.ResourceTemplate;
import org.mcp_java.server.resources.ResourceTemplateArg;

public class InfoResources {

    @Resource(uri = "info://server", mimeType = "text/plain", name = "server-info",
              description = "Returns information about the WildFly server")
    public ResourceContents serverInfo() {
        String info = "WildFly AI Feature Pack Example MCP Server\n"
                + "Runtime: " + System.getProperty("java.runtime.name") + "\n"
                + "Java Version: " + System.getProperty("java.version");
        return ResourceContents.text("info://server", info);
    }

    @ResourceTemplate(uriTemplate = "info://env/{variable}", mimeType = "text/plain",
                       name = "env-variable",
                       description = "Returns the value of an environment variable")
    public ResourceContents envVariable(
            @ResourceTemplateArg(name = "variable") String variable) {
        String value = System.getenv(variable);
        String result = variable + "=" + (value != null ? value : "(not set)");
        return ResourceContents.text("info://env/" + variable, result);
    }
}
