package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * X-Correlation-ID (RNF-MS01-03, Integration Standards): the value is a UUID v4, it is kept if it is valid and if it is
 * missing or not valid a new one is generated
 */
class CorrelationIdsTest {

    private static final String VALID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        /**
     * Checks that a generated value is a UUID v4
     *
     * @param generated value that is verified
     */
    private static void assertGeneratedUuidV4(String generated) {
        assertEquals(4, UUID.fromString(generated).version());
        assertEquals(36, generated.length());
    }

        /**
     * A received UUID v4 is kept as it is
     */
    @Test
    void resolve_uuidV4_isKept() {
        assertEquals(VALID, CorrelationIds.resolve(VALID));
    }

        /**
     * A valid UUID v4 is kept without the surrounding spaces
     */
    @Test
    void resolve_validValueWithSurroundingSpaces_isKeptTrimmed() {
        assertEquals(VALID, CorrelationIds.resolve("  " + VALID + "  "));
    }

        /**
     * An uppercase UUID v4 is accepted
     */
    @Test
    void resolve_uppercaseUuidV4_isAccepted() {
        assertEquals(VALID.toUpperCase(), CorrelationIds.resolve(VALID.toUpperCase()));
    }

        /**
     * A UUID of another version is not a UUID v4 and is replaced
     */
    @Test
    void resolve_uuidOfAnotherVersion_isReplaced() {
        String version1 = "c19b472d-7a2a-1d3b-a12b-abcdef123456";

        String generated = CorrelationIds.resolve(version1);

        assertNotEquals(version1, generated);
        assertGeneratedUuidV4(generated);
    }

        /**
     * A text that is not a UUID is replaced even if it is short and readable
     */
    @Test
    void resolve_textThatIsNotAUuid_isReplaced() {
        assertGeneratedUuidV4(CorrelationIds.resolve("c19b-472d-a12b"));
        assertGeneratedUuidV4(CorrelationIds.resolve("a".repeat(100)));
    }

        /**
     * If the header is missing a UUID v4 is generated
     */
    @Test
    void resolve_null_generatesAUuidV4() {
        assertGeneratedUuidV4(CorrelationIds.resolve(null));
    }

        /**
     * A blank value is treated as if it were missing
     */
    @Test
    void resolve_blank_generatesAUuidV4() {
        assertGeneratedUuidV4(CorrelationIds.resolve("   "));
    }

        /**
     * A value with line breaks is not valid and is replaced, so lines are not injected into the logs
     */
    @Test
    void resolve_valueWithLineBreak_isReplaced() {
        String generated = CorrelationIds.resolve(VALID + "\r\nFORGED");

        assertGeneratedUuidV4(generated);
        assertTrue(!generated.contains("FORGED"));
    }

        /**
     * Two requests without a header receive different values
     */
    @Test
    void resolve_twoCallsWithoutValue_generateDifferentValues() {
        assertNotEquals(CorrelationIds.resolve(null), CorrelationIds.resolve(null));
    }

        /**
     * If a filter already resolved the value, current returns that same value
     */
    @Test
    void current_withResolvedAttribute_returnsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CorrelationIds.ATTRIBUTE, VALID);
        request.addHeader(CorrelationIds.HEADER, "1c2f9d34-5b7a-4e0c-9d61-7a4b8e3f2a10");

        assertEquals(VALID, CorrelationIds.current(request));
    }

        /**
     * If no filter resolved the value, current resolves it from the header
     */
    @Test
    void current_withoutAttribute_resolvesTheHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, VALID);

        assertEquals(VALID, CorrelationIds.current(request));
    }

        /**
     * If there is neither attribute nor header, current generates a UUID v4
     */
    @Test
    void current_withoutAttributeNorHeader_generatesAUuidV4() {
        assertGeneratedUuidV4(CorrelationIds.current(new MockHttpServletRequest()));
    }
}
