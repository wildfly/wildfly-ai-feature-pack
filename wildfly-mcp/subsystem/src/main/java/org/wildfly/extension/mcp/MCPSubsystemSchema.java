/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.MESSAGES_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.PAGE_SIZE;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.SSE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.STREAMABLE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.TIMEOUT;

import java.util.List;
import java.util.Set;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
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
    VERSION_1_0(1, 0),
    VERSION_2_0(2, 0),;
    static final MCPSubsystemSchema CURRENT = VERSION_2_0;

    private static final ResourceRegistration LEGACY_MCP_SERVER = ResourceRegistration.of(PathElement.pathElement("mcp-server"));

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
        SubsystemResourceRegistrationXMLElement.Builder builder = this.factory.subsystemElement(MCPSubsystemRegistrar.REGISTRATION);
        if (this.since(VERSION_2_0)) {
            builder.addAttribute(MESSAGES_PATH)
                   .addAttribute(SSE_PATH)
                   .addAttribute(STREAMABLE_PATH)
                   .addAttribute(PAGE_SIZE)
                   .addAttribute(TIMEOUT);
        } else {
            builder.withContent(this.factory.sequence()
                    .withCardinality(XMLCardinality.Single.OPTIONAL)
                    .addElement(this.factory.element(this.factory.resolve("mcp-server"))
                            .addAttributes(List.of(MESSAGES_PATH, SSE_PATH, STREAMABLE_PATH, PAGE_SIZE, TIMEOUT))
                            .ignoreAttributeLocalNames(Set.of("name"))
                            .build())
                    .build());
        }
        return builder.build();
    }
}
