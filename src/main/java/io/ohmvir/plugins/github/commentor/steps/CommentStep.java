package io.ohmvir.plugins.github.commentor.steps;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import lombok.Getter;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public abstract class CommentStep extends Step {
    private @Getter String credentialsId;

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    public abstract static class CommentStepDescriptor extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class);
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();

            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
                return result.includeEmptyValue()
                        .includeMatchingAs(
                                ACL.SYSTEM2,
                                Jenkins.get(),
                                StandardUsernamePasswordCredentials.class,
                                Collections.emptyList(),
                                CredentialsMatchers.always())
                        .includeCurrentValue(credentialsId);
            }

            if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return result.includeCurrentValue(credentialsId);
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            item,
                            StandardUsernamePasswordCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }
    }
}
