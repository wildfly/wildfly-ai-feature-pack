/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.wildfly.extension.mcp.server.MCPTestHelpers.initializeMessage;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mcp_java.model.common.Annotations;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class ResourceFeaturesTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private TestMCPConnection connection;
    private ConnectionManager connectionManager;
    private WildFlyMCPRegistry registry;

    @Before
    public void setUp() {
        registry = new WildFlyMCPRegistry();

        // Resource WITH title, size, and annotations
        registry.addResource("file:///data/report.csv", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE, "report",
                new MethodMetadata("getReport", "Monthly report", "file:///data/report.csv", "text/csv",
                        List.of(), "org.test.ReportResource", "java.lang.String"),
                "Monthly Report", 2048, new Annotations("user", 0.7)));

        // Resource WITHOUT title, size, or annotations
        registry.addResource("file:///logs/app.log", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE, "app-log",
                new MethodMetadata("appLog", "Application log", "file:///logs/app.log", "text/plain",
                        List.of(), "org.test.AppLogResource", "java.lang.String")));

        // Resource with audience-only annotations (no priority)
        registry.addResource("file:///data/public.txt", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE, "public-data",
                new MethodMetadata("getPublicData", "Public data", "file:///data/public.txt", "text/plain",
                        List.of(), "org.test.PublicDataResource", "java.lang.String"),
                null, -1, new Annotations("user", null)));

        // Resource with priority-only annotations (no audience)
        registry.addResource("file:///data/priority.txt", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE, "priority-data",
                new MethodMetadata("getPriorityData", "Priority data", "file:///data/priority.txt", "text/plain",
                        List.of(), "org.test.PriorityDataResource", "java.lang.String"),
                null, -1, new Annotations(null, 0.3)));

        // Resource template WITH title and annotations
        registry.addResourceTemplate("db:///{database}/tables/{table}", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE, "db-table",
                new MethodMetadata("readTable", "Read a database table", "db:///{database}/tables/{table}", "application/json",
                        List.of(
                                new ArgumentMetadata("database", "Database name", true, String.class),
                                new ArgumentMetadata("table", "Table name", true, String.class)),
                        "org.test.DbResource", "java.lang.String"),
                "Database Table", -1, new Annotations("assistant", 0.9)));

        // Resource template WITHOUT title or annotations
        registry.addResourceTemplate("config:///{key}", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE, "config-entry",
                new MethodMetadata("readConfig", "Read config entry", "config:///{key}", "text/plain",
                        List.of(new ArgumentMetadata("key", "Config key", true, String.class)),
                        "org.test.ConfigResource", "java.lang.String")));

        connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");

        responder = new TestResponder();
        connection = new TestMCPConnection("test-conn-1");
        connectionManager.add(connection);
    }

    // ==================== Capabilities Tests ====================

    @Test
    public void testResourcesListChangedCapabilityAdvertised() {
        handler.handle(initializeMessage(1), connection, responder);

        JsonObject resources = responder.lastResult().getJsonObject("capabilities").getJsonObject("resources");
        assertNotNull(resources);
        assertTrue(resources.getBoolean("listChanged"));
        assertTrue(resources.getBoolean("subscribe"));
    }

    // ==================== Resources List — Field Presence ====================

    @Test
    public void testResourcesListIncludesTitleWhenSet() {
        moveToOperation();

        handler.handle(jsonRpcRequest(10, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "report");
        assertNotNull(resource);
        assertTrue(resource.containsKey("title"));
        assertEquals("Monthly Report", resource.getString("title"));
    }

    @Test
    public void testResourcesListIncludesSizeWhenNonNegative() {
        moveToOperation();

        handler.handle(jsonRpcRequest(11, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "report");
        assertNotNull(resource);
        assertTrue(resource.containsKey("size"));
        assertEquals(2048, resource.getInt("size"));
    }

    @Test
    public void testResourcesListIncludesAnnotations() {
        moveToOperation();

        handler.handle(jsonRpcRequest(12, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "report");
        assertNotNull(resource);
        assertTrue(resource.containsKey("annotations"));
        JsonObject annotations = resource.getJsonObject("annotations");
        assertEquals("user", annotations.getJsonArray("audience").getString(0));
        assertEquals(0.7, annotations.getJsonNumber("priority").doubleValue(), 0.001);
    }

    @Test
    public void testResourcesListIncludesAudienceOnlyAnnotations() {
        moveToOperation();

        handler.handle(jsonRpcRequest(40, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "public-data");
        assertNotNull(resource);
        assertTrue(resource.containsKey("annotations"));
        JsonObject annotations = resource.getJsonObject("annotations");
        assertTrue(annotations.containsKey("audience"));
        JsonArray audience = annotations.getJsonArray("audience");
        assertEquals(1, audience.size());
        assertEquals("user", audience.getString(0));
        assertFalse(annotations.containsKey("priority"));
    }

    @Test
    public void testResourcesListIncludesPriorityOnlyAnnotations() {
        moveToOperation();

        handler.handle(jsonRpcRequest(41, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "priority-data");
        assertNotNull(resource);
        assertTrue(resource.containsKey("annotations"));
        JsonObject annotations = resource.getJsonObject("annotations");
        assertFalse(annotations.containsKey("audience"));
        assertTrue(annotations.containsKey("priority"));
        assertEquals(0.3, annotations.getJsonNumber("priority").doubleValue(), 0.001);
    }

    @Test
    public void testResourcesListAudienceIsArray() {
        moveToOperation();

        handler.handle(jsonRpcRequest(42, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "report");
        assertNotNull(resource);
        JsonObject annotations = resource.getJsonObject("annotations");
        assertEquals(jakarta.json.JsonValue.ValueType.ARRAY, annotations.get("audience").getValueType());
    }

    // ==================== Resources List — Field Absence ====================

    @Test
    public void testResourcesListOmitsTitleWhenNull() {
        moveToOperation();

        handler.handle(jsonRpcRequest(13, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "app-log");
        assertNotNull(resource);
        assertFalse(resource.containsKey("title"));
    }

    @Test
    public void testResourcesListOmitsSizeWhenNegative() {
        moveToOperation();

        handler.handle(jsonRpcRequest(14, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "app-log");
        assertNotNull(resource);
        assertFalse(resource.containsKey("size"));
    }

    @Test
    public void testResourcesListOmitsAnnotationsWhenNull() {
        moveToOperation();

        handler.handle(jsonRpcRequest(15, "resources/list"), connection, responder);

        JsonObject resource = findResourceByName(responder.lastResult().getJsonArray("resources"), "app-log");
        assertNotNull(resource);
        assertFalse(resource.containsKey("annotations"));
    }

    // ==================== Resource Templates List — Field Presence ====================

    @Test
    public void testResourceTemplatesListIncludesTitleWhenSet() {
        moveToOperation();

        handler.handle(jsonRpcRequest(20, "resources/templates/list"), connection, responder);

        JsonObject template = findTemplateByName(responder.lastResult().getJsonArray("resourceTemplates"), "db-table");
        assertNotNull(template);
        assertTrue(template.containsKey("title"));
        assertEquals("Database Table", template.getString("title"));
    }

    @Test
    public void testResourceTemplatesListIncludesAnnotations() {
        moveToOperation();

        handler.handle(jsonRpcRequest(21, "resources/templates/list"), connection, responder);

        JsonObject template = findTemplateByName(responder.lastResult().getJsonArray("resourceTemplates"), "db-table");
        assertNotNull(template);
        assertTrue(template.containsKey("annotations"));
        JsonObject annotations = template.getJsonObject("annotations");
        assertEquals("assistant", annotations.getJsonArray("audience").getString(0));
        assertEquals(0.9, annotations.getJsonNumber("priority").doubleValue(), 0.001);
    }

    // ==================== Resource Templates List — Field Absence ====================

    @Test
    public void testResourceTemplatesListOmitsTitleWhenNull() {
        moveToOperation();

        handler.handle(jsonRpcRequest(22, "resources/templates/list"), connection, responder);

        JsonObject template = findTemplateByName(responder.lastResult().getJsonArray("resourceTemplates"), "config-entry");
        assertNotNull(template);
        assertFalse(template.containsKey("title"));
    }

    @Test
    public void testResourceTemplatesListOmitsAnnotationsWhenNull() {
        moveToOperation();

        handler.handle(jsonRpcRequest(23, "resources/templates/list"), connection, responder);

        JsonObject template = findTemplateByName(responder.lastResult().getJsonArray("resourceTemplates"), "config-entry");
        assertNotNull(template);
        assertFalse(template.containsKey("annotations"));
    }

    // ==================== Subscription & Notification Tests ====================

    @Test
    public void testSubscribeThenNotifyDelivers() {
        ResourceMessageHandler resourceHandler = new ResourceMessageHandler(registry, getClass().getClassLoader(), null);
        TestResponderConnection subscriber = new TestResponderConnection("sub-conn-1");
        TestResponder subscribeResponder = new TestResponder();

        JsonObject subscribeMsg = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 30)
                .add("method", "resources/subscribe")
                .add("params", Json.createObjectBuilder()
                        .add("uri", "file:///data/report.csv"))
                .build();
        resourceHandler.resourcesSubscribe(subscribeMsg, subscribeResponder, subscriber);
        assertTrue(subscribeResponder.hasResult());

        resourceHandler.notifyResourceUpdated("file:///data/report.csv");

        assertEquals(1, subscriber.receivedNotifications().size());
        JsonObject notification = subscriber.receivedNotifications().get(0);
        assertEquals("notifications/resources/updated", notification.getString("method"));
        assertEquals("file:///data/report.csv",
                notification.getJsonObject("params").getString("uri"));
    }

    @Test
    public void testNotifyWithNoSubscribersIsNoOp() {
        ResourceMessageHandler resourceHandler = new ResourceMessageHandler(registry, getClass().getClassLoader(), null);
        resourceHandler.notifyResourceUpdated("file:///nonexistent");
    }

    @Test
    public void testUnsubscribeStopsNotifications() {
        ResourceMessageHandler resourceHandler = new ResourceMessageHandler(registry, getClass().getClassLoader(), null);
        TestResponderConnection subscriber = new TestResponderConnection("sub-conn-2");
        TestResponder subResponder = new TestResponder();

        // Subscribe
        JsonObject subscribeMsg = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 31)
                .add("method", "resources/subscribe")
                .add("params", Json.createObjectBuilder()
                        .add("uri", "file:///data/report.csv"))
                .build();
        resourceHandler.resourcesSubscribe(subscribeMsg, subResponder, subscriber);

        // Unsubscribe
        JsonObject unsubscribeMsg = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 32)
                .add("method", "resources/unsubscribe")
                .add("params", Json.createObjectBuilder()
                        .add("uri", "file:///data/report.csv"))
                .build();
        resourceHandler.resourcesUnsubscribe(unsubscribeMsg, subResponder, subscriber);

        // Notify — should not deliver
        resourceHandler.notifyResourceUpdated("file:///data/report.csv");

        assertTrue(subscriber.receivedNotifications().isEmpty());
    }

    // ==================== Helpers ====================

    private void moveToOperation() {
        MCPTestHelpers.moveToOperation(handler, connection, responder);
    }

    private static JsonObject findResourceByName(JsonArray resources, String name) {
        for (int i = 0; i < resources.size(); i++) {
            JsonObject resource = resources.getJsonObject(i);
            if (name.equals(resource.getString("name"))) {
                return resource;
            }
        }
        return null;
    }

    private static JsonObject findTemplateByName(JsonArray templates, String name) {
        for (int i = 0; i < templates.size(); i++) {
            JsonObject template = templates.getJsonObject(i);
            if (name.equals(template.getString("name"))) {
                return template;
            }
        }
        return null;
    }

    private static class TestResponderConnection extends TestMCPConnection implements Responder {

        private final List<JsonObject> notifications = new ArrayList<>();

        TestResponderConnection(String id) {
            super(id);
        }

        @Override
        public int lastEventId() {
            return 0;
        }

        @Override
        public void send(JsonObject message) {
            notifications.add(message);
        }

        List<JsonObject> receivedNotifications() {
            return notifications;
        }
    }
}
