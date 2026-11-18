package com.triphuddle.smoke;

import com.triphuddle.smoke.support.CoveredBy;
import com.triphuddle.smoke.support.Flow;
import com.triphuddle.smoke.support.Requirement;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every requirement in the READMEs has a test, and every test names requirements and flows that exist. Missing
 * requirements are reported in target/traceability.md, and fail the build only with -Dtraceability.strict=true,
 * until the smoke suite is complete.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TraceabilityTest {

    // "- FR-6 ..." in the root README, "- **GRP-2** ..." in a module's.
    static final Pattern ID_LINE = Pattern.compile("^- (?:\\*\\*)?([A-Z]{2,4}-[0-9]+)(?:\\*\\*)? ", Pattern.MULTILINE);

    static Path root;
    static Map<String, Path> requirements;
    static Set<String> flows;
    static Map<String, List<String>> proofs;
    static List<String> coveredBy;
    static List<String> namedFlows;

    @BeforeAll
    static void read() throws IOException {
        root = Path.of(System.getProperty("traceability.root", "..")).toAbsolutePath().normalize();
        requirements = new TreeMap<>();
        List<Path> readmes = new ArrayList<>(List.of(root.resolve("README.md")));
        try (Stream<Path> modules = Files.list(root)) {
            modules.map(module -> module.resolve("docs/README.md")).filter(Files::exists).forEach(readmes::add);
        }
        for (Path readme : readmes) {
            Matcher matcher = ID_LINE.matcher(Files.readString(readme));
            while (matcher.find()) {
                requirements.put(matcher.group(1), readme);
            }
        }
        String rootReadme = Files.readString(root.resolve("README.md"));
        String flowSection = rootReadme.substring(rootReadme.indexOf("## End-to-end flows"),
                rootReadme.indexOf("## Software requirements"));
        flows = Pattern.compile("^### (.+)$", Pattern.MULTILINE).matcher(flowSection).results()
                .map(result -> result.group(1).strip()).collect(Collectors.toCollection(TreeSet::new));

        JavaClasses classes = new ClassFileImporter().importPackages("com.triphuddle.smoke");
        proofs = new TreeMap<>();
        coveredBy = new ArrayList<>();
        namedFlows = new ArrayList<>();
        for (JavaClass type : classes) {
            type.tryGetAnnotationOfType(Flow.class).ifPresent(flow -> namedFlows.add(flow.value()));
            type.tryGetAnnotationOfType(Requirement.class).ifPresent(requirement ->
                    record(requirement.value(), type.getSimpleName()));
            for (JavaMethod method : type.getMethods()) {
                String where = type.getSimpleName() + "." + method.getName();
                method.tryGetAnnotationOfType(Requirement.class).ifPresent(requirement ->
                        record(requirement.value(), where));
                method.tryGetAnnotationOfType(CoveredBy.class).ifPresent(covered -> {
                    coveredBy.addAll(Arrays.asList(covered.value()));
                    method.tryGetAnnotationOfType(Requirement.class).ifPresent(requirement -> Arrays.stream(
                            requirement.value()).forEach(id -> proofs.get(id).add("covered by "
                            + String.join(", ", covered.value()))));
                });
            }
        }
    }

    static void record(String[] ids, String where) {
        for (String id : ids) {
            proofs.computeIfAbsent(id, ignored -> new ArrayList<>()).add(where);
        }
    }

    @Test
    void every_requirement_a_test_names_is_in_a_readme() {
        assertThat(requirements).as("requirements found in the READMEs").isNotEmpty();
        assertThat(proofs.keySet()).allSatisfy(id -> assertThat(requirements).as(id).containsKey(id));
    }

    @Test
    void every_flow_a_test_walks_is_a_flow_in_the_root_readme() {
        assertThat(flows).as("flows in the root README").isNotEmpty();
        assertThat(namedFlows).allSatisfy(flow -> assertThat(flows).as(flow).contains(flow));
    }

    @Test
    void every_integration_test_named_as_proof_exists() throws IOException {
        for (String reference : coveredBy) {
            String[] parts = reference.split(":", 2);
            assertThat(parts).as(reference + " is module:ClassName").hasSize(2);
            Path tests = root.resolve(parts[0]).resolve("src/test/java");
            assertThat(tests).as(reference).isDirectory();
            try (Stream<Path> files = Files.walk(tests)) {
                assertThat(files.map(path -> path.getFileName().toString()))
                        .as(reference).contains(parts[1] + ".java");
            }
        }
    }

    @Test
    void every_requirement_and_flow_has_a_test() throws IOException {
        Set<String> missing = new TreeSet<>(requirements.keySet());
        missing.removeAll(proofs.keySet());
        Set<String> unwalked = new TreeSet<>(flows);
        unwalked.removeAll(namedFlows);

        StringBuilder report = new StringBuilder("# Traceability\n\n")
                .append("%d requirements in the READMEs, %d with a test, %d without; %d of %d flows walked.%n%n"
                        .formatted(requirements.size(), requirements.size() - missing.size(), missing.size(),
                                flows.size() - unwalked.size(), flows.size()))
                .append("| Requirement | README | Proved by |\n|---|---|---|\n");
        Map<String, Path> ordered = new LinkedHashMap<>(requirements);
        ordered.forEach((id, readme) -> report.append("| %s | %s | %s |%n".formatted(id,
                root.relativize(readme), String.join("; ", proofs.getOrDefault(id, List.of("**missing**"))))));
        report.append("\nFlows not walked yet: ").append(unwalked.isEmpty() ? "none" : String.join("; ", unwalked))
                .append('\n');
        Path target = Path.of("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("traceability.md"), report);

        if (Boolean.getBoolean("traceability.strict")) {
            assertThat(missing).as("requirements without a test").isEmpty();
            assertThat(unwalked).as("flows without a smoke test").isEmpty();
        }
    }
}
