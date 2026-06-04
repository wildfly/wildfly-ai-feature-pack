/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection;

/**
 * Constants for MCP protocol JSON field names.
 */
public final class MCPFieldNames {

    private MCPFieldNames() {
    }

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String REQUIRED = "required";
    public static final String CURSOR = "cursor";
    public static final String NEXT_CURSOR = "nextCursor";
    public static final String ARGUMENTS = "arguments";
    public static final String CONTENT = "content";
    public static final String TEXT = "text";
    public static final String TYPE = "type";
    public static final String URI = "uri";
    public static final String URI_TEMPLATE = "uriTemplate";
    public static final String MIME_TYPE = "mimeType";
    public static final String TITLE = "title";
    public static final String ANNOTATIONS = "annotations";
    public static final String STRUCTURED_CONTENT = "structuredContent";
    public static final String READ_ONLY_HINT = "readOnlyHint";
    public static final String DESTRUCTIVE_HINT = "destructiveHint";
    public static final String IDEMPOTENT_HINT = "idempotentHint";
    public static final String OPEN_WORLD_HINT = "openWorldHint";
    public static final String INPUT_SCHEMA = "inputSchema";
    public static final String OUTPUT_SCHEMA = "outputSchema";
    public static final String IS_ERROR = "isError";
    public static final String BLOB = "blob";
    public static final String CONTENTS = "contents";
    public static final String MESSAGES = "messages";
    public static final String ROLE = "role";
    public static final String PARAMS = "params";
    public static final String META = "_meta";
    public static final String TOOLS = "tools";
    public static final String PROMPTS = "prompts";
    public static final String RESOURCES = "resources";
    public static final String RESOURCE_TEMPLATES = "resourceTemplates";
    public static final String PROGRESS_TOKEN = "progressToken";
}
