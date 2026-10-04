package io.ohmvir.plugins.github.commentor.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.AbortException;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Plain unit tests: no Jenkins instance needed. */
class IdentifierValidatorTest {

    private static final String HEX = "0123456789abcdef";

    /** Asserts the call throws AbortException (and nothing else), returning its message. */
    private static String abortMessage(Executable call) {
        return assertThrows(AbortException.class, call).getMessage();
    }

    // ---------------------------------------------------------------------
    // validateRepo
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "octocat/hello-world",
                "a/b",
                "Owner_1/repo.name-2",
                ".github/.github",
                "ohmvir/github-commentor",
                "123/456"
            })
    void validRepoIsAccepted(String repo) {
        assertDoesNotThrow(() -> IdentifierValidator.validateRepo(repo));
    }

    @Test
    void nullRepoIsRejected() {
        String message = abortMessage(() -> IdentifierValidator.validateRepo(null));

        assertTrue(message.contains("null"), message);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "owner",
                "owner/",
                "/name",
                "/",
                "owner//name",
                "owner/name/extra",
                "owner /name",
                "owner/na me",
                "owner/name ",
                "owner/name\n",
                "owner\\name",
                "owner/n\u00e4me",
                "owner/name?x=1",
                "owner/name#1",
                "https://github.com/owner/name"
            })
    void malformedRepoIsRejected(String repo) {
        assertThrows(AbortException.class, () -> IdentifierValidator.validateRepo(repo));
    }

    @Test
    void rejectedRepoMessageEchoesTheInput() {
        String message = abortMessage(() -> IdentifierValidator.validateRepo("not a repo"));

        assertTrue(message.contains("not a repo"), message);
        assertTrue(message.contains("owner/name"), "message should say what format is expected: " + message);
    }

    /**
     * "." and ".." match the character class, but would turn the API URL into a path traversal
     * (e.g. /repos/../../...). GitHub does not allow such names. Fails until validateRepo rejects them.
     */
    @ParameterizedTest
    @ValueSource(strings = {"../..", "owner/..", "./name", "owner/.", "./."})
    void dotSegmentsAreRejected(String repo) {
        assertThrows(AbortException.class, () -> IdentifierValidator.validateRepo(repo));
    }

    // ---------------------------------------------------------------------
    // validateIdentifier: issue and pull request numbers
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(
            value = CommentableResourceType.class,
            names = {"ISSUE", "PULL_REQUEST"})
    void positiveNumberIsAcceptedForIssueAndPullRequest(CommentableResourceType type) {
        assertDoesNotThrow(() -> IdentifierValidator.validateIdentifier("1", type));
        assertDoesNotThrow(() -> IdentifierValidator.validateIdentifier("42", type));
        assertDoesNotThrow(() -> IdentifierValidator.validateIdentifier(String.valueOf(Integer.MAX_VALUE), type));
    }

    @ParameterizedTest
    @EnumSource(
            value = CommentableResourceType.class,
            names = {"ISSUE", "PULL_REQUEST"})
    void zeroAndNegativeNumbersAreRejected(CommentableResourceType type) {
        for (String id : new String[] {"0", "-1", String.valueOf(Integer.MIN_VALUE)}) {
            String message = abortMessage(() -> IdentifierValidator.validateIdentifier(id, type));

            assertTrue(message.contains("Invalid issue identifier"), message);
        }
    }

    /**
     * Currently Integer.parseInt leaks a NumberFormatException instead of the AbortException callers
     * expect, so a Pipeline would fail with a stack trace. Fails until the parse is wrapped.
     */
    @ParameterizedTest
    @EnumSource(
            value = CommentableResourceType.class,
            names = {"ISSUE", "PULL_REQUEST"})
    void nonNumericOrOutOfRangeIdentifiersAreRejectedWithAbortException(CommentableResourceType type) {
        String[] bad = {
            "abc", "", " ", " 5", "5 ", "1.5", "0x10", "12abc", "2147483648", "99999999999999999999", "a".repeat(40)
        };
        for (String id : bad) {
            assertThrows(
                    AbortException.class,
                    () -> IdentifierValidator.validateIdentifier(id, type),
                    "identifier '" + id + "' should be rejected with AbortException");
        }
    }

    // ---------------------------------------------------------------------
    // validateIdentifier: commits
    // ---------------------------------------------------------------------

    static Stream<String> validCommitHashes() {
        return Stream.of(
                HEX.repeat(2) + "01234567", // 40 chars, lowercase (SHA-1)
                (HEX.repeat(2) + "01234567").toUpperCase(),
                "0123456789ABCDEFabcdef0123456789abcdef01", // 40 chars, mixed case
                HEX.repeat(4), // 64 chars (SHA-256)
                HEX.repeat(4).toUpperCase(),
                "1".repeat(40), // digits only is still a valid hash
                "f".repeat(64));
    }

    static Stream<String> invalidCommitHashes() {
        String sha1 = HEX.repeat(2) + "01234567";
        return Stream.of(
                "",
                " ",
                "abc1234", // abbreviated
                "a".repeat(39),
                "a".repeat(41),
                "a".repeat(63),
                "a".repeat(65),
                "a".repeat(128),
                "g".repeat(40), // non-hex
                "z".repeat(64),
                "a".repeat(39) + "-",
                "0x" + "a".repeat(38),
                sha1 + "\n", // trailing newline
                " " + sha1, // leading whitespace
                sha1 + " ",
                "123456"); // a PR/issue number is not a commit
    }

    @ParameterizedTest
    @MethodSource("validCommitHashes")
    void validCommitHashIsAccepted(String hash) {
        assertDoesNotThrow(() -> IdentifierValidator.validateIdentifier(hash, CommentableResourceType.COMMIT));
    }

    @ParameterizedTest
    @MethodSource("invalidCommitHashes")
    void invalidCommitHashIsRejected(String hash) {
        String message =
                abortMessage(() -> IdentifierValidator.validateIdentifier(hash, CommentableResourceType.COMMIT));

        assertTrue(message.contains("Invalid commit identifier"), message);
    }

    // ---------------------------------------------------------------------
    // validateIdentifier: null handling
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(CommentableResourceType.class)
    void nullIdentifierIsRejectedForEveryResourceType(CommentableResourceType type) {
        String message = abortMessage(() -> IdentifierValidator.validateIdentifier(null, type));

        assertTrue(message.contains("identifier"), message);
    }

    @Test
    void nullResourceTypeIsRejected() {
        String message = abortMessage(() -> IdentifierValidator.validateIdentifier("1", null));

        assertTrue(message.contains("Invalid identifier resource type"), message);
    }

    @Test
    void nullIdentifierIsReportedBeforeNullResourceType() {
        String message = abortMessage(() -> IdentifierValidator.validateIdentifier(null, null));

        assertTrue(message.contains("Didn't get an identifier"), message);
    }
}
