/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;


/**
 * Enumeration of MCP Server subsystem schema versions.
 */
enum MCPSubsystemSchema implements SubsystemResourceXMLSchema<MCPSubsystemSchema> {
    VERSION_1_0(1, 0),;
    static final MCPSubsystemSchema CURRENT = VERSION_1_0;

    private final VersionedNamespace<IntVersion, MCPSubsystemSchema> namespace;
    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);

    MCPSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(MCPSubsystemRegistrar.NAME, Stability.EXPERIMENTAL, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, MCPSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        return this.factory.subsystemElement(MCPSubsystemRegistrar.REGISTRATION)
                .withContent(this.factory.choice().withCardinality(XMLCardinality.Single.OPTIONAL)
                        .addElement(this.factory.namedElement(MCPEndpointConfigurationProviderRegistrar.REGISTRATION)
                                .addAttributes(MCPEndpointConfigurationProviderRegistrar.ATTRIBUTES).build())
                        .build()
                )
                .build();
    }
}
