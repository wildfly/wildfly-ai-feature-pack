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

    // JSON-RPC envelope
    public static final String JSONRPC = "jsonrpc";
    public static final String ID = "id";
    public static final String METHOD = "method";
    public static final String RESULT = "result";
    public static final String ERROR = "error";
    public static final String CODE = "code";
    public static final String MESSAGE = "message";
    public static final String DATA = "data";

    // Common fields
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String REQUIRED = "required";
    public static final String TYPE = "type";
    public static final String TITLE = "title";
    public static final String VALUE = "value";
    public static final String VALUES = "values";
    public static final String PROPERTIES = "properties";

    // Initialization and server info
    public static final String VERSION = "version";
    public static final String PROTOCOL_VERSION = "protocolVersion";
    public static final String CLIENT_INFO = "clientInfo";
    public static final String SERVER_INFO = "serverInfo";
    public static final String CAPABILITIES = "capabilities";
    public static final String SUPPORTED_VERSIONS = "supportedVersions";
    public static final String EXTENSIONS = "extensions";

    // Request/response structure
    public static final String PARAMS = "params";
    public static final String META = "_meta";
    public static final String CURSOR = "cursor";
    public static final String NEXT_CURSOR = "nextCursor";
    public static final String ARGUMENTS = "arguments";

    // Content and resources
    public static final String CONTENT = "content";
    public static final String CONTENTS = "contents";
    public static final String TEXT = "text";
    public static final String BLOB = "blob";
    public static final String URI = "uri";
    public static final String URI_TEMPLATE = "uriTemplate";
    public static final String MIME_TYPE = "mimeType";
    public static final String RESOURCE = "resource";
    public static final String LAST_MODIFIED = "lastModified";
    public static final String STRUCTURED_CONTENT = "structuredContent";
    public static final String SIZE = "size";

    // Annotations
    public static final String ANNOTATIONS = "annotations";
    public static final String AUDIENCE = "audience";
    public static final String PRIORITY = "priority";

    // Tool hints
    public static final String READ_ONLY_HINT = "readOnlyHint";
    public static final String DESTRUCTIVE_HINT = "destructiveHint";
    public static final String IDEMPOTENT_HINT = "idempotentHint";
    public static final String OPEN_WORLD_HINT = "openWorldHint";

    // Tool schemas
    public static final String INPUT_SCHEMA = "inputSchema";
    public static final String OUTPUT_SCHEMA = "outputSchema";
    public static final String IS_ERROR = "isError";
    public static final String X_MCP_HEADER = "x-mcp-header";
    public static final String REQUIRED_CAPABILITIES = "requiredCapabilities";

    // Messages and roles
    public static final String MESSAGES = "messages";
    public static final String ROLE = "role";

    // Listing
    public static final String TOOLS = "tools";
    public static final String PROMPTS = "prompts";
    public static final String RESOURCES = "resources";
    public static final String RESOURCE_TEMPLATES = "resourceTemplates";

    // Completion
    public static final String COMPLETION = "completion";
    public static final String REF = "ref";
    public static final String ARGUMENT = "argument";
    public static final String CONTEXT = "context";
    public static final String TOTAL = "total";
    public static final String HAS_MORE = "hasMore";

    // Progress
    public static final String PROGRESS = "progress";
    public static final String PROGRESS_TOKEN = "progressToken";

    // Caching (2026-07-28)
    public static final String RESULT_TYPE = "resultType";
    public static final String CACHE_SCOPE = "cacheScope";
    public static final String TTL_MS = "ttlMs";

    // Capabilities detail
    public static final String LIST_CHANGED = "listChanged";
    public static final String SUBSCRIBE = "subscribe";
    public static final String COMPLETIONS = "completions";

    // Subscriptions
    public static final String NOTIFICATIONS = "notifications";
    public static final String SUBSCRIPTIONS = "subscriptions";
    public static final String SUBSCRIPTION_ID = "subscriptionId";
    public static final String RESOURCES_LIST_CHANGED = "resourcesListChanged";
    public static final String TOOLS_LIST_CHANGED = "toolsListChanged";
    public static final String PROMPTS_LIST_CHANGED = "promptsListChanged";
    public static final String RESOURCE_SUBSCRIPTIONS = "resourceSubscriptions";

    // Elicitation
    public static final String MODE = "mode";
    public static final String URL = "url";
    public static final String REQUESTED_SCHEMA = "requestedSchema";
    public static final String ELICITATION_ID = "elicitationId";
    public static final String ACTION = "action";

    // Error data
    public static final String SUPPORTED = "supported";
    public static final String REQUESTED = "requested";

    // Request state / MRTR (SEP-2322)
    public static final String REQUEST_STATE = "requestState";
    public static final String INPUT_REQUESTS = "inputRequests";
    public static final String INPUT_RESPONSES = "inputResponses";
    public static final String INPUT_REQUIRED = "input_required";
    public static final String STATE = "state";
    public static final String PRINCIPAL = "principal";
    public static final String REQUEST_ID = "requestId";
    public static final String TOOL_NAME = "toolName";
    public static final String CREATED_AT = "createdAt";
    public static final String EXPIRES_AT = "expiresAt";
    public static final String TOOL_STATE = "toolState";

    // Headers
    public static final String HEADERS = "headers";

    // W3C Trace Context
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
}
