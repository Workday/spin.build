package build.spin.engine;

/*-
 * #%L
 * Spin Engine
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

import build.base.foundation.predicate.Predicates;
import build.spin.Cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A heap-based thread-safe {@link Cache}.
 *
 * @param <K> the key type
 * @param <V> the value type
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public class HeapBasedCache<K, V>
    implements Cache<K, V> {

    /**
     * The {@link Cache} entries.
     */
    private final ConcurrentHashMap<K, V> entries;

    /**
     * The {@link ReentrantLock}s for entries.
     */
    private final ConcurrentHashMap<K, ReentrantLock> locks;

    /**
     * Constructs a {@link HeapBasedCache}.
     */
    public HeapBasedCache() {
        this.entries = new ConcurrentHashMap<>();
        this.locks = new ConcurrentHashMap<>();
    }

    /**
     * Acquires the {@link ReentrantLock} for the specified key.
     * <p>
     * Should the {@link ReentrantLock} not exist for the key, a new one is established.
     *
     * @param key the non-{@code null} key
     * @return the {@link ReentrantLock}
     */
    private ReentrantLock acquire(final K key) {
        return this.locks.compute(key, (__, existing) -> existing == null ? new ReentrantLock() : existing);
    }

    /**
     * Releases and removes the {@link ReentrantLock} and value for the specified key.
     *
     * @param key the key
     * @param lock the existing lock
     */
    private void release(final K key, final ReentrantLock lock) {
        if (key != null && lock != null) {
            try {
                lock.lock();
                this.entries.remove(key);
                this.locks.remove(key);
            }
            finally {
                lock.unlock();
            }
        }
    }

    @Override
    public Optional<V> get(final K key) {
        return key == null
            ? Optional.empty()
            : Optional.ofNullable(this.entries.get(key));
    }

    @Override
    public Optional<V> put(final K key, final V value) {
        if (key == null) {
            return Optional.empty();
        }

        if (value == null) {
            return remove(key);
        }

        boolean stableLock;
        V previous = null;

        do {
            final ReentrantLock lock = acquire(key);
            try {
                lock.lock();

                // ensure the lock we acquired is the same as the lock that is present
                // (when it's not we need to retry this update)
                stableLock = lock == this.locks.get(key);

                if (stableLock) {
                    // update the cache
                    previous = this.entries.put(key, value);
                }
            }
            finally {
                lock.unlock();
            }
        } while (!stableLock);

        return Optional.ofNullable(previous);
    }

    @Override
    public Optional<V> remove(final K key) {
        return removeIf(key, Predicates.always());
    }

    @Override
    public Optional<V> removeIf(final K key, final Predicate<? super V> predicate) {
        if (key == null || predicate == null) {
            return Optional.empty();
        }

        while (true) {
            final ReentrantLock lock = acquire(key);
            try {
                lock.lock();

                // ensure the lock we acquired is the same as the lock that is present
                // (when it's not we need to retry)
                if (lock == this.locks.get(key)) {
                    final V existing = this.entries.get(key);

                    if (existing == null || predicate.test(existing)) {
                        release(key, lock);
                    }

                    return Optional.ofNullable(existing);
                }
            }
            finally {
                lock.unlock();
            }
        }
    }

    @Override
    public Optional<V> compute(final K key, final Function<V, V> function) {
        if (key == null) {
            return Optional.empty();
        }

        if (function == null) {
            return get(key);
        }

        while (true) {
            final ReentrantLock lock = acquire(key);
            try {
                lock.lock();

                // ensure the lock we acquired is the same as the lock that is present
                // (when it's not we need to retry)
                if (lock == this.locks.get(key)) {
                    final V existing = this.entries.get(key);
                    final V updated = function.apply(existing);

                    if ((existing != null && updated != null && !existing.equals(updated))
                        || (existing == null && updated != null)) {

                        // update the cache entry
                        this.entries.put(key, updated);
                    }

                    if (updated == null) {
                        release(key, lock);
                    }

                    return Optional.ofNullable(updated);
                }
            }
            finally {
                lock.unlock();
            }
        }
    }

    @Override
    public Optional<V> computeIfAbsent(final K key, final Supplier<V> supplier) {
        return compute(key, existing -> existing == null
            ? supplier.get()
            : existing);
    }

    @Override
    public Optional<V> computeIfPresent(final K key, final Function<V, V> function) {
        return compute(key, existing -> existing == null
            ? null
            : function.apply(existing));
    }

    @Override
    public Stream<K> keys() {
        return this.entries.keySet().stream();
    }

    @Override
    public Stream<V> values() {
        return this.entries.values().stream();
    }

    @Override
    public void forEach(final BiConsumer<? super K, ? super V> consumer) {
        if (consumer != null) {
            this.entries.forEach(consumer);
        }
    }

    @Override
    public void clear() {
        keys().toList().forEach(this::remove);
    }

    @Override
    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    @Override
    public int size() {
        return this.entries.size();
    }
}
