/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.memory;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.cdi.spi.RegisterAIService;

@RegisterAIService(
        chatModelName = "ollama",
        chatMemoryProviderName = "chat-memory"
)
public interface AssistantService {

    String chat(@UserMessage String message);
}
