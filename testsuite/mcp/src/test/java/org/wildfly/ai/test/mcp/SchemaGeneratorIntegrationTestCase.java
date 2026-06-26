/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@code @InputSchema} and {@code @OutputSchema} annotations.
 */
public class SchemaGeneratorIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-schema-generator.war")
                .addClass(TestMCPSchemaGeneratorTool.class)
                .addClass(TestMCPSchemaGeneratorTool.SearchResult.class)
                .addClass(TestMCPSchemaGeneratorTool.AddResult.class)
                .addClass(TestMCPSchemaGeneratorTool.SearchInputSchemaGenerator.class)
                .addClass(TestMCPSchemaGeneratorTool.SearchOutputSchemaGenerator.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void testInputSchemaFromGenerator() throws Exception {
        JsonObject tool = findToolByName("search");
        assertThat(tool).as("search tool should be present").isNotNull();

        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        assertThat(inputSchema).as("inputSchema should be present").isNotNull();
        assertThat(inputSchema.getString("type")).as("type should be object").isEqualTo("object");
        assertThat(inputSchema.getJsonObject("properties").containsKey("query"))
                .as("Should have 'query' property from generator").isTrue();
        assertThat(inputSchema.getJsonObject("properties").containsKey("limit"))
                .as("Should have 'limit' property from generator").isTrue();
    }

    @Test
    public void testOutputSchemaFromGenerator() throws Exception {
        JsonObject tool = findToolByName("search");
        assertThat(tool).as("search tool should be present").isNotNull();

        JsonObject outputSchema = tool.getJsonObject("outputSchema");
        assertThat(outputSchema).as("outputSchema should be present").isNotNull();
        assertThat(outputSchema.getString("type")).as("type should be object").isEqualTo("object");
        assertThat(outputSchema.getJsonObject("properties").containsKey("count"))
                .as("Should have 'count' property from generator").isTrue();
        assertThat(outputSchema.getJsonObject("properties").containsKey("items"))
                .as("Should have 'items' property from generator").isTrue();
    }

    @Test
    public void testOutputSchemaFromClass() throws Exception {
        JsonObject tool = findToolByName("add-from");
        assertThat(tool).as("add-from tool should be present").isNotNull();

        JsonObject outputSchema = tool.getJsonObject("outputSchema");
        assertThat(outputSchema).as("outputSchema should be present").isNotNull();
        assertThat(outputSchema.getString("type")).as("type should be object").isEqualTo("object");
        assertThat(outputSchema.getJsonObject("properties").containsKey("sum"))
                .as("Should have 'sum' property from AddResult class").isTrue();
        assertThat(outputSchema.getJsonObject("properties").containsKey("expression"))
                .as("Should have 'expression' property from AddResult class").isTrue();
    }

    private JsonObject findToolByName(String name) throws Exception {
        String response = sendAndReceive("tools/list", null);
        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        JsonArray tools = json.getJsonObject("result").getJsonArray("tools");
        for (int i = 0; i < tools.size(); i++) {
            if (name.equals(tools.getJsonObject(i).getString("name"))) {
                return tools.getJsonObject(i);
            }
        }
        return null;
    }
}
