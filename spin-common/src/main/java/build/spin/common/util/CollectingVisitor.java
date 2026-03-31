package build.spin.common.util;

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

import build.spin.Visitor;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;

/**
 * A {@link Visitor} that collects visited {@link Object}s using a {@link Collector}.
 *
 * @param <T> the type of {@link Object}
 * @param <R> the type of the collected result
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public class CollectingVisitor<T, R>
    implements Visitor<T> {

    /**
     * The {@link BiConsumer} for accumulating {@link Object}s.
     */
    private final BiConsumer<Object, ? super T> accumulator;

    /**
     * The {@link Function} for finishing accumulation.
     */
    private final Function<Object, R> finisher;

    /**
     * The accumulation.
     */
    private final Object accumulation;

    /**
     * Constructs a {@link CollectingVisitor}.
     *
     * @param collector the {@link Collector}
     */
    @SuppressWarnings("unchecked")
    public CollectingVisitor(final Collector<? super T, ?, R> collector) {
        Objects.requireNonNull(collector, "The collector must not be null");
        this.accumulation = collector.supplier().get();
        this.accumulator = (BiConsumer<Object, ? super T>) collector.accumulator();
        this.finisher = (Function<Object, R>) collector.finisher();
    }

    @Override
    public void visit(final T object) {
        this.accumulator.accept(this.accumulation, object);
    }

    /**
     * Obtains the collected result.
     *
     * @return the collected result
     */
    public R collect() {
        return this.finisher.apply(this.accumulation);
    }
}
