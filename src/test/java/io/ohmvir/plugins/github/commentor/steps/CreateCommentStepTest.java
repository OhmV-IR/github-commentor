package io.ohmvir.plugins.github.commentor.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import java.util.Set;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests everything that happens before the HTTP call. The request URL is hard-coded to
 * api.github.com, so every scenario here must fail validation or credential lookup first and never
 * reach the network. The success and HTTP-error paths need a configurable base URL (see notes).
 */
@WithJenkins
class CreateCommentStepTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** The only place that calls the constructor, so a signature change touches one line. */
    private static CreateCommentStep newStep(String credentialsId) {
        CreateCommentStep step =
                new CreateCommentStep("octocat/hello-world", "12", CommentableResourceType.ISSUE, "hello");
        step.setCredentialsId(credentialsId);
        return step;
    }

    private static String script(String repo, String identifier, String type, String credentialsId) {
        String credentials = credentialsId == null ? "" : ", credentialsId: '" + credentialsId + "'";
        return "createComment repo: '" + repo + "', identifier: '" + identifier + "', identifierType: '" + type
                + "', body: 'hello'" + credentials;
    }

    private WorkflowRun runExpectingFailure(String pipelineScript) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun run = job.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, run);
        return run;
    }

    // ---------------------------------------------------------------------
    // data binding
    // ---------------------------------------------------------------------

    @Test
    void gettersReturnTheConstructorValues() {
        CreateCommentStep step =
                new CreateCommentStep("octocat/hello-world", "abc", CommentableResourceType.COMMIT, "the body");
        step.setCredentialsId("my-cred");

        assertEquals("octocat/hello-world", step.getRepo());
        assertEquals("abc", step.getIdentifier());
        assertEquals(CommentableResourceType.COMMIT, step.getIdentifierType());
        assertEquals("the body", step.getBody());
        assertEquals("my-cred", step.getCredentialsId());
    }

    @Test
    void setterTrimsCredentialsId() {
        CreateCommentStep step = newStep(null);

        step.setCredentialsId("  my-cred \n");

        assertEquals("my-cred", step.getCredentialsId());
    }

    @Test
    void setterTurnsBlankCredentialsIdIntoNull() {
        CreateCommentStep step = newStep("my-cred");

        for (String blank : new String[] {"", "   ", "\t\n", null}) {
            step.setCredentialsId(blank);

            assertNull(step.getCredentialsId(), "blank '" + blank + "' should become null");
        }
    }

    /**
     * Pipeline binds through the constructor, not the setter, so a blank credentialsId from a script
     * currently bypasses normalisation and never falls back to the default credentials. Fails until
     * the constructor normalises too (or credentialsId leaves the constructor).
     */
    @Test
    void constructorNormalisesBlankCredentialsId() {
        assertNull(newStep("").getCredentialsId());
        assertNull(newStep("   ").getCredentialsId());
        assertEquals("my-cred", newStep(" my-cred ").getCredentialsId());
    }

    // ---------------------------------------------------------------------
    // descriptor
    // ---------------------------------------------------------------------

    @Test
    void stepIsRegisteredUnderItsPipelineName() {
        StepDescriptor descriptor = StepDescriptor.byFunctionName("createComment");

        assertNotNull(descriptor, "createComment should be a known Pipeline step");
        assertInstanceOf(CreateCommentStep.DescriptorImpl.class, descriptor);
    }

    @Test
    void descriptorIsAnExtension() {
        assertNotNull(ExtensionList.lookupSingleton(CreateCommentStep.DescriptorImpl.class));
    }

    @Test
    void descriptorHasAFunctionNameAndADisplayName() {
        CreateCommentStep.DescriptorImpl descriptor = new CreateCommentStep.DescriptorImpl();

        assertEquals("createComment", descriptor.getFunctionName());
        assertTrue(descriptor.getDisplayName().toLowerCase().contains("comment"));
    }

    @Test
    void descriptorRequiresRunAndTaskListener() {
        CreateCommentStep.DescriptorImpl descriptor = new CreateCommentStep.DescriptorImpl();

        assertEquals(Set.of(Run.class, TaskListener.class), Set.copyOf(descriptor.getRequiredContext()));
    }

    // ---------------------------------------------------------------------
    // execution: failures that happen before any HTTP call
    // ---------------------------------------------------------------------

    @Test
    void invalidRepoFailsTheBuild() throws Exception {
        WorkflowRun run = runExpectingFailure(script("not a repo", "12", "ISSUE", "any-cred"));

        j.assertLogContains("Invalid repo", run);
    }

    @Test
    void invalidIssueNumberFailsTheBuild() throws Exception {
        WorkflowRun run = runExpectingFailure(script("octocat/hello-world", "0", "ISSUE", "any-cred"));

        j.assertLogContains("Invalid issue identifier", run);
    }

    @Test
    void invalidPullRequestNumberFailsTheBuild() throws Exception {
        WorkflowRun run = runExpectingFailure(script("octocat/hello-world", "-3", "PULL_REQUEST", "any-cred"));

        j.assertLogContains("Invalid issue identifier", run);
    }

    @Test
    void invalidCommitHashFailsTheBuild() throws Exception {
        WorkflowRun run = runExpectingFailure(script("octocat/hello-world", "abc123", "COMMIT", "any-cred"));

        j.assertLogContains("Invalid commit identifier", run);
    }

    @Test
    void unknownIdentifierTypeFailsTheBuild() throws Exception {
        WorkflowRun run = runExpectingFailure(script("octocat/hello-world", "12", "NOPE", "any-cred"));

        j.assertLogContains("NOPE", run);
    }

    @Test
    void unknownCredentialsFailTheBuild() throws Exception {
        WorkflowRun run = runExpectingFailure(script("octocat/hello-world", "12", "ISSUE", "does-not-exist"));

        j.assertLogContains("Credentials not found", run);
    }

    @Test
    void validationHappensBeforeCredentialLookup() throws Exception {
        // both the repo and the credentials are bad; the repo error must win
        WorkflowRun run = runExpectingFailure(script("not a repo", "12", "ISSUE", "does-not-exist"));

        j.assertLogContains("Invalid repo", run);
        j.assertLogNotContains("Credentials not found", run);
    }

    @Test
    void identifierValidationHappensBeforeCredentialLookup() throws Exception {
        WorkflowRun run = runExpectingFailure(script("octocat/hello-world", "0", "ISSUE", "does-not-exist"));

        j.assertLogContains("Invalid issue identifier", run);
        j.assertLogNotContains("Credentials not found", run);
    }

    /**
     * credentialsId is optional in the Pipeline script and falls back to the default; with no default
     * configured the build must fail with "Credentials not found". If credentialsId is still a required
     * constructor parameter, Pipeline rejects the call earlier ("Missing required parameter") and this
     * test fails, which means the default-credentials fallback is unreachable from Pipeline.
     */
    @Test
    void omittedCredentialsIdWithNoDefaultFailsWithCredentialsNotFound() throws Exception {
        WorkflowRun run = runExpectingFailure(script("octocat/hello-world", "12", "ISSUE", null));

        j.assertLogContains("Credentials not found", run);
    }
}
