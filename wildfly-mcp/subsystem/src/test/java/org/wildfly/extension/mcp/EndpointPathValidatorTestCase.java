/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

public class EndpointPathValidatorTestCase {

    private static final EndpointPathValidator VALIDATOR = EndpointPathValidator.INSTANCE;

    private static void assertValid(String value) {
        try {
            VALIDATOR.validateParameter("test-param", new ModelNode(value));
        } catch (OperationFailedException e) {
            fail("Expected '" + value + "' to be valid but got: " + e.getMessage());
        }
    }

    private static void assertInvalid(String value) {
        assertThrows(OperationFailedException.class,
                () -> VALIDATOR.validateParameter("test-param", new ModelNode(value)));
    }

    // ==================== Valid paths ====================

    @Test
    public void testLowercaseLetters() throws Exception {
        assertValid("sse");
    }

    @Test
    public void testUppercaseLetters() throws Exception {
        assertValid("SSE");
    }

    @Test
    public void testMixedCase() throws Exception {
        assertValid("McpEndpoint");
    }

    @Test
    public void testDigitsOnly() throws Exception {
        assertValid("123");
    }

    @Test
    public void testAlphanumeric() throws Exception {
        assertValid("sse2");
    }

    @Test
    public void testHyphen() throws Exception {
        assertValid("mcp-sse");
    }

    @Test
    public void testUnderscore() throws Exception {
        assertValid("mcp_sse");
    }

    @Test
    public void testHyphenAndUnderscore() throws Exception {
        assertValid("mcp-sse_path");
    }

    @Test
    public void testAllAllowedCharacters() throws Exception {
        assertValid("aZ0-_");
    }

    // ==================== Undefined / non-string values (should pass through) ====================

    @Test
    public void testUndefinedNodeIsAccepted() throws Exception {
        VALIDATOR.validateParameter("test-param", new ModelNode());
    }

    // ==================== Invalid paths ====================

    @Test
    public void testSpaceRejected() {
        assertInvalid("mcp sse");
    }

    @Test
    public void testSlashRejected() {
        assertInvalid("mcp/sse");
    }

    @Test
    public void testDotRejected() {
        assertInvalid("mcp.sse");
    }

    @Test
    public void testAtSignRejected() {
        assertInvalid("mcp@sse");
    }

    @Test
    public void testEmptyStringRejected() {
        assertInvalid("");
    }

    @Test
    public void testHashRejected() {
        assertInvalid("mcp#sse");
    }

    @Test
    public void testQuestionMarkRejected() {
        assertInvalid("mcp?sse");
    }

    @Test
    public void testPercentRejected() {
        assertInvalid("mcp%20sse");
    }
}
