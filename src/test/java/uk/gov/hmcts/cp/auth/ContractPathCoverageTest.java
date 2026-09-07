package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enumerates the API contract's paths so that a newly added endpoint fails this test until someone
 * classifies it as protected or exempt.
 *
 */
class ContractPathCoverageTest {

    private static final String SPEC_RESOURCE = "openapi/openapi-spec.yml";

    /**
     * This service depends on two api-cp artefacts - its own contract and the case URN mapper it
     * calls - and both publish their spec at the same resource path. A plain
     * {@code getResourceAsStream} therefore returns whichever jar happens to come first on the
     * classpath, which is the mapper's. The spec is selected by title instead.
     */
    private static final String SPEC_TITLE = "Crime Defendant Details API";

    /**
     * Every path the contract declares, each deliberately classified. Adding an endpoint to the
     * spec without adding it here fails the test below - which is the point.
     */
    private static final Set<String> PROTECTED_CONTRACT_PATHS = Set.of(
            "/defendants/cases/{caseURN}");

    @Test
    void every_contract_path_is_classified() {
        assertThat(contractPaths())
                .withFailMessage("The API contract has changed. Classify each new endpoint as "
                        + "protected (add it to PROTECTED_CONTRACT_PATHS) or exempt (add it to "
                        + "ExemptPaths, which is a security change and must be reviewed as one).")
                .containsExactlyInAnyOrderElementsOf(PROTECTED_CONTRACT_PATHS);
    }

    @Test
    void no_contract_path_is_exempt_from_token_validation() {
        for (final String path : contractPaths()) {
            assertThat(ExemptPaths.isExempt(concreteForm(path)))
                    .withFailMessage("Contract path %s is exempt from token validation", path)
                    .isFalse();
        }
    }

    /**
     * The contract declares a security scheme globally, so the service is obliged to enforce one.
     * If this ever stops being true the spec and the service have diverged.
     */
    @Test
    void the_contract_declares_a_security_scheme_for_every_endpoint() {
        final Map<String, Object> spec = spec();

        assertThat(spec).containsKey("security");
        assertThat((Iterable<?>) spec.get("security")).isNotEmpty();

        @SuppressWarnings("unchecked")
        final Map<String, Object> components = (Map<String, Object>) spec.get("components");
        @SuppressWarnings("unchecked")
        final Map<String, Object> schemes = (Map<String, Object>) components.get("securitySchemes");
        assertThat(schemes).isNotEmpty();
    }

    private static Set<String> contractPaths() {
        @SuppressWarnings("unchecked")
        final Map<String, Object> paths = (Map<String, Object>) spec().get("paths");
        return paths.keySet();
    }

    private static Map<String, Object> spec() {
        final List<Map<String, Object>> specs = new ArrayList<>();
        try {
            final Enumeration<URL> resources = ContractPathCoverageTest.class.getClassLoader()
                    .getResources(SPEC_RESOURCE);
            while (resources.hasMoreElements()) {
                try (InputStream in = resources.nextElement().openStream()) {
                    specs.add(new Yaml().load(in));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + SPEC_RESOURCE, e);
        }

        return specs.stream()
                .filter(spec -> SPEC_TITLE.equals(title(spec)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No spec titled '" + SPEC_TITLE + "' on the classpath. Found: "
                                + specs.stream().map(ContractPathCoverageTest::title).toList()));
    }

    private static String title(final Map<String, Object> spec) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> info = (Map<String, Object>) spec.get("info");
        return info == null ? null : String.valueOf(info.get("title"));
    }

    /** Substitutes a sample value for each path template variable. */
    private static String concreteForm(final String templatedPath) {
        return templatedPath.replaceAll("\\{[^}]+}", "sample");
    }
}
