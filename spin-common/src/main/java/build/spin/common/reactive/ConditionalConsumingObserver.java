package build.spin.common.reactive;

/*-
 * #%L
 * Spin Common Library
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
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
 * #L%
 */

import build.base.flow.Subscriber;
import build.base.foundation.Mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Subscriber} that conditionally consumes items as they are observed, passing only those that
 * satisfy a supplied {@link Predicate} to the {@link Consumer}.
 *
 * @param <T> the type of item being observed
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class ConditionalConsumingObserver<T>
    implements Subscriber<T> {

    /**
     * The {@link Mapping}s from {@link Predicate}s to {@link Consumer}s.
     */
    private final List<Mapping<Predicate<? super T>, Consumer<? super T>>> mappings;

    /**
     * Constructs a {@link ConditionalConsumingObserver}.
     *
     * @param mappings the {@link Mapping}s from {@link Predicate}s to {@link Consumer}s
     */
    private ConditionalConsumingObserver(final Stream<Mapping<Predicate<? super T>, Consumer<? super T>>> mappings) {
        this.mappings = mappings == null
            ? new ArrayList<>()
            : mappings.collect(Collectors.toList());
    }

    @Override
    public void onNext(final T item) {
        for (Mapping<Predicate<? super T>, Consumer<? super T>> mapping : this.mappings) {
            if (mapping.key().test(item)) {
                mapping.value().accept(item);
            }
        }
    }

    /**
     * A builder for {@link ConditionalConsumingObserver}s.
     *
     * @param <T> the type of item being observed
     */
    public static class Builder<T> {

        /**
         * The {@link Consumer}s by {@link Predicate}.
         */
        private final LinkedHashMap<Predicate<? super T>, Consumer<? super T>> consumers;

        /**
         * Constructs a {@link Builder}.
         */
        private Builder() {
            this.consumers = new LinkedHashMap<>();
        }

        /**
         * Adds the specified {@link Consumer} to consume items when the provided {@link Predicate} is satisfied.
         *
         * @param predicate the {@link Predicate}
         * @param consumer the {@link Consumer}
         * @return this {@link Builder} to permit fluent-method invocation
         */
        public Builder<T> with(final Predicate<? super T> predicate,
                               final Consumer<? super T> consumer) {
            this.consumers.put(predicate, consumer);
            return this;
        }

        /**
         * Creates the {@link ConditionalConsumingObserver} given the provided {@link Consumer}s.
         *
         * @return a new {@link ConditionalConsumingObserver}
         */
        public ConditionalConsumingObserver<T> build() {
            return new ConditionalConsumingObserver<>(this.consumers.entrySet().stream()
                .map(Mapping::of));
        }

        /**
         * Creates a new {@link ConditionalConsumingObserver.Builder}.
         *
         * @param <T> the type of item being observed
         * @return a new {@link ConditionalConsumingObserver.Builder}
         */
        public static <T> Builder<T> create() {
            return new Builder<>();
        }
    }
}
