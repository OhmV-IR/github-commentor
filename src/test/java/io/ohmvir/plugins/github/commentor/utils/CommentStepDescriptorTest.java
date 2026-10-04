package io.ohmvir.plugins.github.commentor.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.ohmvir.plugins.github.commentor.steps.CommentStep;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.verb.POST;

@WithJenkins
class CommentStepDescriptorTest {

    /**
     * Descriptor's no-arg constructor requires the descriptor to be nested in its Describable, so the
     * abstract class under test is exercised through a concrete descriptor nested in a dummy step.
     */
    private static class DummyStep extends Step {
        @Override
        public StepExecution start(StepContext context) {
            throw new UnsupportedOperationException("not executed in these tests");
        }

        static class DescriptorImpl extends CommentStep.CommentStepDescriptor {
            @Override
            public String getFunctionName() {
                return "dummyComment";
            }

            @Override
            public String getDisplayName() {
                return "Dummy comment";
            }
        }
    }

    private JenkinsRule j;
    private CommentStep.CommentStepDescriptor descriptor;
    private FreeStyleProject project;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        descriptor = new DummyStep.DescriptorImpl();
        project = j.createFreeStyleProject();
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

    /**
     * alice: Jenkins read only; bob: read on the job; carol: read and extended read on the job;
     * admin: administer everything.
     */
    private void enableSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ)
                .everywhere()
                .to("alice", "bob", "carol")
                .grant(Item.READ)
                .onItems(project)
                .to("bob", "carol")
                .grant(Item.EXTENDED_READ)
                .onItems(project)
                .to("carol")
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to("admin"));
    }

    private ListBoxModel fillAs(String user, Item item, String currentValue) {
        try (ACLContext ignored = ACL.as2(User.getById(user, true).impersonate2())) {
            return descriptor.doFillCredentialsIdItems(item, currentValue);
        }
    }

    private static List<String> values(ListBoxModel model) {
        return model.stream().map(o -> o.value).collect(Collectors.toList());
    }

    /** Option values excluding the blank "none" option, i.e. the actual credential ids. */
    private static List<String> credentialIds(ListBoxModel model) {
        return values(model).stream().filter(v -> v != null && !v.isEmpty()).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------
    // getRequiredContext / annotations
    // ---------------------------------------------------------------------

    @Test
    void requiredContextIsRunAndTaskListener() {
        assertEquals(Set.of(Run.class, TaskListener.class), Set.copyOf(descriptor.getRequiredContext()));
    }

    @Test
    void fillMethodRequiresPost() throws NoSuchMethodException {
        assertTrue(CommentStep.CommentStepDescriptor.class
                .getMethod("doFillCredentialsIdItems", Item.class, String.class)
                .isAnnotationPresent(POST.class));
    }

    // ---------------------------------------------------------------------
    // doFillCredentialsIdItems: global context (no ancestor item)
    // ---------------------------------------------------------------------

    @Test
    void globalContextWithNoCredentialsListsOnlyTheEmptyOption() {
        assertEquals(Collections.singletonList(""), values(descriptor.doFillCredentialsIdItems(null, null)));
    }

    @Test
    void globalContextAdminSeesUsernamePasswordCredentials() throws Exception {
        addUsernamePassword("cred-1");
        addUsernamePassword("cred-2");

        List<String> values = values(descriptor.doFillCredentialsIdItems(null, null));

        assertEquals("", values.get(0), "empty option should come first");
        assertTrue(values.contains("cred-1"));
        assertTrue(values.contains("cred-2"));
        assertEquals(3, values.size());
    }

    @Test
    void globalContextFiltersOutOtherCredentialTypes() throws Exception {
        addUsernamePassword("user-pass");
        addSecretText("secret-text");

        List<String> ids = credentialIds(descriptor.doFillCredentialsIdItems(null, null));

        assertEquals(Collections.singletonList("user-pass"), ids);
    }

    @Test
    void globalContextKeepsACurrentValueThatNoLongerExists() throws Exception {
        addUsernamePassword("cred-1");

        List<String> ids = credentialIds(descriptor.doFillCredentialsIdItems(null, "deleted-id"));

        assertTrue(ids.contains("deleted-id"), "a saved-but-deleted id must stay visible");
        assertTrue(ids.contains("cred-1"));
    }

    @Test
    void globalContextDoesNotDuplicateACurrentValueThatExists() throws Exception {
        addUsernamePassword("cred-1");

        List<String> values = values(descriptor.doFillCredentialsIdItems(null, "cred-1"));

        assertEquals(1, values.stream().filter("cred-1"::equals).count());
    }

    @Test
    void globalContextAdminSeesCredentialsWhenSecurityIsEnabled() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        assertTrue(credentialIds(fillAs("admin", null, null)).contains("cred-1"));
    }

    @Test
    void globalContextNonAdminDoesNotSeeCredentials() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        // carol can edit nothing globally, even though she has extended read on the job
        for (String user : new String[] {"alice", "bob", "carol"}) {
            List<String> ids = credentialIds(fillAs(user, null, null));

            assertTrue(ids.isEmpty(), user + " must not enumerate credential ids, got " + ids);
        }
    }

    @Test
    void globalContextNonAdminOnlySeesTheirCurrentValue() throws Exception {
        addUsernamePassword("cred-1");
        addUsernamePassword("cred-2");
        enableSecurity();

        assertEquals(Collections.singletonList("cred-1"), credentialIds(fillAs("alice", null, "cred-1")));
    }

    @Test
    void globalContextAnonymousDoesNotSeeCredentials() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        try (ACLContext ignored = ACL.as2(Jenkins.ANONYMOUS2)) {
            assertTrue(credentialIds(descriptor.doFillCredentialsIdItems(null, null))
                    .isEmpty());
        }
    }

    // ---------------------------------------------------------------------
    // doFillCredentialsIdItems: inside a job
    // ---------------------------------------------------------------------

    @Test
    void jobContextListsGlobalUsernamePasswordCredentials() throws Exception {
        addUsernamePassword("cred-1");
        addSecretText("secret-text");

        List<String> values = values(descriptor.doFillCredentialsIdItems(project, null));

        assertEquals("", values.get(0), "empty option should come first");
        assertTrue(values.contains("cred-1"));
        assertFalse(values.contains("secret-text"));
    }

    @Test
    void jobContextAdminSeesCredentials() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        assertTrue(credentialIds(fillAs("admin", project, null)).contains("cred-1"));
    }

    @Test
    void jobContextUserWithExtendedReadSeesCredentials() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        assertTrue(credentialIds(fillAs("carol", project, null)).contains("cred-1"));
    }

    @Test
    void jobContextUserWithoutExtendedReadDoesNotSeeCredentials() throws Exception {
        addUsernamePassword("cred-1");
        enableSecurity();

        // bob can read the job but not its configuration; alice cannot even read it
        for (String user : new String[] {"alice", "bob"}) {
            List<String> ids = credentialIds(fillAs(user, project, null));

            assertTrue(ids.isEmpty(), user + " must not enumerate credential ids, got " + ids);
        }
    }

    @Test
    void jobContextUnprivilegedUserOnlySeesTheirCurrentValue() throws Exception {
        addUsernamePassword("cred-1");
        addUsernamePassword("cred-2");
        enableSecurity();

        assertEquals(Collections.singletonList("cred-1"), credentialIds(fillAs("bob", project, "cred-1")));
    }

    @Test
    void jobContextKeepsACurrentValueThatNoLongerExists() throws Exception {
        addUsernamePassword("cred-1");

        List<String> ids = credentialIds(descriptor.doFillCredentialsIdItems(project, "deleted-id"));

        assertTrue(ids.contains("deleted-id"));
        assertTrue(ids.contains("cred-1"));
    }
}
