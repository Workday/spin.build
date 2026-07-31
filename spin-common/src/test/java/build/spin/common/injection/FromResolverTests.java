package build.spin.common.injection;

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

import build.base.telemetry.TelemetryRecorder;
import build.codemodel.dependency.injection.Context;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.UnsatisfiedDependencyException;
import build.spin.Asset;
import build.spin.AssetCache;
import build.spin.Instruction;
import build.spin.Invocable;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.From;
import build.spin.annotation.Merge;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FromResolver}, including its handling of {@link Merge}-annotated abstract
 * {@code @From} {@link Task} types.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
class FromResolverTests {

    /**
     * A stub {@link Plugin} declaring the {@link Task}s used by these tests as its (implicitly
     * {@code public static}) nested classes.
     */
    private interface StubPlugin
        extends Plugin {

        class Parrot
            implements Task<String> {

            public String speak() {
                return "squawk";
            }
        }

        class Listener
            implements Task<String> {

            public String listen(@From(Parrot.class) final String sound) {
                return sound;
            }
        }

        // eg: multiple unrelated animals each contribute the sounds they make; the zoo wants the
        // whole chorus, not an arbitrarily-chosen single animal's sound.
        @Merge
        interface Animal
            extends Task<Set<String>> {

            Set<String> makeSounds();

            static Set<String> merge(final Stream<Set<String>> results) {
                return results.flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
            }
        }

        class Cat
            implements Animal {

            public Set<String> makeSounds() {
                return Set.of("meow");
            }
        }

        class Dog
            implements Animal {

            public Set<String> makeSounds() {
                return Set.of("woof");
            }
        }

        class Zoo
            implements Task<Set<String>> {

            public Set<String> chorus(@From(Animal.class) final Set<String> sounds) {
                return sounds;
            }
        }

        @Merge
        interface MuteAnimal
            extends Task<Set<String>> {

            Set<String> makeSounds();
        }

        class Fish
            implements MuteAnimal {

            public Set<String> makeSounds() {
                return Set.of("blub");
            }
        }

        class Aquarium
            implements Task<Set<String>> {

            public Set<String> chorus(@From(MuteAnimal.class) final Set<String> sounds) {
                return sounds;
            }
        }

        @Merge
        interface ImproperlyDeclaredAnimal
            extends Task<Set<String>> {

            Set<String> makeSounds();

            // intentionally an instance (non-static) method: @Merge requires merge(Stream<T>) to be
            // public static, and this must be rejected rather than invoked reflectively with a null
            // target.
            default Set<String> merge(final Stream<Set<String>> results) {
                return results.flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
            }
        }

        class Platypus
            implements ImproperlyDeclaredAnimal {

            public Set<String> makeSounds() {
                return Set.of("growl");
            }
        }

        class ConfusedZookeeper
            implements Task<Set<String>> {

            public Set<String> chorus(@From(ImproperlyDeclaredAnimal.class) final Set<String> sounds) {
                return sounds;
            }
        }
    }

    /**
     * Ensure a plain (non-{@link Merge}) {@code @From} dependency on a concrete {@link Task} still
     * resolves to that {@link Task}'s single result, unaffected by the {@link Merge} handling added
     * to {@link FromResolver}.
     */
    @Test
    void shouldResolveConcreteFromDependencyWithoutMerge() {
        final Project project = mockProject();
        final Reference reference = Reference.of(project, StubPlugin.Parrot.class);

        final Asset<Object> asset = mockAsset("squawk");

        final AssetCache cache = mock(AssetCache.class);
        when(cache.get(reference)).thenReturn(Optional.of(asset));

        final String result = execute(new StubPlugin.Listener(), project, mockInstruction(project), cache);

        assertThat(result).isEqualTo("squawk");
    }

    /**
     * Ensure a {@code @Merge @From(...)} dependency on an abstract {@link Task} type combines the
     * results of every matching implementor via the type's declared {@code merge(Stream<T>)} method
     * — here, the chorus of sounds made by two otherwise-unrelated animals — rather than arbitrarily
     * picking one implementor's result.
     */
    @Test
    void shouldMergeResultsFromAllImplementorsWhenAnnotatedWithMerge() {
        final Project project = mockProject();

        final Reference cat = Reference.of(project, StubPlugin.Cat.class);
        final Reference dog = Reference.of(project, StubPlugin.Dog.class);

        final Asset<Object> catAsset = mockAsset(Set.of("meow"));
        final Asset<Object> dogAsset = mockAsset(Set.of("woof"));

        final AssetCache cache = mock(AssetCache.class);
        when(cache.get(cat)).thenReturn(Optional.of(catAsset));
        when(cache.get(dog)).thenReturn(Optional.of(dogAsset));

        final Instruction<?> instruction = mockInstruction(project, cat, dog);

        final Set<String> result = execute(new StubPlugin.Zoo(), project, instruction, cache);

        assertThat(result).containsExactlyInAnyOrder("meow", "woof");
    }

