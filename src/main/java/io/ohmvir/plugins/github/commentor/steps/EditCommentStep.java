package io.ohmvir.plugins.github.commentor.steps;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import io.ohmvir.plugins.github.commentor.utils.CredentialUtils;
import io.ohmvir.plugins.github.commentor.utils.IdentifierValidator;
import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jspecify.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;

public class EditCommentStep extends IdentifierTypeRequiredStep {
    private final @Getter String newBody;
    private final @Getter int commentId;

    @DataBoundConstructor
    public EditCommentStep(String repo, CommentableResourceType identifierType, int commentId, String newBody) {
        super(repo, identifierType);
        this.commentId = commentId;
        this.newBody = newBody;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private final transient EditCommentStep step;

        @Serial
        private static final long serialVersionUID = 1L;

        protected Execution(@NonNull StepContext context, EditCommentStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);

            IdentifierValidator.validateRepo(step.getRepo());
            if (step.getIdentifierType() == null) {
                throw new AbortException("Identifier type not specified");
            }

            if (step.newBody == null) {
                throw new AbortException("New body must be provided");
            }

            String token = CredentialUtils.resolveToken(run, step.getCredentialsId());
            JSONObject payload = new JSONObject().element("body", step.newBody);
            URI apiUrl =
                    switch (step.getIdentifierType()) {
                        case ISSUE, PULL_REQUEST ->
                            URI.create("https://api.github.com/repos/" + step.getRepo() + "/issues/comments/"
                                    + step.getCommentId());
                        case COMMIT ->
                            URI.create("https://api.github.com/repos/" + step.getRepo() + "/comments/"
                                    + step.getCommentId());
                    };
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(apiUrl)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-Github-Api-Version", "2026-03-10")
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<Void> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
                throw new AbortException("Failed to call the github API for " + step.getRepo() + ": " + e);
            }

            if (response.statusCode() != 204) {
                throw new AbortException("Github returned HTTP " + response.statusCode() + " while editing comment on "
                        + step.getRepo() + " with identifier type " + step.getIdentifierType());
            }
            listener.getLogger()
                    .println("Edited comment on " + step.getRepo() + " with identifier type "
                            + step.getIdentifierType());
            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends CommentStepDescriptor {
        @Override
        public String getFunctionName() {
            return "editComment";
        }

        @Override
        public @NonNull String getDisplayName() {
            return "Edit an existing comment on a github issue, pull request or commit";
        }
    }
}
