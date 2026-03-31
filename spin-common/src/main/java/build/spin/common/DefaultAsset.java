package build.spin.common;

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

import build.spin.Asset;
import build.spin.Invocable;

import java.util.Objects;

/**
 * A default implementation of an {@link Asset}.
 *
 * @param <T> the type of the {@link Asset}
 * @author brian.oliver
 * @since Jan-2023
 */
public class DefaultAsset<T>
    implements Asset<T> {

    private final Invocable<T> invocable;

    private final T value;

    /**
     * Constructs a {@link DefaultAsset}.
     *
     * @param invocable the {@link Invocable} that produced the {@link Asset} value
     * @param value the value of the {@link Asset}
     */
    DefaultAsset(final Invocable<T> invocable,
                 final T value) {

        this.invocable = Objects.requireNonNull(invocable, "The Invocable must not be null");
        this.value = Objects.requireNonNull(value, "The value of an Asset must not be null");
    }

    @Override
    public Invocable<T> invocable() {
        return this.invocable;
    }

    @Override
    public T get() {
        return this.value;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DefaultAsset<?> that = (DefaultAsset<?>) object;
        return Objects.equals(this.invocable.getReference(), that.invocable.getReference())
            && Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.invocable.getReference(), this.value);
    }

    /**
     * Creates a new {@link Asset}.
     *
     * @param invocable the {@link Invocable} that produced the {@link Asset} value
     * @param value the value of the {@link Asset}
     */
    public static <T> DefaultAsset<T> create(final Invocable<T> invocable,
                                             final T value) {
        return new DefaultAsset<>(invocable, value);
    }
}
