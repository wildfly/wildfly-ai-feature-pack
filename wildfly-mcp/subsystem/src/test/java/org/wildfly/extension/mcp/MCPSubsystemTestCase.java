/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import java.util.EnumSet;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MCPSubsystemTestCase extends AbstractSubsystemSchemaTest<MCPSubsystemSchema> {

    private final MCPSubsystemSchema schema;

    @Parameters
    public static Iterable<MCPSubsystemSchema> parameters() {
        return EnumSet.allOf(MCPSubsystemSchema.class);
    }

    public MCPSubsystemTestCase(MCPSubsystemSchema schema) {
        super(MCPSubsystemRegistrar.NAME, new MCPExtension(), schema, MCPSubsystemSchema.CURRENT);
        this.schema = schema;
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.withCapabilities(Stability.EXPERIMENTAL);
    }

    @Override
    protected KernelServices standardSubsystemTest(String configId, String configIdResolvedModel, boolean compareXml, AdditionalInitialization additionalInit) throws Exception {
        KernelServices services = super.standardSubsystemTest(configId, configIdResolvedModel, compareXml, additionalInit);

        if (!this.schema.since(MCPSubsystemSchema.VERSION_2_0)) {
            ModelNode model = services.readWholeModel();
            ModelNode subsystem = model.get("subsystem", MCPSubsystemRegistrar.NAME);
            Assert.assertTrue("Subsystem resource should exist after parsing v1.0 XML", subsystem.isDefined());
            for (AttributeDefinition attr : MCPSubsystemRegistrar.ATTRIBUTES) {
                String name = attr.getName();
                if (!attr.isNullSignificant() && !attr.isRequired()) {
                    continue;
                }
                Assert.assertTrue("Attribute '" + name + "' should be defined on the subsystem resource after v1.0 XML parsing",
                        subsystem.hasDefined(name));
            }
            Assert.assertFalse("Legacy mcp-server child resource should not exist after v1.0 XML parsing",
                    subsystem.hasDefined("mcp-server"));
        }

        return services;
    }
}
