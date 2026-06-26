/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.container;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.util.logging.Logger;

/**
 * Registered as a JUnit Platform service-loader extension so that JUnit loads this class
 * before any tests run. Loading it triggers the {@link LgtmContainerManager} static block,
 * which starts (or detects) the LGTM stack early enough that the OTel endpoint is already
 * available when Arquillian launches the WildFly server.
 */
public class LgtmContainerInitializer implements TestExecutionListener {

    private static final Logger LOG = Logger.getLogger(LgtmContainerInitializer.class.getName());

    static {
        // Trigger LgtmContainerManager class loading (and its static initializer)
        // during ServiceLoader discovery — early enough that system properties like
        // lgtm.otlp.endpoint are set before Arquillian resolves arquillian.xml.
        LgtmContainerManager.isAvailable();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (LgtmContainerManager.isAvailable()) {
            LOG.info("=================================================");
            LOG.info("LGTM observability stack initialized:");
            LOG.info("  Grafana:    " + LgtmContainerManager.getGrafanaUrl());
            LOG.info("  OTLP gRPC:  " + LgtmContainerManager.getOtlpGrpcEndpoint());
            LOG.info("  OTLP HTTP:  " + LgtmContainerManager.getOtlpHttpEndpoint());
            LOG.info("  Prometheus: " + LgtmContainerManager.getPrometheusUrl());
            LOG.info("=================================================");
        } else {
            LOG.info("=================================================");
            LOG.info("LGTM not available - OpenTelemetry tests disabled");
            LOG.info("To enable: Start Grafana locally on port 3000");
            LOG.info("  or ensure Docker is available");
            LOG.info("=================================================");
        }
    }
}
