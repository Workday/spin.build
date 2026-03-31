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
import build.spin.AssetCache;
import build.spin.Invocable;
import build.spin.Reference;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A "near" {@link AssetCache}, consisting of a "front" and a "back" {@link AssetCache}.
 * <p>
 * Upon requesting an {@link Asset}, the "near" {@link AssetCache} will first attempt to obtain the {@link Asset} from
 * the "front" {@link AssetCache}.  Should that fail, it will attempt to obtain the {@link Asset} from the "back" cache.
 * <p>
 * Upon updating a {@link Asset}, only the "front" {@link AssetCache} will be updated.  No changes will be made to
 * the "back" {@link AssetCache}.
 *
 * @author brian.oliver
 * @since Jan-2021
 */
public class NearAssetCache
    implements AssetCache {

    /**
     * The "front" {@link AssetCache}.
     */
    private final AssetCache front;

    /**
     * The "back" {@link AssetCache}.
     */
    private final AssetCache back;

    /**
     * Constructs a {@link NearAssetCache} {@link AssetCache}.
     *
     * @param front the "front" {@link AssetCache}
     * @param back the "back" {@link AssetCache}
     */
    private NearAssetCache(final AssetCache front, final AssetCache back) {
        this.front = Objects.requireNonNull(front, "The front AssetCache must not be null");
        this.back = Objects.requireNonNull(back, "The back AssetCache must not be null");
    }

    @Override
    public <T> Optional<Asset<T>> get(final Reference reference) {
        final Optional<Asset<T>> front = this.front.get(reference);
        if (front.isPresent()) {
            return front;
        }
        return this.back.get(reference);
    }

    @Override
    public <T> Optional<Asset<T>> putIfAbsent(final Invocable<T> invocable, final T result) {
        return putIfAbsent(DefaultAsset.create(invocable, result));
    }

    @Override
    public Stream<Invocable<?>> invocables() {
        return Stream.concat(this.front.invocables(), this.back.invocables());
    }

    @Override
    public <T> Optional<Asset<T>> putIfAbsent(final Asset<T> asset) {
        return this.front.putIfAbsent(asset);
    }

    @Override
    public Stream<Asset<?>> assets() {
        return Stream.concat(this.front.assets(), this.back.assets());
    }

    /**
     * Constructs a {@link NearAssetCache} {@link AssetCache}.
     *
     * @param front the "front" {@link AssetCache}
     * @param back the "back" {@link AssetCache}
     * @return a new {@link NearAssetCache} {@link AssetCache}
     */
    public static AssetCache of(final AssetCache front, final AssetCache back) {
        return new NearAssetCache(front, back);
    }
}
