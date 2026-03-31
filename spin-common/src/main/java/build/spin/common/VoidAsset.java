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
 * An implementation of an {@link Asset} to represent a {@code void} result.
 *
 * @author brian.oliver
 * @since Jan-2023
 */
public final class VoidAsset
    implements Asset<Object> {

    private final Invocable<?> invocable;

    /**
     * Constructs a {@link VoidAsset}.
     *
     * @param invocable the {@link Invocable} that produced the {@link Asset} value
     */
    VoidAsset(final Invocable<?> invocable) {
        this.invocable = Objects.requireNonNull(invocable, "The Invocable must not be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Invocable<Object> invocable() {
        return (Invocable<Object>) this.invocable;
    }

    @Override
    public Object get() {
        throw new UnsupportedOperationException(
            "Can't 'get' the value of a 'void' asset for " + this.invocable.getReference());
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final VoidAsset that = (VoidAsset) object;
        return Objects.equals(this.invocable.getReference(), that.invocable.getReference());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.invocable.getReference());
    }

    /**
     * Creates a new {@link Asset}.
     *
     * @param invocable the {@link Invocable} that produced the {@link Asset} value
     */
    public static VoidAsset create(final Invocable<?> invocable) {
        return new VoidAsset(invocable);
    }
}
