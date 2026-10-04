package io.ohmvir.plugins.github.commentor.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.verb.POST;

@WithJenkins
class GithubCommentorConfigurationTest {

    private JenkinsRule j;
    private GithubCommentorConfiguration config;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        config = ExtensionList.lookupSingleton(GithubCommentorConfiguration.class);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void addUsernamePassword(String id) throws IOException, Descriptor.FormException {
        CredentialsStore store =
                CredentialsProvider.lookupStores(j.jenkins).iterator().next();
        store.addCredentials(
                Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, "description of " + id, "user", "pw"));
    }

    private void addSecretText(String id) throws IOException {
        CredentialsStore store =
                CredentialsProvider.lookupStores(j.jenkins).iterator().next();
        store.addCredentials(
                Domain.global(),
                new StringCredentialsImpl(CredentialsScope.GLOBAL, id, "description of " + id, Secret.fromString("s")));
    }

    private void enableSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ)
                .everywhere()
                .to("alice")
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to("admin"));
    }

    private static List<String> values(ListBoxModel model) {
        return model.stream().map(o -> o.value).collect(Collectors.toList());
    }

    /** Option values excluding the blank "none" option, i.e. the actual credential ids. */
    private static List<String> credentialIds(ListBoxModel model) {
        return values(model).stream().filter(v -> v != null && !v.isEmpty()).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------
    // bean behaviour
    // ---------------------------------------------------------------------

    @Test
    void isRegisteredAsGlobalConfiguration() {
        assertNotNull(GlobalConfiguration.all().get(GithubCommentorConfiguration.class));
    }

    @Test
    void defaultConstructorLeavesCredentialsUnset() {
        assertNull(new GithubCommentorConfiguration().getDefaultCommentorCredentials());
    }

    @Test
    void dataBoundConstructorStoresCredentialsId() {
        GithubCommentorConfiguration c = new GithubCommentorConfiguration("my-pat");
        assertEquals("my-pat", c.getDefaultCommentorCredentials());
    }

    @Test
    void dataBoundConstructorAcceptsNull() {
        assertNull(new GithubCommentorConfiguration((String) null).getDefaultCommentorCredentials());
    }

    @Test
    void setterAndGetterRoundTrip() {
        config.setDefaultCommentorCredentials("abc");
        assertEquals("abc", config.getDefaultCommentorCredentials());

        config.setDefaultCommentorCredentials(null);
        assertNull(config.getDefaultCommentorCredentials());
    }

    /**
     * Saves, then builds a fresh instance the way Jenkins does after a restart.
     * NOTE: this fails until the no-arg constructor calls {@code load()}.
     */
    @Test
    void valueSurvivesSaveAndReload() {
        config.setDefaultCommentorCredentials("persisted-id");
        config.save();

        GithubCommentorConfiguration reloaded = new GithubCommentorConfiguration();
        assertEquals("persisted-id", reloaded.getDefaultCommentorCredentials());
    }

    // ---------------------------------------------------------------------
    // doFillDefaultCommentorCredentialsItems
    // ---------------------------------------------------------------------

    @Test
    void fillMethodRequiresPost() throws NoSuchMethodException {
        assertTrue(GithubCommentorConfiguration.class
                .getMethod("doFillDefaultCommentorCredentialsItems", String.class)
                .isAnnotationPresent(POST.class));
    }

    @Test
    void adminWithNoCredentialsGetsOnlyTheEmptyOption() {
        ListBoxModel model = config.doFillDefaultCommentorCredentialsItems(null);

        assertEquals(Collections.singletonList(""), values(model));
    }

    @Test
    void adminSeesAllGlobalUsernamePasswordCredentials() throws Exception {
        addUsernamePassword("cred-1");
        addUsernamePassword("cred-2");

        List<String> values = values(config.doFillDefaultCommentorCredentialsItems(null));

        assertEquals("", values.get(0), "empty option should come first");
        assertTrue(values.contains("cred-1"));
        assertTrue(values.contains("cred-2"));
        assertEquals(3, values.size());
    }

    @Test
    void nonUsernamePasswordCredentialsAreFilteredOut() throws Exception {
        addUsernamePassword("user-pass");
        addSecretText("secret-text");

        List<String> values = values(config.doFillDefaultCommentorCredentialsItems(null));

        assertTrue(values.contains("user-pass"));
        assertFalse(values.contains("secret-text"));
    }

    @Test
    void currentValueAlreadyInStoreIsNotDuplicated() throws Exception {
        addUsernamePassword("cred-1");

        List<String> values = values(config.doFillDefaultCommentorCredentialsItems("cred-1"));

        assertEquals(1, values.stream().filter("cred-1"::equals).count());
    }

    @Test
    void currentValueMissingFromStoreIsStillListed() throws Exception {
        addUsernamePassword("cred-1");

        List<String> values = values(config.doFillDefaultCommentorCredentialsItems("deleted-id"));

        assertTrue(values.contains("deleted-id"), "a saved-but-deleted id must stay visible");
        assertTrue(values.contains("cred-1"));
    }

    @Test
    void blankCurrentValueAddsNoExtraOption() {
        assertEquals(Collections.singletonList(""), values(config.doFillDefaultCommentorCredentialsItems("")));
    }

    @Test
    void explicitAdminSeesCredentialsWhenSecurityIsEnabled() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        try (ACLContext ignored = ACL.as2(User.getById("admin", true).impersonate2())) {
            List<String> values = values(config.doFillDefaultCommentorCredentialsItems(null));

            assertTrue(values.contains("cred-1"));
        }
    }

    @Test
    void nonAdminDoesNotSeeCredentials() throws Exception {
        addUsernamePassword("cred-1");
        addUsernamePassword("cred-2");
        enableSecurity();

        try (ACLContext ignored = ACL.as2(User.getById("alice", true).impersonate2())) {
            List<String> ids = credentialIds(config.doFillDefaultCommentorCredentialsItems(null));

            assertTrue(ids.isEmpty(), "non-admins must not enumerate credential ids, got " + ids);
        }
    }

    @Test
    void nonAdminOnlySeesTheirCurrentValue() throws Exception {
        addUsernamePassword("cred-1");
        addUsernamePassword("cred-2");
        enableSecurity();

        try (ACLContext ignored = ACL.as2(User.getById("alice", true).impersonate2())) {
            List<String> values = values(config.doFillDefaultCommentorCredentialsItems("cred-1"));

            assertEquals(Collections.singletonList("cred-1"), values);
        }
    }

    @Test
    void anonymousDoesNotSeeCredentials() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        try (ACLContext ignored = ACL.as2(Jenkins.ANONYMOUS2)) {
            assertTrue(credentialIds(config.doFillDefaultCommentorCredentialsItems(null))
                    .isEmpty());
        }
    }
}
