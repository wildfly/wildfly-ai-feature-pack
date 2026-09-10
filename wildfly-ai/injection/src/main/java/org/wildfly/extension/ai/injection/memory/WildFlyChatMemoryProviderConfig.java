/*
 * Copyright 2025 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 /*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.injection.memory;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import jakarta.enterprise.inject.Instance;
import jakarta.servlet.http.HttpSession;
import org.wildfly.extension.ai.injection.AILogger;

public class WildFlyChatMemoryProviderConfig {

    public static enum ChatMemoryType {
        MESSAGE, TOKEN
    }
    private static HuggingFaceTokenCountEstimator estimator = null;
    private int size;
    private boolean useHttpSession;
    private ChatMemoryType type;

    public ChatMemoryProvider createChatMemory(Instance<Object> lookup) {
        if (shouldUseHttpSession(lookup)) {
            AILogger.ROOT_LOGGER.debug("Using the HTTP session to store the chat memory");
            HttpSession session = lookup.select(jakarta.servlet.http.HttpSession.class).get();
            switch (this.type) {
                case TOKEN:
                    return memoryId -> TokenWindowChatMemory.builder().id(session.getId())
                            .maxTokens(size, estimator()).build();
                case MESSAGE:
                default:
                    return memoryId -> MessageWindowChatMemory.builder().id(session.getId())
                            .maxMessages(size).build();

            }
        }
        return memoryId -> MessageWindowChatMemory.builder().id(memoryId).maxMessages(size).build();
    }

    private TokenCountEstimator estimator() {
        if (estimator == null) {
            estimator = new HuggingFaceTokenCountEstimator();
        }
        return estimator;
    }

    private boolean shouldUseHttpSession(Instance<Object> lookup) {
        return this.useHttpSession && lookup.select(jakarta.servlet.http.HttpSession.class).isResolvable();
    }

    public WildFlyChatMemoryProviderConfig size(int size) {
        this.size = size;
        return this;
    }

    public WildFlyChatMemoryProviderConfig useHttpSession(boolean useHttpSession) {
        this.useHttpSession = useHttpSession;
        return this;
    }

    public WildFlyChatMemoryProviderConfig type(String type) {
        this.type = ChatMemoryType.valueOf(type);
        return this;
    }
}