    /**
     * Ensure that a {@link Merge}-annotated {@link Task} type which fails to declare a
     * {@code merge(Stream<T>)} method leaves the dependency unsatisfied, with a fatal recorded,
     * rather than silently picking an arbitrary result.
     */
    @Test
    void shouldFailWhenMergeAnnotatedTaskDoesNotDeclareMergeMethod() {
        final Project project = mockProject();
        final Reference ref = Reference.of(project, StubPlugin.Fish.class);

        final Asset<Object> asset = mockAsset(Set.of("blub"));

        final AssetCache cache = mock(AssetCache.class);
        when(cache.get(ref)).thenReturn(Optional.of(asset));

        final Instruction<?> instruction = mockInstruction(project, ref);

        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);

        assertThrows(UnsatisfiedDependencyException.class,
            () -> execute(new StubPlugin.Aquarium(), project, instruction, cache, recorder));

        verify(recorder, atLeastOnce()).fatal(anyString(), any(Object[].class));
    }

    /**
     * Regression test: a {@link Merge}-annotated {@link Task} type whose {@code merge(Stream<T>)}
     * method is declared as an instance (non-{@code static}) method must be treated the same as a
     * missing {@code merge} method — a fatal is recorded and the dependency is left unsatisfied —
     * rather than being invoked reflectively with a {@code null} target, which would otherwise throw
     * an uncaught {@link NullPointerException}.
     */
    @Test
    void shouldFailWhenMergeMethodIsNotStatic() {
        final Project project = mockProject();
        final Reference ref = Reference.of(project, StubPlugin.Platypus.class);

        final Asset<Object> asset = mockAsset(Set.of("growl"));

        final AssetCache cache = mock(AssetCache.class);
        when(cache.get(ref)).thenReturn(Optional.of(asset));

        final Instruction<?> instruction = mockInstruction(project, ref);

        final TelemetryRecorder recorder = mock(TelemetryRecorder.class);

        assertThrows(UnsatisfiedDependencyException.class,
            () -> execute(new StubPlugin.ConfusedZookeeper(), project, instruction, cache, recorder));

        verify(recorder, atLeastOnce()).fatal(anyString(), any(Object[].class));
    }

    private static Project mockProject() {
        final Project project = mock(Project.class);
        when(project.name()).thenReturn("test-project");
        return project;
    }

    @SuppressWarnings("unchecked")
    private static Instruction<?> mockInstruction(final Project project, final Reference... dependencies) {
        final Instruction<?> instruction = mock(Instruction.class);
        when(instruction.getProject()).thenReturn(project);
        when(instruction.dependencies()).thenAnswer(__ -> Stream.of(dependencies));
        return instruction;
    }

    @SuppressWarnings("unchecked")
    private static Asset<Object> mockAsset(final Object value) {
        final Asset<Object> asset = mock(Asset.class);
        when(asset.get()).thenReturn(value);
        return asset;
    }

    @SuppressWarnings("unchecked")
    private static <T> T execute(final Task<T> task,
                                 final Project project,
                                 final Instruction<?> instruction,
                                 final AssetCache cache) {
        return execute(task, project, instruction, cache, mock(TelemetryRecorder.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> T execute(final Task<T> task,
                                 final Project project,
                                 final Instruction<?> instruction,
                                 final AssetCache cache,
                                 final TelemetryRecorder recorder) {

        final InjectionFramework framework = InjectionFramework.create();
        final Context context = framework.newContext();
        context.addResolver(new FromResolver(recorder, instruction, cache));

        final Invocable<T> invocable = mock(Invocable.class);
        when(invocable.getTaskResultClass()).thenReturn(Optional.of(taskResultClass(task)));
        when(invocable.getProject()).thenReturn(project);

        return task.execute(invocable, context, framework);
    }

    private static Class<?> taskResultClass(final Task<?> task) {
        if (task instanceof StubPlugin.Listener) {
            return String.class;
        }
        return Set.class;
    }
}
