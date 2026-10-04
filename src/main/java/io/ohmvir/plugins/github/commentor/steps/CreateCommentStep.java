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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jspecify.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;

public class CreateCommentStep extends IdentifierRequiredStep {
    private final @Getter String body;

    @DataBoundConstructor
    public CreateCommentStep(String repo, String identifier, CommentableResourceType identifierType, String body) {
        super(repo, identifier, identifierType);
        this.body = body;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Integer> {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient CreateCommentStep step;

        Execution(StepContext context, CreateCommentStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Integer run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);

            IdentifierValidator.validateRepo(step.getRepo());
            IdentifierValidator.validateIdentifier(step.getIdentifier(), step.getIdentifierType());

            if (step.body == null) {
                throw new AbortException("Body must be provided");
            }

            String token = CredentialUtils.resolveToken(run, step.getCredentialsId());
            JSONObject payload = new JSONObject().element("body", step.body);
            URI apiUrl =
                    switch (step.getIdentifierType()) {
                        case ISSUE, PULL_REQUEST ->
                            URI.create("https://api.github.com/repos/" + step.getRepo() + "/issues/"
                                    + step.getIdentifier() + "/comments");
                        case COMMIT ->
                            URI.create("https://api.github.com/repos/" + step.getRepo() + "/commits/"
                                    + step.getIdentifier() + "/comments");
                    };

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(apiUrl)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-Github-Api-Version", "2026-03-10")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
                throw new AbortException("Failed to call the github API for " + step.getRepo() + ": " + e);
            }

            if (response.statusCode() != 201) {
                throw new AbortException("Github returned HTTP " + response.statusCode() + " while commenting on "
                        + step.getRepo() + " with identifier " + step.getIdentifier() + "/" + step.getIdentifierType()
                        + ": "
                        + response.body());
            }
            listener.getLogger()
                    .println("Commented on " + step.getRepo() + " with identifier " + step.getIdentifier() + "/"
                            + step.getIdentifierType());
            JSONObject resJson = JSONObject.fromObject(response.body());
            return resJson.getInt("id");
        }
    }

    @Extension
    public static class DescriptorImpl extends CommentStepDescriptor {
        @Override
        public String getFunctionName() {
            return "createComment";
        }

        @Override
        public @NonNull String getDisplayName() {
            return "Create a comment on a github issue, pull request or commit";
        }
    }
}
