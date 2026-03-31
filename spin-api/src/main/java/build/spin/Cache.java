package build.spin;

/*-
 * #%L
 * Spin API
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

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A {@link Map}-like data-structure supporting full concurrency of retrievals and high expected concurrency for updates.
 * <p>
 * While all operations are thread-safe, retrieval operations do not entail locking, and there is no support for locking
 * an entire {@link Cache} in a way that prevents all access.
 * <p>
 * Retrieval operations (including {@link #get(Object)}) do not block, so may overlap with update operations
 * (including put and remove). Retrievals reflect the results of the most recently completed update operations holding
 * upon their onset. (More formally, an update operation for a given key bears a happens-before relation with any
 * (non-null) retrieval for that key reporting the updated value.) For aggregate operations such as {@link #clear()} and
 * {@link #forEach(BiConsumer)}, concurrent retrievals may reflect insertion or removal of only some entries. Similarly,
 * {@link Stream}s will reflecting the state of a {@link Cache} at some point at or since their creation. They do not
 * throw {@code ConcurrentModificationException}. However, {@link Stream}s are designed to be used by only one thread
 * at a time. Bear in mind that the results of aggregate status methods including {@link #size} and {@link #isEmpty()},
 * are typically useful only when a {@link Cache} is not undergoing concurrent updates in other threads. Otherwise the
 * results of these methods reflect transient states that may be adequate for monitoring or estimation purposes,
 * but not for program control.
 * <p>
 * Like {@link Map}, {@code null} keys and values are not supported.  However unlike {@link Map}, keys and values
 * may expire at any time (based on the {@link Cache} implementation and configuration).
 *
 * @param <K> the key type
 * @param <V> the value type
 *
 * @author brian.oliver
 * @since Mar-2021
 */
public interface Cache<K, V> {

    /**
     * Obtains the current value for the specified key.
     *
     * @param key the key
     * @return the {@link Optional} current value,
     *         {@link Optional#empty()} if the value is not present in the {@link Cache}
     */
    Optional<V> get(K key);

    /**
     * Obtains the current value for the specified key, and if it's not available, returns the defaultValue.
     *
     * @param key the key
     * @param defaultValue the default value
     * @return the {@link Optional} current value,
     *         {@link Optional#empty()} if the value is not present in the {@link Cache} and the defaultValue is
     *         {@code null}
     */
    default Optional<V> getOrDefault(final K key, final V defaultValue) {
        Optional<V> value = get(key);

        if (!value.isPresent()) {
            value = Optional.ofNullable(defaultValue);
        }

        return value;
    }

    /**
     * Obtains the current value for the specified key, and if it's not available, uses the provided {@link Supplier}
     * to obtain a default value.
     *
     * @param key the key
     * @param supplier the {@link Supplier}
     * @return the {@link Optional} current value,
     *         {@link Optional#empty()} if the value is not present in the {@link Cache} and the default value is
     *         {@code null}
     */
    default Optional<V> getOrDefault(final K key, final Supplier<V> supplier) {
        Optional<V> value = get(key);

        if (!value.isPresent()) {
            value = supplier == null
                ? Optional.empty()
                : Optional.ofNullable(supplier.get());
        }

        return value;
    }

    /**
     * Updates the current value for the specified key to the provided value.
     *
     * @param key the key
     * @param value the value
     * @return the {@link Optional} value before the update occurred
     *         {@link Optional#empty()} if there was no value prior to the update occurring
     */
    Optional<V> put(K key, V value);

    /**
     * Removes the current value for the specified key.
     *
     * @param key the key
     * @return the {@link Optional} value that was removed
     *         {@link Optional#empty()} if there was no value present to removed
     */
    Optional<V> remove(K key);

    /**
     * Removes the current value for the specified key, if the said value satisfies the provided {@link Predicate}.
     *
     * @param key the key
     * @param predicate the {@link Predicate}
     * @return the {@link Optional} value that was removed
     *         {@link Optional#empty()} if there was no value present to remove or the {@link Predicate} was
     *         not satisfied by the current value
     */
    Optional<V> removeIf(K key, Predicate<? super V> predicate);

    /**
     * Computes and updates the current value for the specified key by obtaining a new value using the provided
     * {@link Function} based on the said current value.
     *
     * @param key the key
     * @param function the {@link Function}
     * @return the {@link Optional} value before the update occurred
     *         {@link Optional#empty()} if there was no value prior to the update occurring,
     *         or the update resulted in a {@code null}, meaning the current value was effectively removed
     */
    Optional<V> compute(K key, Function<V, V> function);

    /**
     * Computes a value for the specified key if and only if there is no existing value for the said key.  Should an
     * existing value already exist, no value is computed, the existing value is returned.
     *
     * @param key the key
     * @param supplier the {@link Supplier}
     * @return the {@link Optional} current value, or
     *         {@link Optional#empty()} if there was no previous value and the {@link Supplier} supplied a {@code null}
     */
    Optional<V> computeIfAbsent(K key, Supplier<V> supplier);

    /**
     * Computes a new value for the specified key if and only if there is an existing value for the said key.
     *
     * @param key the key
     * @param function the {@link Supplier}
     * @return the {@link Optional} computed value, or
     *         {@link Optional#empty()} if there was no previous value,
     *         or the {@link Function} supplied a {@code null} value
     */
    Optional<V> computeIfPresent(K key, Function<V, V> function);

    /**
     * Obtains a {@link Stream} of the current keys in the {@link Cache}.
     *
     * @return the {@link Stream} of keys
     */
    Stream<K> keys();

    /**
     * Obtains a {@link Stream} of the current values in the {@link Cache}.
     *
     * @return the {@link Stream} of values
     */
    Stream<V> values();

    /**
     * Consumes each of the keys and values in the {@link Cache}.
     *
     * @param consumer the {@link BiConsumer}
     */
    void forEach(BiConsumer<? super K, ? super V> consumer);

    /**
     * Clears the entries in the {@link Cache}.
     */
    void clear();

    /**
     * Determines if the {@link Cache} is empty, and thus has no entries.
     *
     * @return {@code true} if the {@link Cache} is empty, {@code false} otherwise
     */
    boolean isEmpty();

    /**
     * Obtains the number of entries in the {@link Cache}.
     *
     * @return the number of entries
     */
    int size();
}
