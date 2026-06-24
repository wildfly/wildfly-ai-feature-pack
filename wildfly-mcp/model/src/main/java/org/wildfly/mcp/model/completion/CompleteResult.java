/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.completion;

import java.util.List;

public record CompleteResult(Completion completion) {

    public record Completion(List<String> values, Integer total, Boolean hasMore) {
    }
}
