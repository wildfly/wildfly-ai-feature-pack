/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.elicitation;

import static org.wildfly.mcp.api._private.MCPApiLogger.ROOT_LOGGER;

final class RangeValidation {

    private RangeValidation() {
    }

    static <T extends Number & Comparable<T>> void validateMinMax(T min, T max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw ROOT_LOGGER.maxCanNotBeLessThanMin(max, min);
        }
    }
}
