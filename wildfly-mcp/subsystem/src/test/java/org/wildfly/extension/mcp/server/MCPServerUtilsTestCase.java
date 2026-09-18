/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MCPServerUtilsTestCase {

    // ==================== extractHostName ====================

    @Test
    void extractHostName_plainHostname() {
        assertEquals("example.com", MCPServerUtils.extractHostName("example.com"));
    }

    @Test
    void extractHostName_hostnameWithPort() {
        assertEquals("example.com", MCPServerUtils.extractHostName("example.com:8080"));
    }

    @Test
    void extractHostName_ipv4() {
        assertEquals("192.168.1.1", MCPServerUtils.extractHostName("192.168.1.1"));
    }

    @Test
    void extractHostName_ipv4WithPort() {
        assertEquals("192.168.1.1", MCPServerUtils.extractHostName("192.168.1.1:8080"));
    }

    @Test
    void extractHostName_bracketedIpv6WithPort() {
        assertEquals("::1", MCPServerUtils.extractHostName("[::1]:8080"));
    }

    @Test
    void extractHostName_bracketedIpv6Alone() {
        assertEquals("::1", MCPServerUtils.extractHostName("[::1]"));
    }

    @Test
    void extractHostName_bracketedFullIpv6WithPort() {
        assertEquals("2001:db8::1", MCPServerUtils.extractHostName("[2001:db8::1]:443"));
    }

    @Test
    void extractHostName_localhost() {
        assertEquals("localhost", MCPServerUtils.extractHostName("localhost"));
    }

    @Test
    void extractHostName_localhostWithPort() {
        assertEquals("localhost", MCPServerUtils.extractHostName("localhost:9990"));
    }

    // ==================== isLocalhostAddress ====================

    @Test
    void isLocalhostAddress_localhost() {
        assertTrue(MCPServerUtils.isLocalhostAddress("localhost"));
    }

    @Test
    void isLocalhostAddress_localhostUpperCase() {
        assertTrue(MCPServerUtils.isLocalhostAddress("LOCALHOST"));
    }

    @Test
    void isLocalhostAddress_ipv4Loopback() {
        assertTrue(MCPServerUtils.isLocalhostAddress("127.0.0.1"));
    }

    @Test
    void isLocalhostAddress_ipv6Loopback() {
        assertTrue(MCPServerUtils.isLocalhostAddress("::1"));
    }

    @Test
    void isLocalhostAddress_bracketedIpv6Loopback() {
        assertTrue(MCPServerUtils.isLocalhostAddress("[::1]"));
    }

    @Test
    void isLocalhostAddress_externalHost() {
        assertFalse(MCPServerUtils.isLocalhostAddress("evil.example.com"));
    }

    @Test
    void isLocalhostAddress_externalIp() {
        assertFalse(MCPServerUtils.isLocalhostAddress("192.168.1.1"));
    }

    // ==================== isAllowedHost (no allowedOrigins) ====================

    @Test
    void isAllowedHost_localhost() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080);
        assertTrue(MCPServerUtils.isAllowedHost("localhost", dest));
    }

    @Test
    void isAllowedHost_ipv4Loopback() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080);
        assertTrue(MCPServerUtils.isAllowedHost("127.0.0.1", dest));
    }

    @Test
    void isAllowedHost_ipv6Loopback() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("::1"), 8080);
        assertTrue(MCPServerUtils.isAllowedHost("[::1]", dest));
    }

    @Test
    void isAllowedHost_matchingDestIp() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("10.0.0.5"), 8080);
        assertTrue(MCPServerUtils.isAllowedHost("10.0.0.5", dest));
    }

    @Test
    void isAllowedHost_matchingDestHostname() {
        InetSocketAddress dest = InetSocketAddress.createUnresolved("myserver.local", 8080);
        assertTrue(MCPServerUtils.isAllowedHost("myserver.local", dest));
    }

    @Test
    void isAllowedHost_matchingDestHostnameCaseInsensitive() {
        InetSocketAddress dest = InetSocketAddress.createUnresolved("MyServer.Local", 8080);
        assertTrue(MCPServerUtils.isAllowedHost("myserver.local", dest));
    }

    @Test
    void isAllowedHost_evilHostRejected() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080);
        assertFalse(MCPServerUtils.isAllowedHost("evil.example.com", dest));
    }

    @Test
    void isAllowedHost_mismatchedIpRejected() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("10.0.0.5"), 8080);
        assertFalse(MCPServerUtils.isAllowedHost("10.0.0.99", dest));
    }

    // ==================== isAllowedHost (with allowedOrigins) ====================

    @Test
    void isAllowedHost_configuredOriginAllowed() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("10.0.0.5"), 8080);
        Set<String> allowed = Set.of("myapp.example.com", "other.example.com");
        assertTrue(MCPServerUtils.isAllowedHost("myapp.example.com", dest, allowed));
    }

    @Test
    void isAllowedHost_configuredOriginCaseInsensitive() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("10.0.0.5"), 8080);
        Set<String> allowed = Set.of("MyApp.Example.COM");
        assertTrue(MCPServerUtils.isAllowedHost("myapp.example.com", dest, allowed));
    }

    @Test
    void isAllowedHost_unknownHostNotInAllowedOrigins() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("10.0.0.5"), 8080);
        Set<String> allowed = Set.of("myapp.example.com");
        assertFalse(MCPServerUtils.isAllowedHost("evil.example.com", dest, allowed));
    }

    @Test
    void isAllowedHost_emptyAllowedOriginsFallsBackToDefaults() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080);
        assertTrue(MCPServerUtils.isAllowedHost("localhost", dest, Collections.emptySet()));
    }

    @Test
    void isAllowedHost_localhostStillAllowedWithConfiguredOrigins() throws Exception {
        InetSocketAddress dest = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080);
        Set<String> allowed = Set.of("myapp.example.com");
        assertTrue(MCPServerUtils.isAllowedHost("localhost", dest, allowed));
    }

    // ==================== extractHostPort ====================

    @Test
    void extractHostPort_noPort() {
        assertEquals(-1, MCPServerUtils.extractHostPort("localhost"));
    }

    @Test
    void extractHostPort_withPort() {
        assertEquals(8080, MCPServerUtils.extractHostPort("localhost:8080"));
    }

    @Test
    void extractHostPort_ipv6WithPort() {
        assertEquals(443, MCPServerUtils.extractHostPort("[::1]:443"));
    }

    @Test
    void extractHostPort_ipv6NoPort() {
        assertEquals(-1, MCPServerUtils.extractHostPort("[::1]"));
    }

    // ==================== effectivePort ====================

    @Test
    void effectivePort_explicitPort() {
        assertEquals(3000, MCPServerUtils.effectivePort(3000, "http"));
    }

    @Test
    void effectivePort_httpDefault() {
        assertEquals(80, MCPServerUtils.effectivePort(-1, "http"));
    }

    @Test
    void effectivePort_httpsDefault() {
        assertEquals(443, MCPServerUtils.effectivePort(-1, "https"));
    }

    @Test
    void effectivePort_nullScheme() {
        assertEquals(80, MCPServerUtils.effectivePort(-1, null));
    }
}
