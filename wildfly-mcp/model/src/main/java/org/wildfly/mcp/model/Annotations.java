/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public interface Annotations {

    Optional<Set<Role>> audience();

    OptionalDouble priority();

    Optional<Instant> lastModified();

    static Builder builder() {
        return new AnnotationsBuilder();
    }

    interface Builder {

        Builder setAudience(Role... roles);

        Builder setAudience(Set<Role> roles);

        Builder setPriority(double priority);

        Builder setLastModified(Instant lastModified);

        Annotations build();
    }

    final class AnnotationsBuilder implements Builder {

        private Set<Role> audience;
        private OptionalDouble priority = OptionalDouble.empty();
        private Instant lastModified;

        AnnotationsBuilder() {
        }

        @Override
        public Builder setAudience(Role... roles) {
            this.audience = EnumSet.noneOf(Role.class);
            Collections.addAll(this.audience, roles);
            return this;
        }

        @Override
        public Builder setAudience(Set<Role> roles) {
            this.audience = roles == null ? null : EnumSet.copyOf(roles);
            return this;
        }

        @Override
        public Builder setPriority(double priority) {
            this.priority = OptionalDouble.of(priority);
            return this;
        }

        @Override
        public Builder setLastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        @Override
        public Annotations build() {
            return new AnnotationsRecord(
                    audience == null ? Optional.empty() : Optional.of(Collections.unmodifiableSet(audience)),
                    priority,
                    Optional.ofNullable(lastModified));
        }
    }

    record AnnotationsRecord(Optional<Set<Role>> audience, OptionalDouble priority,
            Optional<Instant> lastModified) implements Annotations {
    }
}
