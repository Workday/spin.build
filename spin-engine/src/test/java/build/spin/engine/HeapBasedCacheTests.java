package build.spin.engine;

import build.spin.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
        assertThat(this.cache.isEmpty(), is(true));
        assertThat(this.cache.size(), is(0));

        assertThat(this.cache.keys().count(), is(0L));
        assertThat(this.cache.values().count(), is(0L));

        assertThat(this.cache.get("Greeting").isPresent(), is(false));
    }

    /**
     * Ensure entries can be added to a {@link Cache}.
     */
    @Test
    void shouldCreateEntries() {
        final Optional<String> previous = this.cache.put("Greeting", "G'day");
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(1));
        assertThat(previous.isPresent(), is(false));
        assertThat(this.cache.get("Greeting").get(), is("G'day"));

        final Optional<String> current = this.cache.computeIfAbsent("Welcome", () -> "Hello");
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(2));
        assertThat(current.isPresent(), is(true));
        assertThat(current.get(), is("Hello"));
        assertThat(this.cache.get("Welcome").get(), is("Hello"));

        final Optional<String> computed = this.cache.compute("Message", __ -> "Awesome");
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(3));
        assertThat(computed.isPresent(), is(true));
        assertThat(computed.get(), is("Awesome"));
        assertThat(this.cache.get("Message").get(), is("Awesome"));

        assertThat(this.cache.keys().count(), is(3L));
        assertThat(this.cache.values().count(), is(3L));
    }

    /**
     * Ensure entries in a {@link Cache} can be updated.
     */
    @Test
    void shouldUpdateEntries() {
        final String key = "Greeting";

        final String hello = "Hello";
        final Optional<String> first = this.cache.put(key, hello);
        assertThat(first.isPresent(), is(false));
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(1));
        assertThat(this.cache.get(key).get(), is(hello));

        final String gday = "G'day";
        final Optional<String> second = this.cache.put(key, gday);
        assertThat(second.isPresent(), is(true));
        assertThat(second.get(), is(hello));
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(1));
        assertThat(this.cache.get(key).get(), is(gday));

        final String howdy = "Howdy!";
        final Optional<String> third = this.cache.computeIfPresent(key, __ -> howdy);
        assertThat(third.isPresent(), is(true));
        assertThat(third.get(), is(howdy));
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(1));
        assertThat(this.cache.get(key).get(), is(howdy));

        final String yomate = "Yo Mate!";
        final Optional<String> fourth = this.cache.computeIfAbsent(key, () -> yomate);
        assertThat(fourth.isPresent(), is(true));
        assertThat(fourth.get(), is(howdy));
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(1));
        assertThat(this.cache.get(key).get(), is(howdy));

        final String wazzup = "Wazzup?";
        final Optional<String> fifth = this.cache.compute(key, __ -> wazzup);
        assertThat(fifth.isPresent(), is(true));
        assertThat(fifth.get(), is(wazzup));
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.size(), is(1));
        assertThat(this.cache.get(key).get(), is(wazzup));
    }

    /**
     * Ensure entries in a {@link Cache} aren't be updated.
     */
    @Test
    void shouldNotUpdateEntries() {
        final String key = "Greeting";

        final String hello = "Hello";
        final Optional<String> first = this.cache.put(key, hello);
        assertThat(first.isPresent(), is(false));
        assertThat(this.cache.isEmpty(), is(false));
        assertThat(this.cache.get(key).get(), is(hello));

        final String howdy = "Howdy!";
        final Optional<String> second = this.cache.computeIfAbsent(key, () -> howdy);
        assertThat(second.isPresent(), is(true));
        assertThat(second.get(), is(hello));
        assertThat(this.cache.get(key).get(), is(hello));

        final Optional<String> third = this.cache.compute(key, existing -> existing);
        assertThat(third.isPresent(), is(true));
        assertThat(third.get(), is(hello));
        assertThat(this.cache.get(key).get(), is(hello));
    }

    /**
     * Ensure entries in a {@link Cache} are removed.
     */
    @Test
    void shouldRemoveEntries() {

        final String key = "Greeting";

        assertThat(this.cache.remove(key).isPresent(), is(false));

        final String hello = "Hello";
        this.cache.put(key, hello);

        final Optional<String> first = this.cache.remove(key);
        assertThat(first.isPresent(), is(true));
        assertThat(this.cache.isEmpty(), is(true));
        assertThat(this.cache.size(), is(0));
        assertThat(this.cache.get(key).isPresent(), is(false));

        this.cache.put(key, hello);

        final Optional<String> second = this.cache.removeIf(key, __ -> true);
        assertThat(second.isPresent(), is(true));
        assertThat(this.cache.isEmpty(), is(true));
        assertThat(this.cache.size(), is(0));
        assertThat(this.cache.get(key).isPresent(), is(false));
    }

    /**
     * Ensure entries in a {@link Cache} are removed when using {@code null}.
     */
    @Test
    void shouldRemoveEntriesWhenUsingNull() {

        final String key = "Greeting";

        assertThat(this.cache.remove(key).isPresent(), is(false));

        final String hello = "Hello";
        this.cache.put(key, hello);

        final Optional<String> first = this.cache.put(key, null);
        assertThat(first.isPresent(), is(true));
        assertThat(this.cache.isEmpty(), is(true));
        assertThat(this.cache.size(), is(0));
        assertThat(this.cache.get(key).isPresent(), is(false));

        this.cache.put(key, hello);

        final Optional<String> second = this.cache.computeIfPresent(key, __ -> null);
        assertThat(second.isPresent(), is(false));
        assertThat(this.cache.isEmpty(), is(true));
        assertThat(this.cache.size(), is(0));
        assertThat(this.cache.get(key).isPresent(), is(false));

        this.cache.put(key, hello);

        final Optional<String> third = this.cache.compute(key, __ -> null);
        assertThat(third.isPresent(), is(false));
        assertThat(this.cache.isEmpty(), is(true));
        assertThat(this.cache.size(), is(0));
        assertThat(this.cache.get(key).isPresent(), is(false));
    }

    /**
     * Ensure clearing a {@link Cache} removes all entries.
     */
    @Test
    void shouldClearCache() {
        this.cache.put("Greeting", "Hello");
        this.cache.put("Welcome", "Always");

        this.cache.clear();

        assertThat(this.cache.isEmpty(), is(true));
        assertThat(this.cache.size(), is(0));
        assertThat(this.cache.get("Greeting").isPresent(), is(false));
        assertThat(this.cache.get("Welcome").isPresent(), is(false));
    }
}
