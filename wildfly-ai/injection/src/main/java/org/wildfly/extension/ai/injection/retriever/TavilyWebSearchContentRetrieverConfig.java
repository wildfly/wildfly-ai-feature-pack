/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.injection.retriever;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import jakarta.enterprise.inject.Instance;
import java.time.Duration;
import java.util.List;

public class TavilyWebSearchContentRetrieverConfig implements WildFlyContentRetrieverConfig {

    private String apiKey;
    private String baseUrl;
    private List<String> excludeDomains;
    private Boolean includeAnswer;
    private List<String> includeDomains;
    private Boolean includeRawContent;
    private String searchDepth;
    private Duration timeout;
    private Integer maxResults;

    @Override
    public ContentRetriever createContentRetriever(Instance<Object> lookup) {
        WebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .excludeDomains(excludeDomains)
                .includeAnswer(includeAnswer)
                .includeDomains(includeDomains)
                .includeRawContent(includeRawContent)
                .searchDepth(searchDepth)
                .timeout(timeout)
                .build();
        return new WebSearchContentRetriever(engine, maxResults);
    }

    public TavilyWebSearchContentRetrieverConfig apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;

    }

    public TavilyWebSearchContentRetrieverConfig baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public TavilyWebSearchContentRetrieverConfig excludeDomains(List<String> excludeDomains) {
        this.excludeDomains = excludeDomains;
        return this;
    }

    public TavilyWebSearchContentRetrieverConfig includeAnswer(Boolean includeAnswer) {
        this.includeAnswer = includeAnswer;
        return this;
    }

    public TavilyWebSearchContentRetrieverConfig includeDomains(List<String> includeDomains) {
        this.includeDomains = includeDomains;
        return this;
    }

    public TavilyWebSearchContentRetrieverConfig includeRawContent(Boolean includeRawContent) {
        this.includeRawContent = includeRawContent;
        return this;
    }

    public TavilyWebSearchContentRetrieverConfig searchDepth(String searchDepth) {
        this.searchDepth = searchDepth;
        return this;
    }

    public TavilyWebSearchContentRetrieverConfig timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public TavilyWebSearchContentRetrieverConfig maxResults(Integer maxResults) {
        this.maxResults = maxResults;
        return this;
    }

}
