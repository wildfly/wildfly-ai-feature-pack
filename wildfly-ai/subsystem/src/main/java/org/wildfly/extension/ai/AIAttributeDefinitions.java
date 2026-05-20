/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai;


import static org.wildfly.extension.ai.Capabilities.MANAGED_EXECUTOR_CAPABILITY_NAME;

import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author Emmanuel Hugonnet (c) 2024 Red Hat, Inc.
 */
public class AIAttributeDefinitions {

    public static final SimpleAttributeDefinition API_KEY = new SimpleAttributeDefinitionBuilder("api-key", ModelType.STRING, false)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition BASE_URL = new SimpleAttributeDefinitionBuilder("base-url", ModelType.STRING, false)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition BOLT_URL = new SimpleAttributeDefinitionBuilder("bolt-url", ModelType.STRING, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition CONNECT_TIMEOUT = new SimpleAttributeDefinitionBuilder("connect-timeout", ModelType.LONG, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.ZERO)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(true, true)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition EXECUTOR_SERVICE = new SimpleAttributeDefinitionBuilder("executor-service", ModelType.STRING, false)
            .setCapabilityReference(MANAGED_EXECUTOR_CAPABILITY_NAME)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition FREQUENCY_PENALTY = new SimpleAttributeDefinitionBuilder("frequency-penalty", ModelType.DOUBLE, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition LOG_REQUESTS = SimpleAttributeDefinitionBuilder.create("log-requests", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition LOG_REQUESTS_RESPONSES = SimpleAttributeDefinitionBuilder.create("log-requests-responses", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition LOG_RESPONSES = SimpleAttributeDefinitionBuilder.create("log-responses", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MAX_RETRIES = SimpleAttributeDefinitionBuilder.create("max-retries", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MAX_TOKEN = new SimpleAttributeDefinitionBuilder("max-token", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(1000))
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MODEL_NAME = new SimpleAttributeDefinitionBuilder("model-name", ModelType.STRING, false)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition PRESENCE_PENALTY = new SimpleAttributeDefinitionBuilder("presence-penalty", ModelType.DOUBLE, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition RESPONSE_FORMAT = new SimpleAttributeDefinitionBuilder("response-format", ModelType.STRING, true)
            .setValidator(EnumValidator.create(ResponseFormat.class))
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition SEED = new SimpleAttributeDefinitionBuilder("seed", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition SSL_ENABLED = SimpleAttributeDefinitionBuilder.create("ssl-enabled", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final StringListAttributeDefinition STOP_SEQUENCES = StringListAttributeDefinition.Builder.of("stop-sequences")
            .setRequired(false)
            .setMinSize(0)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition STREAMING = new SimpleAttributeDefinitionBuilder("streaming", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition TEMPERATURE = new SimpleAttributeDefinitionBuilder("temperature", ModelType.DOUBLE, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition TOP_P = new SimpleAttributeDefinitionBuilder("top-p", ModelType.DOUBLE, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition USER_MESSAGE = new SimpleAttributeDefinitionBuilder("user-message", ModelType.STRING, false)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition USERNAME = new SimpleAttributeDefinitionBuilder("username", ModelType.STRING, false)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();

    public enum ResponseFormat {
        JSON, TEXT;

        public static boolean isJson(String format) {
            return format != null && JSON == valueOf(format);
        }
    }
}
