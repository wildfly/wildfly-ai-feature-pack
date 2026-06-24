package org.wildfly.extension.mcp.api;

import org.mcp_java.server.resources.ResourceContents;
import java.util.List;

/**
 *
 * @param contents
 */
public record ResourceResponse(List<ResourceContents> contents) {

}
