/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressNotification;
import org.mcpjava.server.progress.ProgressToken;
import org.mcpjava.server.progress.ProgressTracker;
import org.wildfly.extension.mcp.api.MCPMethods;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.MCPFieldNames;

class ProgressImpl implements Progress {

    private final ProgressToken progressToken;
    private final Responder responder;

    ProgressImpl(ProgressToken progressToken, Responder responder) {
        this.progressToken = progressToken;
        this.responder = responder;
    }

    @Override
    public Optional<ProgressToken> token() {
        return Optional.ofNullable(progressToken);
    }

    @Override
    public ProgressNotification.Builder notificationBuilder() {
        return new NotificationBuilderImpl(progressToken, responder);
    }

    @Override
    public ProgressTracker.Builder trackerBuilder() {
        return new TrackerBuilderImpl(progressToken, responder);
    }

    private static void sendNotification(Responder responder, ProgressToken token,
            BigDecimal progress, BigDecimal total, String message) {
        if (token == null) {
            return;
        }
        JsonObjectBuilder params = Json.createObjectBuilder();
        if (token.type() == ProgressToken.Type.STRING) {
            params.add(MCPFieldNames.PROGRESS_TOKEN, token.asString());
        } else {
            params.add(MCPFieldNames.PROGRESS_TOKEN, token.asInteger().longValue());
        }
        params.add("progress", progress);
        if (total != null) {
            params.add("total", total);
        }
        if (message != null) {
            params.add("message", message);
        }
        responder.send(Messages.newNotification(MCPMethods.NOTIFICATIONS_PROGRESS, params));
    }

    static class NotificationBuilderImpl implements ProgressNotification.Builder {

        private final ProgressToken token;
        private final Responder responder;
        private BigDecimal progress = BigDecimal.ZERO;
        private BigDecimal total;
        private String message;
        private Map<String, Object> metadata = new HashMap<>();

        NotificationBuilderImpl(ProgressToken token, Responder responder) {
            this.token = token;
            this.responder = responder;
        }

        @Override
        public ProgressNotification.Builder setProgress(long progress) {
            this.progress = BigDecimal.valueOf(progress);
            return this;
        }

        @Override
        public ProgressNotification.Builder setProgress(double progress) {
            this.progress = BigDecimal.valueOf(progress);
            return this;
        }

        @Override
        public ProgressNotification.Builder setTotal(long total) {
            this.total = BigDecimal.valueOf(total);
            return this;
        }

        @Override
        public ProgressNotification.Builder setTotal(double total) {
            this.total = BigDecimal.valueOf(total);
            return this;
        }

        @Override
        public ProgressNotification.Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        @Override
        public ProgressNotification.Builder putMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        @Override
        public ProgressNotification.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        @Override
        public ProgressNotification build() {
            return new NotificationImpl(token, responder, progress, total, message, metadata);
        }
    }

    static class NotificationImpl implements ProgressNotification {

        private final ProgressToken token;
        private final Responder responder;
        private final BigDecimal progress;
        private final BigDecimal total;
        private final String message;
        private final Map<String, Object> metadata;

        NotificationImpl(ProgressToken token, Responder responder,
                BigDecimal progress, BigDecimal total, String message,
                Map<String, Object> metadata) {
            this.token = token;
            this.responder = responder;
            this.progress = progress;
            this.total = total;
            this.message = message;
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }

        @Override
        public ProgressToken token() {
            return token;
        }

        @Override
        public Optional<BigDecimal> total() {
            return Optional.ofNullable(total);
        }

        @Override
        public BigDecimal progress() {
            return progress;
        }

        @Override
        public Optional<String> message() {
            return Optional.ofNullable(message);
        }

        @Override
        public Map<String, Object> metadata() {
            return metadata;
        }

        @Override
        public void sendAndForget() {
            ProgressImpl.sendNotification(responder, token, progress, total, message);
        }

        @Override
        public <T> T send() {
            sendAndForget();
            return null;
        }
    }

    static class TrackerBuilderImpl implements ProgressTracker.Builder {

        private final ProgressToken token;
        private final Responder responder;
        private BigDecimal total;
        private BigDecimal defaultStep = BigDecimal.ONE;
        private Function<BigDecimal, String> messageBuilder;

        TrackerBuilderImpl(ProgressToken token, Responder responder) {
            this.token = token;
            this.responder = responder;
        }

        @Override
        public ProgressTracker.Builder setTotal(long total) {
            if (total <= 0) {
                throw new IllegalArgumentException("Total must be positive");
            }
            this.total = BigDecimal.valueOf(total);
            return this;
        }

        @Override
        public ProgressTracker.Builder setTotal(double total) {
            if (total <= 0) {
                throw new IllegalArgumentException("Total must be positive");
            }
            this.total = BigDecimal.valueOf(total);
            return this;
        }

        @Override
        public ProgressTracker.Builder setDefaultStep(long step) {
            this.defaultStep = BigDecimal.valueOf(step);
            return this;
        }

        @Override
        public ProgressTracker.Builder setDefaultStep(double step) {
            this.defaultStep = BigDecimal.valueOf(step);
            return this;
        }

        @Override
        public ProgressTracker.Builder setMessageBuilder(Function<BigDecimal, String> messageBuilder) {
            this.messageBuilder = messageBuilder;
            return this;
        }

        @Override
        public ProgressTracker build() {
            return new TrackerImpl(token, responder, total, defaultStep, messageBuilder);
        }
    }

    static class TrackerImpl implements ProgressTracker {

        private final ProgressToken token;
        private final Responder responder;
        private final BigDecimal total;
        private final BigDecimal step;
        private final Function<BigDecimal, String> messageBuilder;
        private final AtomicReference<BigDecimal> progress = new AtomicReference<>(BigDecimal.ZERO);

        TrackerImpl(ProgressToken token, Responder responder, BigDecimal total,
                BigDecimal step, Function<BigDecimal, String> messageBuilder) {
            this.token = token;
            this.responder = responder;
            this.total = total;
            this.step = step;
            this.messageBuilder = messageBuilder;
        }

        @Override
        public ProgressToken token() {
            return token;
        }

        @Override
        public void advanceAndForget(BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            BigDecimal current = progress.accumulateAndGet(amount, (prev, add) -> {
                BigDecimal result = prev.add(add);
                if (total != null && result.compareTo(total) > 0) {
                    throw new IllegalArgumentException("Progress " + result + " exceeds total " + total);
                }
                return result;
            });
            String message = messageBuilder != null ? messageBuilder.apply(current) : null;
            ProgressImpl.sendNotification(responder, token, current, total, message);
        }

        @Override
        public <T> T advance(BigDecimal amount) {
            advanceAndForget(amount);
            return null;
        }

        @Override
        public BigDecimal progress() {
            return progress.get();
        }

        @Override
        public Optional<BigDecimal> total() {
            return Optional.ofNullable(total);
        }

        @Override
        public BigDecimal step() {
            return step;
        }
    }
}
