/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import java.util.List;
import java.util.stream.Stream;
import org.wildfly.mcp.api.completion.CompleteContext;
import org.mcpjava.server.completion.CompleteArg;
import org.mcpjava.server.completion.CompletePrompt;
import org.mcpjava.server.completion.CompleteResourceTemplate;

public class TestMCPCompletion {

    private static final List<String> CITIES = List.of("London", "Lisbon", "Lima", "Lyon", "Ljubljana");
    private static final List<String> PYTHON_VERSIONS = List.of("3.10", "3.11", "3.12");
    private static final List<String> PERL_VERSIONS = List.of("5.36", "5.38", "5.40");

    @CompletePrompt("greeting")
    List<String> completeGreetingName(@CompleteArg(name = "name") String value) {
        return Stream.of("WildFly", "World", "Developer", "User")
                .filter(s -> s.toLowerCase().startsWith(value.toLowerCase()))
                .toList();
    }

    @CompletePrompt("code-review")
    List<String> completeVersionWithContext(
            @CompleteArg(name = "version") String version,
            CompleteContext context) {
        String language = context.arguments().getOrDefault("language", "");
        return switch (language) {
            case "python" -> PYTHON_VERSIONS.stream()
                    .filter(v -> v.startsWith(version)).toList();
            case "perl" -> PERL_VERSIONS.stream()
                    .filter(v -> v.startsWith(version)).toList();
            default -> List.of();
        };
    }

    @CompleteResourceTemplate("test-weather")
    List<String> completeWeatherCity(@CompleteArg(name = "city") String value) {
        return CITIES.stream()
                .filter(c -> c.toLowerCase().startsWith(value.toLowerCase()))
                .toList();
    }
}