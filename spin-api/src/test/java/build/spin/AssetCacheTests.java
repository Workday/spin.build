package build.spin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the default {@code get} methods on {@link AssetCache} that look up by task interface.
 */
class AssetCacheTests {

    interface MyTask extends Task<String> {
    }

    static class ConcreteTask implements MyTask {
        public String compute() {
            return "";
        }
    }

    static class UnrelatedTask implements Task<String> {
        public String compute() {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static Asset<String> assetFor(final Project project, final Class<? extends Task<String>> taskClass) {
        final Invocable<String> invocable = mock(Invocable.class);
        when(invocable.getProject()).thenReturn(project);
        when(invocable.getTaskClass()).thenReturn((Class) taskClass);

        final Asset<String> asset = mock(Asset.class);
        when(asset.invocable()).thenReturn(invocable);
        return asset;
    }

    private static AssetCache cacheOf(final Asset<?>... assets) {
        return new AssetCache() {
            @Override
            public <T> Optional<Asset<T>> get(final Reference reference) {
                return Optional.empty();
            }

            @Override
            public <T> Optional<Asset<T>> putIfAbsent(final Invocable<T> invocable, final T value) {
                return Optional.empty();
            }

            @Override
            public <T> Optional<Asset<T>> putIfAbsent(final Asset<T> asset) {
                return Optional.empty();
            }

            @Override
            public Stream<Invocable<?>> invocables() {
                return Stream.empty();
            }

            @Override
            public Stream<Asset<?>> assets() {
                return List.of(assets).stream();
            }
        };
    }

    @Test
    void getByInterfaceFindsMatchingAsset() {
        final Asset<String> asset = assetFor(mock(Project.class), ConcreteTask.class);
        final AssetCache cache = cacheOf(asset);

        assertThat(cache.get(MyTask.class)).isPresent();
    }

    @Test
    void getByInterfaceReturnsEmptyWhenNoMatch() {
        final Asset<String> asset = assetFor(mock(Project.class), UnrelatedTask.class);
        final AssetCache cache = cacheOf(asset);

        assertThat(cache.get(MyTask.class)).isEmpty();
    }

    @Test
    void getByInterfaceReturnsEmptyForEmptyCache() {
        assertThat(cacheOf().get(MyTask.class)).isEmpty();
    }

    @Test
    void getByProjectAndInterfaceFindsMatchingAsset() {
        final Project project = mock(Project.class);
        final Asset<String> asset = assetFor(project, ConcreteTask.class);
        final AssetCache cache = cacheOf(asset);

        assertThat(cache.get(project, MyTask.class)).isPresent();
    }

    @Test
    void getByProjectAndInterfaceExcludesWrongProject() {
        final Project projectA = mock(Project.class);
        final Project projectB = mock(Project.class);
        final Asset<String> asset = assetFor(projectA, ConcreteTask.class);
        final AssetCache cache = cacheOf(asset);

        assertThat(cache.get(projectB, MyTask.class)).isEmpty();
    }

    @Test
    void getByProjectAndInterfaceExcludesWrongInterface() {
        final Project project = mock(Project.class);
        final Asset<String> asset = assetFor(project, UnrelatedTask.class);
        final AssetCache cache = cacheOf(asset);

        assertThat(cache.get(project, MyTask.class)).isEmpty();
    }
}
