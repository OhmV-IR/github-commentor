package io.ohmvir.plugins.github.commentor.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.AbortException;
import hudson.model.Descriptor;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.util.Secret;
import io.ohmvir.plugins.github.commentor.configuration.GithubCommentorConfiguration;
import java.io.IOException;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CredentialsUtilsTest {

    private JenkinsRule j;
    private Run<?, ?> run;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        FreeStyleProject project = j.createFreeStyleProject();
        run = j.buildAndAssertSuccess(project);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private UsernamePasswordCredentialsImpl addUsernamePassword(String id, String password) throws IOException, Descriptor.FormException {
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, "description of " + id, "user", password);
        store().addCredentials(Domain.global(), credentials);
        return credentials;
    }

    private void addSecretText(String id) throws IOException {
        store().addCredentials(
                Domain.global(),
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, id, "description of " + id, Secret.fromString("secret")));
    }

    private CredentialsStore store() {
        return CredentialsProvider.lookupStores(j.jenkins).iterator().next();
    }

    private static void setDefaultCredentials(String id) {
        GithubCommentorConfiguration.get().setDefaultCommentorCredentials(id);
    }

    private static String notFoundMessage(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(AbortException.class, call).getMessage();
    }

    // ---------------------------------------------------------------------
    // explicit credentials id
    // ---------------------------------------------------------------------

    @Test
    void explicitIdReturnsThePasswordAsPlainText() throws Exception {
        addUsernamePassword("my-pat", "ghp_explicit");

        assertEquals("ghp_explicit", CredentialUtils.resolveToken(run, "my-pat"));
    }

    @Test
    void explicitIdTakesPrecedenceOverTheDefault() throws Exception {
        addUsernamePassword("default-pat", "ghp_default");
        addUsernamePassword("explicit-pat", "ghp_explicit");
        setDefaultCredentials("default-pat");

        assertEquals("ghp_explicit", CredentialUtils.resolveToken(run, "explicit-pat"));
    }

    @Test
    void unknownExplicitIdIsRejected() throws Exception {
        addUsernamePassword("known", "pw");

        String message = notFoundMessage(() -> CredentialUtils.resolveToken(run, "does-not-exist"));

        assertTrue(message.contains("Credentials not found"), message);
    }

    @Test
    void unknownExplicitIdDoesNotFallBackToTheDefault() throws Exception {
        addUsernamePassword("default-pat", "ghp_default");
        setDefaultCredentials("default-pat");

        notFoundMessage(() -> CredentialUtils.resolveToken(run, "does-not-exist"));
    }

    /**
     * Pins current behaviour: only null falls back to the default. Blank or padded explicit ids are
     * looked up verbatim, so callers must normalise them first (the githubComment step does, via
     * Util.fixEmptyAndTrim). Flip this test if you decide blank should mean "use the default".
     */
    @Test
    void explicitIdIsUsedVerbatimWithoutTrimmingOrDefaultFallback() throws Exception {
        addUsernamePassword("known-id", "pw");
        addUsernamePassword("default-pat", "ghp_default");
        setDefaultCredentials("default-pat");

        for (String id : new String[] {"", " ", " known-id "}) {
            assertThrows(
                    AbortException.class,
                    () -> CredentialUtils.resolveToken(run, id),
                    "explicit id '" + id + "' should not resolve");
        }
    }

    @Test
    void credentialOfTheWrongTypeIsNotFound() throws Exception {
        addSecretText("secret-text-id");

        String message = notFoundMessage(() -> CredentialUtils.resolveToken(run, "secret-text-id"));

        assertTrue(message.contains("Credentials not found"), message);
    }

    // ---------------------------------------------------------------------
    // default credentials from the global configuration
    // ---------------------------------------------------------------------

    @Test
    void nullIdFallsBackToTheConfiguredDefault() throws Exception {
        addUsernamePassword("default-pat", "ghp_default");
        setDefaultCredentials("default-pat");

        assertEquals("ghp_default", CredentialUtils.resolveToken(run, null));
    }

    @Test
    void configuredDefaultIsTrimmed() throws Exception {
        addUsernamePassword("default-pat", "ghp_default");
        setDefaultCredentials("  default-pat \n");

        assertEquals("ghp_default", CredentialUtils.resolveToken(run, null));
    }

    @Test
    void nullIdWithNoDefaultConfiguredIsRejected() {
        setDefaultCredentials(null);

        String message = notFoundMessage(() -> CredentialUtils.resolveToken(run, null));

        assertTrue(message.contains("Credentials not found"), message);
    }

    @Test
    void blankDefaultIsTreatedAsNotConfigured() throws Exception {
        addUsernamePassword("default-pat", "ghp_default");

        for (String blank : new String[] {"", "   ", "\t\n"}) {
            setDefaultCredentials(blank);

            assertThrows(
                    AbortException.class,
                    () -> CredentialUtils.resolveToken(run, null),
                    "blank default '" + blank + "' should count as not configured");
        }
    }

    @Test
    void defaultPointingAtAMissingCredentialIsRejected() {
        setDefaultCredentials("deleted-id");

        String message = notFoundMessage(() -> CredentialUtils.resolveToken(run, null));

        assertTrue(message.contains("Credentials not found"), message);
    }

    // ---------------------------------------------------------------------
    // usage tracking
    // ---------------------------------------------------------------------

    @Test
    void resolvedCredentialIsTrackedAgainstTheRun() throws Exception {
        Credentials credentials = addUsernamePassword("tracked-pat", "ghp_tracked");

        CredentialUtils.resolveToken(run, "tracked-pat");

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertNotNull(fingerprint, "using a credential should create a fingerprint");
        assertTrue(
                fingerprint.getJobs().contains(run.getParent().getFullName()),
                "fingerprint should record the job that used it, got " + fingerprint.getJobs());
    }
}