/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import java.lang.reflect.Type;

public record ArgumentMetadata (String name, String description, boolean required, Type type, String headerName) {

    public ArgumentMetadata(String name, String description, boolean required, Type type) {
        this(name, description, required, type, null);
    }

    public boolean isHeader() {
        return headerName != null;
    }
}
