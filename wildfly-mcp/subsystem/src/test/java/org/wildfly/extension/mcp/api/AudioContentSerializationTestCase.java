/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Collection;
import org.junit.Test;
import org.wildfly.mcp.model.content.AudioContent;
import org.wildfly.mcp.model.content.ContentBlock;

/**
 * Verifies that AudioContent is correctly passed through ContentMapper
 * and serialized to the MCP-compliant JSON structure.
 */
public class AudioContentSerializationTestCase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testAudioContentPassesThroughContentMapper() {
        AudioContent audio = AudioContent.of("dGVzdA==", "audio/wav");

        Collection<? extends ContentBlock> result = ContentMapper.processResultAsText(audio);

        assertNotNull(result);
        assertEquals(1, result.size());
        ContentBlock block = result.iterator().next();
        assertEquals("audio", block.type());
        AudioContent resultAudio = (AudioContent) block;
        assertEquals("dGVzdA==", resultAudio.data());
        assertEquals("audio/wav", resultAudio.mimeType());
    }

    @Test
    public void testAudioContentSerializesToCorrectMcpJson() throws Exception {
        AudioContent audio = AudioContent.of(Base64.getEncoder().encodeToString("hello".getBytes()), "audio/mp3");

        JsonObject json = serializeContentBlock(audio);

        assertEquals("audio", json.getString("type"));
        assertEquals(Base64.getEncoder().encodeToString("hello".getBytes()), json.getString("data"));
        assertEquals("audio/mp3", json.getString("mimeType"));
        // annotations must not appear when null
        assertFalse(json.containsKey("annotations"));
    }

    @Test
    public void testAudioContentTypeIsNotText() throws Exception {
        AudioContent audio = AudioContent.of("dGVzdA==", "audio/wav");

        JsonObject json = serializeContentBlock(audio);

        assertFalse("audio content must not use type=text", "text".equals(json.getString("type")));
    }

    @Test
    public void testAudioContentViaContentMapperSerializesCorrectly() throws Exception {
        AudioContent audio = AudioContent.of("dGVzdA==", "audio/ogg");

        Collection<? extends ContentBlock> blocks = ContentMapper.processResultAsText(audio);
        ContentBlock block = blocks.iterator().next();
        JsonObject json = serializeContentBlock(block);

        assertEquals("audio", json.getString("type"));
        assertEquals("dGVzdA==", json.getString("data"));
        assertEquals("audio/ogg", json.getString("mimeType"));
    }

    /** Mirrors the null-filtering serialization logic used in ToolMessageHandler. */
    private static JsonObject serializeContentBlock(ContentBlock block) throws Exception {
        try (StringWriter out = new StringWriter()) {
            MAPPER.writeValue(out, block);
            try (StringReader in = new StringReader(out.toString())) {
                JsonObject raw = Json.createReader(in).readObject();
                JsonObjectBuilder filtered = Json.createObjectBuilder();
                for (String key : raw.keySet()) {
                    if (!raw.isNull(key)) {
                        filtered.add(key, raw.get(key));
                    }
                }
                return filtered.build();
            }
        }
    }
}
