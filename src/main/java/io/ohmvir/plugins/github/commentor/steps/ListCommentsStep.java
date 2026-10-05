package io.ohmvir.plugins.github.commentor.steps;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import io.ohmvir.plugins.github.commentor.GithubComment;
import io.ohmvir.plugins.github.commentor.utils.CredentialUtils;
import io.ohmvir.plugins.github.commentor.utils.IdentifierValidator;
import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jspecify.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class ListCommentsStep extends IdentifierRequiredStep {
    private @Getter String userFilter;

    @DataBoundConstructor
    public ListCommentsStep(String repo, String identifier, CommentableResourceType identifierType) {
        super(repo, identifier, identifierType);
    }

    @DataBoundSetter
    public void setUserFilter(String userFilter) {
        this.userFilter = Util.fixEmptyAndTrim(userFilter);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<List<GithubComment>> {
        private final transient ListCommentsStep step;

        @Serial
        private static final long serialVersionUID = 1L;

        public Execution(StepContext context, ListCommentsStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected List<GithubComment> run() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);

            IdentifierValidator.validateRepo(step.getRepo());
            IdentifierValidator.validateIdentifier(step.getIdentifier(), step.getIdentifierType());

            String token = CredentialUtils.resolveToken(run, step.getCredentialsId());
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
                    .GET()
                    .build();

            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                e.printStackTrace(listener.getLogger());
                throw new AbortException("Failed to call the github API for " + step.getRepo() + ": " + e);
            }

            if (response.statusCode() != 200) {
                throw new AbortException("Github returned HTTP " + response.statusCode()
                        + " while listing comments on " + step.getRepo() + " with identifier "
                        + step.getIdentifier() + "/" + step.getIdentifierType() + ": " + response.body());
            }

            JSONArray comments = JSONArray.fromObject(response.body());
            List<GithubComment> result = new ArrayList<>();
            for (int i = 0; i < comments.size(); i++) {
                JSONObject comment = comments.getJSONObject(i);
                String commentUser = comment.getJSONObject("user").getString("login");
                if (step.userFilter != null && !step.userFilter.equals(commentUser)) {
                    continue;
                }
                result.add(new GithubComment(comment.getInt("id"), comment.getString("body"), commentUser));
            }

            listener.getLogger()
                    .println("Listed " + result.size() + " comment(s) on " + step.getRepo() + " with identifier "
                            + step.getIdentifier() + "/" + step.getIdentifierType());
            return result;
        }
    }

    @Extension
    public static class DescriptorImpl extends CommentStepDescriptor {
        @Override
        public String getFunctionName() {
            return "listComments";
        }

        @Override
        public @NonNull String getDisplayName() {
            return "Lists the comments on an issue, pull request or commit with optional filters for user.";
        }
    }
}
