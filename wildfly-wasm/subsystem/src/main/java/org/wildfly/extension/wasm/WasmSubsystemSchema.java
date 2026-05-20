/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.wasm;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;

/**
 * Enumeration of Wasm subsystem schema versions.
 */
enum WasmSubsystemSchema implements SubsystemResourceXMLSchema<WasmSubsystemSchema> {
    VERSION_1_0(1, 0),;
    static final WasmSubsystemSchema CURRENT = VERSION_1_0;

    private final VersionedNamespace<IntVersion, WasmSubsystemSchema> namespace;
    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);

    WasmSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(WasmSubsystemRegistrar.NAME, Stability.EXPERIMENTAL, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, WasmSubsystemSchema> getNamespace() {
        return this.namespace;
    }
    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        return this.factory.subsystemElement(WasmSubsystemRegistrar.REGISTRATION)
                .withContent(this.factory.choice().withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                        .addElement(this.factory.namedElement(WasmProviderRegistrar.REGISTRATION)
                                .addAttributes(WasmProviderRegistrar.ATTRIBUTES).build())
                        .build()
                )
                .build();
    }
}
