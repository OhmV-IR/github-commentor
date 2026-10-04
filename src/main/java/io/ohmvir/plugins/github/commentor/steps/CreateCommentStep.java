package io.ohmvir.plugins.github.commentor.steps;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.ohmvir.plugins.github.commentor.utils.CommentStepDescriptor;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import io.ohmvir.plugins.github.commentor.utils.CredentialUtils;
import io.ohmvir.plugins.github.commentor.utils.IdentifierValidator;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jspecify.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class CreateCommentStep extends Step {

    private final @Getter String repo;
    private final @Getter String identifier;
    private final @Getter CommentableResourceType identifierType;
    private final @Getter String body;
    private @Getter String credentialsId;

    @DataBoundConstructor
    public CreateCommentStep(String repo, String identifier, CommentableResourceType identifierType, String body) {
        this.repo = repo;
        this.identifier = identifier;
        this.identifierType = identifierType;
        this.body = body;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<String> {
        private static final long serialVersionUID = 1L;

        private final transient CreateCommentStep step;

        Execution(StepContext context, CreateCommentStep step){
            super(context);
            this.step = step;
        }

        @Override
        protected String run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);

            IdentifierValidator.validateRepo(step.repo);
            IdentifierValidator.validateIdentifier(step.identifier, step.identifierType);

            if(step.body == null){
                throw new AbortException("Body must be provided");
            }

            String token = CredentialUtils.resolveToken(run, step.credentialsId);
            JSONObject payload = new JSONObject().element("body", step.body);
            URI apiUrl = switch(step.identifierType){
                case ISSUE, PULL_REQUEST -> URI.create("https://api.github.com/repos/" + step.repo + "/issues/" + step.identifier + "/comments");
                case COMMIT -> URI.create("https://api.github.com/repos/" + step.repo + "/commits/" + step.identifier + "/comments");
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
                throw new AbortException("Failed to call the github API for " + step.repo + ": " + e);
            }

            if(response.statusCode() != 201){
                throw new AbortException("Github returned HTTP " + response.statusCode() + " while commenting on " + step.repo + " with identifier " + step.identifier + "/" + step.identifierType + ": " + response.body());
            }
            listener.getLogger().println("Commented on " + step.repo + " with identifier " + step.identifier + "/" + step.identifierType);
            JSONObject resJson = JSONObject.fromObject(response.body());
            return resJson.getString("id");
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
