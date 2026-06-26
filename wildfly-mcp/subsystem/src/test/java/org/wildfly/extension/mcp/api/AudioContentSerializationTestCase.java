/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import jakarta.json.JsonObject;
import java.util.Base64;
import java.util.Collection;
import org.junit.Test;
import org.mcpjava.server.content.AudioContent;
import org.mcpjava.server.content.ContentBlock;

/**
 * Verifies that AudioContent is correctly passed through ContentMapper
 * and serialized to the MCP-compliant JSON structure.
 */
public class AudioContentSerializationTestCase {

    @Test
    public void testAudioContentPassesThroughContentMapper() {
        byte[] data = Base64.getDecoder().decode("dGVzdA==");
        AudioContent audio = AudioContent.of(data, "audio/wav");

        Collection<? extends ContentBlock> result = ContentMapper.processResultAsText(audio);

        assertNotNull(result);
        assertEquals(1, result.size());
        ContentBlock block = result.iterator().next();
        assertTrue("Expected AudioContent instance", block instanceof AudioContent);
        AudioContent resultAudio = (AudioContent) block;
        assertArrayEquals(data, resultAudio.data());
        assertEquals("audio/wav", resultAudio.mimeType());
    }

    @Test
    public void testAudioContentSerializesToCorrectMcpJson() {
        byte[] data = "hello".getBytes();
        AudioContent audio = AudioContent.of(data, "audio/mp3");

        JsonObject json = ContentMapper.contentBlockToJson(audio).build();

        assertEquals("audio", json.getString("type"));
        assertEquals(Base64.getEncoder().encodeToString(data), json.getString("data"));
        assertEquals("audio/mp3", json.getString("mimeType"));
        // annotations must not appear when empty
        assertFalse(json.containsKey("annotations"));
    }

    @Test
    public void testAudioContentTypeIsNotText() {
        byte[] data = Base64.getDecoder().decode("dGVzdA==");
        AudioContent audio = AudioContent.of(data, "audio/wav");

        JsonObject json = ContentMapper.contentBlockToJson(audio).build();

        assertFalse("audio content must not use type=text", "text".equals(json.getString("type")));
    }

    @Test
    public void testAudioContentViaContentMapperSerializesCorrectly() {
        byte[] data = Base64.getDecoder().decode("dGVzdA==");
        AudioContent audio = AudioContent.of(data, "audio/ogg");

        Collection<? extends ContentBlock> blocks = ContentMapper.processResultAsText(audio);
        ContentBlock block = blocks.iterator().next();
        JsonObject json = ContentMapper.contentBlockToJson(block).build();

        assertEquals("audio", json.getString("type"));
        assertEquals(Base64.getEncoder().encodeToString(data), json.getString("data"));
        assertEquals("audio/ogg", json.getString("mimeType"));
    }
}
