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
import build.spin.Task;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The default implementation of an {@link AssetCache}.
 *
 * @author brian.oliver
 * @since Jan-2021
 */
public class DefaultAssetCache
    implements AssetCache {

    /**
     * The thread-safe cache of {@link Asset}s by {@link Task} {@link Reference}.
     */
    private final ConcurrentHashMap<Reference, Asset<?>> cache;

    /**
     * Constructs a {@link DefaultAssetCache} {@link AssetCache}.
     */
    DefaultAssetCache() {
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<Asset<T>> get(final Reference reference) {
        return Optional.ofNullable((Asset<T>) this.cache.get(reference));
    }

    @Override
    public <T> Optional<Asset<T>> putIfAbsent(final Invocable<T> invocable, final T result) {
        return putIfAbsent(DefaultAsset.create(invocable, result));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<Asset<T>> putIfAbsent(final Asset<T> asset) {
        return Optional.ofNullable((Asset<T>) this.cache.putIfAbsent(asset.invocable().getReference(), asset));
    }

    @Override
    public Stream<Invocable<?>> invocables() {
        return this.cache.values()
            .stream()
            .map(Asset::invocable);
    }

    @Override
    public Stream<Asset<?>> assets() {
        return this.cache.values().stream();
    }

    /**
     * Creates a new {@link DefaultAssetCache}.
     *
     * @return a new {@link DefaultAssetCache}
     */
    public static DefaultAssetCache create() {
        return new DefaultAssetCache();
    }
}
