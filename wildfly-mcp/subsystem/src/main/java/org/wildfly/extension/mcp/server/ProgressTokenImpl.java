/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.Objects;

import org.mcp_java.server.progress.ProgressToken;

class ProgressTokenImpl implements ProgressToken {

    private final Type type;
    private final String stringValue;
    private final Number integerValue;

    ProgressTokenImpl(String value) {
        Objects.requireNonNull(value, "ProgressToken string value must not be null");
        this.type = Type.STRING;
        this.stringValue = value;
        this.integerValue = null;
    }

    ProgressTokenImpl(Long value) {
        Objects.requireNonNull(value, "ProgressToken string value must not be null");
        this.type = Type.INTEGER;
        this.stringValue = null;
        this.integerValue = value;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public Number asInteger() {
        if (type != Type.INTEGER) {
            throw new IllegalStateException("Token is not an integer");
        }
        return integerValue;
    }

    @Override
    public String asString() {
        return type == Type.STRING ? stringValue : integerValue.toString();
    }
}
