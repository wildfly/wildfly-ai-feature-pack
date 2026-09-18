/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration test that runs the {@code @modelcontextprotocol/conformance}
 * test suite against the WildFly MCP server.
 *
 * <p>The WildFly server is provisioned, started, and the WAR deployed by the
 * {@code wildfly-maven-plugin} during Maven's pre-integration-test phase.
 * This test class is executed during the integration-test phase by
 * {@code maven-failsafe-plugin}.</p>
 *
 * <p>Runs two conformance passes: one for spec version {@code 2025-11-25}
 * and one for {@code 2026-07-28}, each with {@code --suite all}.
 * Per-version expected-failure baselines are loaded from
 * {@code conformance-baseline-<version>.yml}.</p>
 *
 * <p>Requires {@code npx} (Node.js) to be available on the PATH.</p>
 */
public class MCPConformanceIT {

    private static final long PROCESS_TIMEOUT_MINUTES = 15;
    private static final String CONFORMANCE_PACKAGE_PREFIX = "@modelcontextprotocol/conformance@";

    private static String serverUrl;
    private static String baselineDir;
    private static String scenario;
    private static String conformancePackage;

    @BeforeAll
    static void setUp() {
        serverUrl = System.getProperty("mcp.server.url", "http://localhost:8080/mcp/stream");
        baselineDir = System.getProperty("conformance.baseline.dir",
                System.getProperty("project.basedir", ".") + "/src/test/resources");
        scenario = System.getProperty("conformance.scenario");
        conformancePackage = CONFORMANCE_PACKAGE_PREFIX
                + System.getProperty("conformance.package.version", "0.2.0-alpha.11");
    }

    @Test
    void conformance_2025_11_25() throws Exception {
        runConformance("2025-11-25");
    }

    @Test
    void conformance_2026_07_28() throws Exception {
        runConformance("2026-07-28");
    }

    private void runConformance(String specVersion) throws Exception {
        assumeTrue(isNpxAvailable(), "npx is not available on the PATH; skipping conformance tests");

        List<String> command = buildCommand(specVersion);
        System.out.println("Running MCP conformance suite (spec " + specVersion + "):");
        System.out.println("  Command: " + String.join(" ", command));
        System.out.println("  Server URL: " + serverUrl);
        System.out.println();

        String outputFileName = "conformance-output-" + specVersion + ".txt";
        Path outputFile = Path.of(System.getProperty("project.build.directory", "target"), outputFileName);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("project.basedir", ".")));
        pb.redirectErrorStream(true);
        pb.environment().put("NO_COLOR", "1");
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        Thread drainThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    System.out.println(line);
                }
            } catch (IOException e) {
                output.append("Error reading process output: ").append(e.getMessage());
            }
        }, "conformance-output-drain-" + specVersion);
        drainThread.setDaemon(true);
        drainThread.start();

        boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
        }
        drainThread.join(30_000);

        Files.writeString(outputFile, output.toString());
        System.out.println();
        System.out.println("Conformance output (" + specVersion + ") saved to: " + outputFile.toAbsolutePath());

        if (!finished) {
            throw new AssertionError("Conformance suite (spec " + specVersion + ") timed out after "
                    + PROCESS_TIMEOUT_MINUTES + " minutes. Output saved to: " + outputFile.toAbsolutePath());
        }

        int exitCode = process.exitValue();
        assertEquals(0, exitCode,
                "MCP conformance suite (spec " + specVersion + ") failed (exit code " + exitCode + "). "
                        + "Review output at: " + outputFile.toAbsolutePath() + ". "
                        + "To baseline expected failures, add them to conformance-baseline-" + specVersion + ".yml.");
    }

    private List<String> buildCommand(String specVersion) {
        List<String> cmd = new ArrayList<>();
        cmd.add("npx");
        cmd.add("--yes");
        cmd.add(conformancePackage);
        cmd.add("server");
        cmd.add("--url");
        cmd.add(serverUrl);
        cmd.add("--spec-version");
        cmd.add(specVersion);

        if (scenario != null && !scenario.isBlank()) {
            cmd.add("--scenario");
            cmd.add(scenario);
        } else {
            cmd.add("--suite");
            cmd.add("all");
        }

        cmd.add("--verbose");

        String baselinePath = baselineDir + "/conformance-baseline-" + specVersion + ".yml";
        if (hasExpectedFailures(baselinePath)) {
            cmd.add("--expected-failures");
            cmd.add(baselinePath);
        }

        return cmd;
    }

    private static boolean hasExpectedFailures(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                return false;
            }
            for (String line : Files.readAllLines(file.toPath())) {
                if (line.matches("^\\s*-\\s+\\S.*")) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("WARNING: failed to read conformance baseline " + path
                    + "; --expected-failures will be omitted: " + e.getMessage());
        }
        return false;
    }

    private static boolean isNpxAvailable() {
        try {
            Process p = new ProcessBuilder("npx", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
