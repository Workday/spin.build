package build.spin.module.modulesystem;

import build.base.foundation.Exceptional;
import build.base.foundation.iterator.matching.IteratorPatternMatcher;
import build.base.foundation.iterator.matching.IteratorPatternMatchers;
import build.spin.common.util.CollectingVisitor;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleDescriptorTests {

    @Test
    void shouldParseSimpleModule() {

        // a parsable module-info.java file
        final String moduleInfo = "open module com.example.foo {\n"
            + "requires com.example.foo.http;\n"
            + " // this is a comment \n"
            + "requires java.logging;\n"
            + " /* this is a comment\nacross\n multiple \n\n \n lines */"
            + "requires static com.workday.fruit;\n"
            + "requires transitive com.example.foo.network;\n"
            + "exports com.example.foo.bar;\n"
            + "exports com.example.foo.internal to com.example.foo.probe, com.example.other;\n"
            + "opens com.example.foo.quux;\n"
            + "opens com.example.foo.internal to com.example.foo.network,\n"
            + "com.example.foo.probe ;\n"
            + "uses com.example.foo.spi.Intf ;\n"
            + "uses com.example.foo.spi.Blah;\n"
            + "provides com.example.foo.spi.Intf with com.example.foo.Impl, com.other.Impl;\n"
            + "}\n";

        final ModuleDescriptor descriptor = ModuleDescriptor.parse(moduleInfo).build();

        assertThat(descriptor, is(not(nullValue())));
        assertThat(descriptor.name(), is("com.example.foo"));
        assertThat(descriptor.isOpen(), is(true));
        assertThat(descriptor.isAutomatic(), is(false));
        assertThat(descriptor.requires().count(), is(4L));

        assertThat(descriptor.provides().count(), is(1L));
        assertThat(descriptor.provides().findFirst().get().providers().count(), is(2L));

        assertThat(descriptor.uses().count(), is(2L));

        assertThat(descriptor.exports().count(), is(2L));

        assertThat(descriptor.opens().count(), is(2L));

        // ensure two ModuleDescriptors built from the same source are identical
        assertThat(ModuleDescriptor.parse(moduleInfo).build(), is(descriptor));
    }

    @Test
    void shouldParseSimpleModuleFile() {

        final String moduleInfo = "open module com.example.foo {\n"
            + "    requires com.example.foo.http;\n"
            + "    // this is a comment\n"
            + "    requires java.logging;\n"
            + "    /* this is a comment\n"
            + "        across\n"
            + "        multiple\n"
            + "\n"
            + "\n"
            + "\n"
            + "          lines */\n"
            + "    requires static com.workday.fruit;\n"
            + "    requires transitive com.example.foo.network;\n"
            + "    exports com.example.foo.bar;\n"
            + "    exports com.example.foo.internal to com.example.foo.probe, com.example.other;\n"
            + "    opens com.example.foo.quux;\n"
            + "    opens com.example.foo.internal to com.example.foo.network,\n"
            + "    com.example.foo.probe ;\n"
            + "    uses com.example.foo.spi.Intf ;\n"
            + "    provides com.example.foo.spi.Intf with com.example.foo.Impl ;\n"
            + "}";

        final ModuleDescriptor descriptor = ModuleDescriptor.parse(new StringReader(moduleInfo))
            .build();

        assertThat(descriptor, is(not(nullValue())));
        assertThat(descriptor.name(), is("com.example.foo"));
        assertThat(descriptor.isOpen(), is(true));
        assertThat(descriptor.isAutomatic(), is(false));
        assertThat(descriptor.requires().count(), is(4L));

        assertThat(descriptor.provides().count(), is(1L));
        assertThat(descriptor.provides().findFirst().get().providers().count(), is(1L));

        assertThat(descriptor.uses().count(), is(1L));

        assertThat(descriptor.exports().count(), is(2L));

        assertThat(descriptor.opens().count(), is(2L));
    }

    @Test
    void shouldParseModuleDescriptorVersions() {

        final ModuleDescriptor.Version version = ModuleDescriptor.Version.parse("1.21.0-SNAPSHOT+42");

        final IteratorPatternMatcher<Object> sequence = IteratorPatternMatchers.starts()
            .then().matches(1)
            .then().matches(21)
            .then().matches(0)
            .then().ends();

        final IteratorPatternMatcher<Object> prerelease = IteratorPatternMatchers.starts()
            .then().matches("SNAPSHOT")
            .then().ends();

        final IteratorPatternMatcher<Object> build = IteratorPatternMatchers.starts()
            .then().matches(42)
            .then().ends();

        assertThat(version, is(not(nullValue())));
        assertThat(sequence.test(version.sequence().map(ModuleDescriptor.Version.Element::getValue)), is(true));
        assertThat(prerelease.test(version.prerelease().map(ModuleDescriptor.Version.Element::getValue)), is(true));
        assertThat(build.test(version.build().map(ModuleDescriptor.Version.Element::getValue)), is(true));
    }

    @Test
    void shouldCompareModuleDescriptorVersions() {

        final ModuleDescriptor.Version v0 = ModuleDescriptor.Version.parse("0");
        final ModuleDescriptor.Version otherV0 = ModuleDescriptor.Version.parse("0");

        assertThat(v0, Matchers.is(otherV0));
        assertThat(otherV0, Matchers.is(v0));

        final ModuleDescriptor.Version v0_0 = ModuleDescriptor.Version.parse("0.0");
        assertThat(v0, Matchers.is(v0_0));
        assertThat(v0_0, Matchers.is(v0));

        final ModuleDescriptor.Version v0_0_SNAPSHOT = ModuleDescriptor.Version.parse("0.0-SNAPSHOT");
        assertThat(v0, greaterThan(v0_0_SNAPSHOT));
        assertThat(v0_0_SNAPSHOT, lessThan(v0));
        assertThat(v0, is(Matchers.not(v0_0_SNAPSHOT)));

        final ModuleDescriptor.Version v0_0_SNAPSHOT_1 = ModuleDescriptor.Version.parse("0.0-SNAPSHOT+1");

        assertThat(v0_0_SNAPSHOT_1, greaterThan(v0_0_SNAPSHOT));
        assertThat(v0_0_SNAPSHOT, lessThan(v0_0_SNAPSHOT_1));
        assertThat(v0_0_SNAPSHOT, is(Matchers.not(v0_0_SNAPSHOT_1)));

        final ModuleDescriptor.Version v0_0_SNAPSHOT_2 = ModuleDescriptor.Version.parse("0.0-SNAPSHOT+2");
        assertThat(v0_0_SNAPSHOT_2, greaterThan(v0_0_SNAPSHOT_1));
        assertThat(v0_0_SNAPSHOT_1, lessThan(v0_0_SNAPSHOT_2));
        assertThat(v0_0_SNAPSHOT_1, is(Matchers.not(v0_0_SNAPSHOT_2)));

        final ModuleDescriptor.Version v1 = ModuleDescriptor.Version.parse("1");
        assertThat(v1, greaterThan(v0));
        assertThat(v0, lessThan(v1));
        assertThat(v0, is(Matchers.not(v1)));

        final ModuleDescriptor.Version v1_1 = ModuleDescriptor.Version.parse("1.1");
        assertThat(v1_1, greaterThan(v1));
        assertThat(v1, lessThan(v1_1));
        assertThat(v1, is(Matchers.not(v1_1)));
    }

    @Test
    void shouldWalkEmptyModuleDescriptor() {

        final ModuleDescriptor.Builder builder = ModuleDescriptor.Builder.create("com.example.foo");
        final ModuleDescriptor descriptor = builder.build();

        final CollectingVisitor<ModuleDescriptor, List<ModuleDescriptor>> collector =
            new CollectingVisitor<>(Collectors.toList());

        descriptor.walk(collector, requires -> Exceptional.empty());

        final List<ModuleDescriptor> list = collector.collect();
        assertThat(list.size(), is(1));
        assertThat(list.get(0), is(descriptor));
    }

    @Test
    void shouldThrowCyclicDependencyWhenWalkingSelfDependentModuleDescriptor() {

        final ModuleDescriptor.Builder builder = ModuleDescriptor.Builder.create("com.example.foo");
        builder.requires("com.example.foo", null, null);
        final ModuleDescriptor descriptor = builder.build();

        final CollectingVisitor<ModuleDescriptor, List<ModuleDescriptor>> collector =
            new CollectingVisitor<>(Collectors.toList());

        assertThrows(CyclicDependencyException.class,
            () -> descriptor.walk(collector, requires -> Exceptional.of(descriptor)));
    }

    @Test
    void shouldWalkSimpleModuleDescriptor() {

        final HashMap<String, ModuleDescriptor> moduleDescriptors = new HashMap<>();

        final ModuleDescriptor descriptor = ModuleDescriptor.Builder.create("com.example.foo")
            .requires("com.example.other", null, null)
            .build();
        moduleDescriptors.put(descriptor.name(), descriptor);

        final ModuleDescriptor other = ModuleDescriptor.Builder.create("com.example.other")
            .build();
        moduleDescriptors.put(other.name(), other);

        final CollectingVisitor<ModuleDescriptor, List<ModuleDescriptor>> collector =
            new CollectingVisitor<>(Collectors.toList());

        descriptor.walk(collector, d -> Exceptional.ofNullable(moduleDescriptors.get(d.name())));

        final List<ModuleDescriptor> list = collector.collect();
        assertThat(list.size(), is(2));
        assertThat(list.get(0), is(descriptor));
        assertThat(list.get(1), is(other));
    }

    @Test
    void shouldWalkComplexModuleDescriptor() {

        final HashMap<String, ModuleDescriptor> moduleDescriptors = new HashMap<>();

        final ModuleDescriptor foo = ModuleDescriptor.Builder.create("com.example.foo")
            .requires("com.example.bar", null, null)
            .requires("com.example.gar", null, null)
            .build();
        moduleDescriptors.put(foo.name(), foo);

        final ModuleDescriptor bar = ModuleDescriptor.Builder.create("com.example.bar")
            .requires("com.example.gar", null, null)
            .build();
        moduleDescriptors.put(bar.name(), bar);

        final ModuleDescriptor gar = ModuleDescriptor.Builder.create("com.example.gar")
            .build();
        moduleDescriptors.put(gar.name(), gar);

        final CollectingVisitor<ModuleDescriptor, List<ModuleDescriptor>> collector =
            new CollectingVisitor<>(Collectors.toList());

        foo.walk(collector, d -> Exceptional.ofNullable(moduleDescriptors.get(d.name())));

        final List<ModuleDescriptor> list = collector.collect();
        assertThat(list.size(), is(4));
        assertThat(list.get(0), is(foo));
        assertThat(list.get(1), is(bar));
        assertThat(list.get(2), is(gar));
        assertThat(list.get(3), is(gar));
    }

    @Test
    void shouldThrowCyclicDependencyWhenWalkingTransitivelyDefinedModuleDescriptors() {

        final HashMap<String, ModuleDescriptor> moduleDescriptors = new HashMap<>();

        final ModuleDescriptor descriptor = ModuleDescriptor.Builder.create("com.example.foo")
            .requires("com.example.other", null, null)
            .build();
        moduleDescriptors.put(descriptor.name(), descriptor);

        final ModuleDescriptor other = ModuleDescriptor.Builder.create("com.example.other")
            .requires("com.example.foo", null, null)
            .build();
        moduleDescriptors.put(other.name(), other);

        final CollectingVisitor<ModuleDescriptor, List<ModuleDescriptor>> collector =
            new CollectingVisitor<>(Collectors.toList());

        assertThrows(CyclicDependencyException.class,
            () -> descriptor.walk(collector, d -> Exceptional.ofNullable(moduleDescriptors.get(d.name()))));
    }
}
