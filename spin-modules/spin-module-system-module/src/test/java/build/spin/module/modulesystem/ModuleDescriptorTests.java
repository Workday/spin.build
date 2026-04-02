package build.spin.module.modulesystem;

import build.base.foundation.Exceptional;
import build.base.foundation.iterator.matching.IteratorPatternMatcher;
import build.base.foundation.iterator.matching.IteratorPatternMatchers;
import build.spin.common.util.CollectingVisitor;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
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

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.name()).isEqualTo("com.example.foo");
        assertThat(descriptor.isOpen()).isTrue();
        assertThat(descriptor.isAutomatic()).isFalse();
        assertThat(descriptor.requires().count()).isEqualTo(4L);

        assertThat(descriptor.provides().count()).isEqualTo(1L);
        assertThat(descriptor.provides().findFirst().get().providers().count()).isEqualTo(2L);

        assertThat(descriptor.uses().count()).isEqualTo(2L);

        assertThat(descriptor.exports().count()).isEqualTo(2L);

        assertThat(descriptor.opens().count()).isEqualTo(2L);

        // ensure two ModuleDescriptors built from the same source are identical
        assertThat(ModuleDescriptor.parse(moduleInfo).build()).isEqualTo(descriptor);
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

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.name()).isEqualTo("com.example.foo");
        assertThat(descriptor.isOpen()).isTrue();
        assertThat(descriptor.isAutomatic()).isFalse();
        assertThat(descriptor.requires().count()).isEqualTo(4L);

        assertThat(descriptor.provides().count()).isEqualTo(1L);
        assertThat(descriptor.provides().findFirst().get().providers().count()).isEqualTo(1L);

        assertThat(descriptor.uses().count()).isEqualTo(1L);

        assertThat(descriptor.exports().count()).isEqualTo(2L);

        assertThat(descriptor.opens().count()).isEqualTo(2L);
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

        assertThat(version).isNotNull();
        assertThat(sequence.test(version.sequence().map(ModuleDescriptor.Version.Element::getValue))).isTrue();
        assertThat(prerelease.test(version.prerelease().map(ModuleDescriptor.Version.Element::getValue))).isTrue();
        assertThat(build.test(version.build().map(ModuleDescriptor.Version.Element::getValue))).isTrue();
    }

    @Test
    void shouldCompareModuleDescriptorVersions() {

        final ModuleDescriptor.Version v0 = ModuleDescriptor.Version.parse("0");
        final ModuleDescriptor.Version otherV0 = ModuleDescriptor.Version.parse("0");

        assertThat(v0).isEqualTo(otherV0);
        assertThat(otherV0).isEqualTo(v0);

        final ModuleDescriptor.Version v0_0 = ModuleDescriptor.Version.parse("0.0");
        assertThat(v0).isEqualTo(v0_0);
        assertThat(v0_0).isEqualTo(v0);

        final ModuleDescriptor.Version v0_0_SNAPSHOT = ModuleDescriptor.Version.parse("0.0-SNAPSHOT");
        assertThat(v0).isGreaterThan(v0_0_SNAPSHOT);
        assertThat(v0_0_SNAPSHOT).isLessThan(v0);
        assertThat(v0).isNotEqualTo(v0_0_SNAPSHOT);

        final ModuleDescriptor.Version v0_0_SNAPSHOT_1 = ModuleDescriptor.Version.parse("0.0-SNAPSHOT+1");

        assertThat(v0_0_SNAPSHOT_1).isGreaterThan(v0_0_SNAPSHOT);
        assertThat(v0_0_SNAPSHOT).isLessThan(v0_0_SNAPSHOT_1);
        assertThat(v0_0_SNAPSHOT).isNotEqualTo(v0_0_SNAPSHOT_1);

        final ModuleDescriptor.Version v0_0_SNAPSHOT_2 = ModuleDescriptor.Version.parse("0.0-SNAPSHOT+2");
        assertThat(v0_0_SNAPSHOT_2).isGreaterThan(v0_0_SNAPSHOT_1);
        assertThat(v0_0_SNAPSHOT_1).isLessThan(v0_0_SNAPSHOT_2);
        assertThat(v0_0_SNAPSHOT_1).isNotEqualTo(v0_0_SNAPSHOT_2);

        final ModuleDescriptor.Version v1 = ModuleDescriptor.Version.parse("1");
        assertThat(v1).isGreaterThan(v0);
        assertThat(v0).isLessThan(v1);
        assertThat(v0).isNotEqualTo(v1);

        final ModuleDescriptor.Version v1_1 = ModuleDescriptor.Version.parse("1.1");
        assertThat(v1_1).isGreaterThan(v1);
        assertThat(v1).isLessThan(v1_1);
        assertThat(v1).isNotEqualTo(v1_1);
    }

    @Test
    void shouldWalkEmptyModuleDescriptor() {

        final ModuleDescriptor.Builder builder = ModuleDescriptor.Builder.create("com.example.foo");
        final ModuleDescriptor descriptor = builder.build();

        final CollectingVisitor<ModuleDescriptor, List<ModuleDescriptor>> collector =
            new CollectingVisitor<>(Collectors.toList());

        descriptor.walk(collector, requires -> Exceptional.empty());

        final List<ModuleDescriptor> list = collector.collect();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo(descriptor);
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
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0)).isEqualTo(descriptor);
        assertThat(list.get(1)).isEqualTo(other);
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
        assertThat(list.size()).isEqualTo(4);
        assertThat(list.get(0)).isEqualTo(foo);
        assertThat(list.get(1)).isEqualTo(bar);
        assertThat(list.get(2)).isEqualTo(gar);
        assertThat(list.get(3)).isEqualTo(gar);
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
