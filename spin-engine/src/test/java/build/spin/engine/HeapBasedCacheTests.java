package build.spin.engine;

import build.spin.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HeapBasedCache}s.
 *
 * @author brian.oliver
 * @since Mar-2021
 */
class HeapBasedCacheTests {

    /**
     * The {@link Cache} under test.
     */
    private Cache<String, String> cache;

    @BeforeEach
    void onBeforeEach() {
        this.cache = new HeapBasedCache<>();
    }

    /**
     * Ensure we can create an empty {@link Cache}.
     */
    @Test
    void shouldCreateEmptyCache() {
        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);

        assertThat(this.cache.keys().count()).isEqualTo(0L);
        assertThat(this.cache.values().count()).isEqualTo(0L);

        assertThat(this.cache.get("Greeting").isPresent()).isFalse();
    }

    /**
     * Ensure entries can be added to a {@link Cache}.
     */
    @Test
    void shouldCreateEntries() {
        final Optional<String> previous = this.cache.put("Greeting", "G'day");
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(1);
        assertThat(previous.isPresent()).isFalse();
        assertThat(this.cache.get("Greeting").get()).isEqualTo("G'day");

        final Optional<String> current = this.cache.computeIfAbsent("Welcome", () -> "Hello");
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(2);
        assertThat(current.isPresent()).isTrue();
        assertThat(current.get()).isEqualTo("Hello");
        assertThat(this.cache.get("Welcome").get()).isEqualTo("Hello");

        final Optional<String> computed = this.cache.compute("Message", __ -> "Awesome");
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(3);
        assertThat(computed.isPresent()).isTrue();
        assertThat(computed.get()).isEqualTo("Awesome");
        assertThat(this.cache.get("Message").get()).isEqualTo("Awesome");

        assertThat(this.cache.keys().count()).isEqualTo(3L);
        assertThat(this.cache.values().count()).isEqualTo(3L);
    }

    /**
     * Ensure entries in a {@link Cache} can be updated.
     */
    @Test
    void shouldUpdateEntries() {
        final String key = "Greeting";

        final String hello = "Hello";
        final Optional<String> first = this.cache.put(key, hello);
        assertThat(first.isPresent()).isFalse();
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(1);
        assertThat(this.cache.get(key).get()).isEqualTo(hello);

        final String gday = "G'day";
        final Optional<String> second = this.cache.put(key, gday);
        assertThat(second.isPresent()).isTrue();
        assertThat(second.get()).isEqualTo(hello);
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(1);
        assertThat(this.cache.get(key).get()).isEqualTo(gday);

        final String howdy = "Howdy!";
        final Optional<String> third = this.cache.computeIfPresent(key, __ -> howdy);
        assertThat(third.isPresent()).isTrue();
        assertThat(third.get()).isEqualTo(howdy);
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(1);
        assertThat(this.cache.get(key).get()).isEqualTo(howdy);

        final String yomate = "Yo Mate!";
        final Optional<String> fourth = this.cache.computeIfAbsent(key, () -> yomate);
        assertThat(fourth.isPresent()).isTrue();
        assertThat(fourth.get()).isEqualTo(howdy);
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(1);
        assertThat(this.cache.get(key).get()).isEqualTo(howdy);

        final String wazzup = "Wazzup?";
        final Optional<String> fifth = this.cache.compute(key, __ -> wazzup);
        assertThat(fifth.isPresent()).isTrue();
        assertThat(fifth.get()).isEqualTo(wazzup);
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.size()).isEqualTo(1);
        assertThat(this.cache.get(key).get()).isEqualTo(wazzup);
    }

    /**
     * Ensure entries in a {@link Cache} aren't be updated.
     */
    @Test
    void shouldNotUpdateEntries() {
        final String key = "Greeting";

        final String hello = "Hello";
        final Optional<String> first = this.cache.put(key, hello);
        assertThat(first.isPresent()).isFalse();
        assertThat(this.cache.isEmpty()).isFalse();
        assertThat(this.cache.get(key).get()).isEqualTo(hello);

        final String howdy = "Howdy!";
        final Optional<String> second = this.cache.computeIfAbsent(key, () -> howdy);
        assertThat(second.isPresent()).isTrue();
        assertThat(second.get()).isEqualTo(hello);
        assertThat(this.cache.get(key).get()).isEqualTo(hello);

        final Optional<String> third = this.cache.compute(key, existing -> existing);
        assertThat(third.isPresent()).isTrue();
        assertThat(third.get()).isEqualTo(hello);
        assertThat(this.cache.get(key).get()).isEqualTo(hello);
    }

    /**
     * Ensure entries in a {@link Cache} are removed.
     */
    @Test
    void shouldRemoveEntries() {

        final String key = "Greeting";

        assertThat(this.cache.remove(key).isPresent()).isFalse();

        final String hello = "Hello";
        this.cache.put(key, hello);

        final Optional<String> first = this.cache.remove(key);
        assertThat(first.isPresent()).isTrue();
        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);
        assertThat(this.cache.get(key).isPresent()).isFalse();

        this.cache.put(key, hello);

        final Optional<String> second = this.cache.removeIf(key, __ -> true);
        assertThat(second.isPresent()).isTrue();
        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);
        assertThat(this.cache.get(key).isPresent()).isFalse();
    }

    /**
     * Ensure entries in a {@link Cache} are removed when using {@code null}.
     */
    @Test
    void shouldRemoveEntriesWhenUsingNull() {

        final String key = "Greeting";

        assertThat(this.cache.remove(key).isPresent()).isFalse();

        final String hello = "Hello";
        this.cache.put(key, hello);

        final Optional<String> first = this.cache.put(key, null);
        assertThat(first.isPresent()).isTrue();
        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);
        assertThat(this.cache.get(key).isPresent()).isFalse();

        this.cache.put(key, hello);

        final Optional<String> second = this.cache.computeIfPresent(key, __ -> null);
        assertThat(second.isPresent()).isFalse();
        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);
        assertThat(this.cache.get(key).isPresent()).isFalse();

        this.cache.put(key, hello);

        final Optional<String> third = this.cache.compute(key, __ -> null);
        assertThat(third.isPresent()).isFalse();
        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);
        assertThat(this.cache.get(key).isPresent()).isFalse();
    }

    /**
     * Ensure clearing a {@link Cache} removes all entries.
     */
    @Test
    void shouldClearCache() {
        this.cache.put("Greeting", "Hello");
        this.cache.put("Welcome", "Always");

        this.cache.clear();

        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);
        assertThat(this.cache.get("Greeting").isPresent()).isFalse();
        assertThat(this.cache.get("Welcome").isPresent()).isFalse();
    }

    /**
     * Ensure clearing a {@link Cache} with many entries removes all of them.
     * <p>
     * Regression test: {@code clear()} previously streamed keys lazily while removing entries,
     * which could cause some keys to be skipped on concurrent-safe maps.
     */
    @Test
    void shouldClearCacheWithManyEntries() {
        final int count = 100;

        for (int i = 0; i < count; i++) {
            this.cache.put("key-" + i, "value-" + i);
        }

        assertThat(this.cache.size()).isEqualTo(count);

        this.cache.clear();

        assertThat(this.cache.isEmpty()).isTrue();
        assertThat(this.cache.size()).isEqualTo(0);

        for (int i = 0; i < count; i++) {
            assertThat(this.cache.get("key-" + i)).isEmpty();
        }
    }
}
