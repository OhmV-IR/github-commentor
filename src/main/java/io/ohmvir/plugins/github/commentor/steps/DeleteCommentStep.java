package io.ohmvir.plugins.github.commentor.steps;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import io.ohmvir.plugins.github.commentor.utils.CredentialUtils;
import io.ohmvir.plugins.github.commentor.utils.IdentifierValidator;
import lombok.Getter;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jspecify.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DeleteCommentStep extends CommentStep {
    private final @Getter int commentId;

    @DataBoundConstructor
    public DeleteCommentStep(String repo, String identifier, CommentableResourceType identifierType, int commentId){
        super(repo, identifier, identifierType);
        this.commentId = commentId;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private final transient DeleteCommentStep step;
        @Serial
        private static final long serialVersionUID = 1L;

        protected Execution(@NonNull StepContext context, @NonNull DeleteCommentStep step) throws Exception {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);

            IdentifierValidator.validateRepo(step.getRepo());
            IdentifierValidator.validateIdentifier(step.getIdentifier(), step.getIdentifierType());

            String token = CredentialUtils.resolveToken(run, step.getCredentialsId());
            URI apiUrl = switch (step.getIdentifierType()){
                case ISSUE, PULL_REQUEST ->
                    URI.create("https://api.github.com/repos/" + step.getRepo() + "/issues/" + step.getIdentifier() + "/comments/" + step.getCommentId());
                case COMMIT ->
                    URI.create("https://api.github.com/repos/" + step.getRepo() + "/comments/" + step.getCommentId());
            };
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(apiUrl)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-Github-Api-Version", "2026-03-10")
                    .header("Content-Type", "application/json")
                    .DELETE()
                    .build();
            HttpResponse<Void> response;
            try (HttpClient client = HttpClient.newHttpClient()){
                response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            } catch (IOException e){
                e.printStackTrace(listener.getLogger());
                throw new AbortException("Failed to call the github API for " + step.getRepo() + ": " + e);
            }

            if(response.statusCode() != 204){
                throw new AbortException("Github returned HTTP " + response.statusCode() + " while deleting comment " + step.getCommentId() + " on "
                        + step.getRepo() + " with identifier " + step.getIdentifier() + "/" + step.getIdentifierType() + ": "
                        + response.body());
            }
            listener.getLogger().println("Deleted comment " + step.getCommentId() + " on repo " + step.getRepo() + " attached to identifier " + step.getIdentifier() + "/" + step.getIdentifierType());
            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends CommentStepDescriptor {
        @Override
        public String getFunctionName() {
            return "deleteComment";
        }

        @Override
        public @NonNull String getDisplayName() {
            return "Deletes a comment on a github issue, pull request or commit";
        }
    }
}
