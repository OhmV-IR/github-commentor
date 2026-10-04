package io.ohmvir.plugins.github.commentor.configuration;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

@Extension
public class GithubCommentorConfiguration extends GlobalConfiguration {
    private @Getter @Nullable String defaultCommentorCredentials;

    @DataBoundConstructor
    public GithubCommentorConfiguration(@Nullable String defaultCommentorCredentials) {
        this.defaultCommentorCredentials = defaultCommentorCredentials;
    }

    @DataBoundSetter
    public void setDefaultCommentorCredentials(String defaultCommentorCredentials) {
        this.defaultCommentorCredentials = defaultCommentorCredentials;
        save();
    }

    public GithubCommentorConfiguration(){
        load();
    }

    @Override
    public @NonNull String getDisplayName() {
        return "Github Commentor Configuration";
    }

    @POST
    public ListBoxModel doFillDefaultCommentorCredentialsItems(@QueryParameter String defaultCommentorCredentials) {
        StandardListBoxModel result = new StandardListBoxModel();

        if(!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return result.includeCurrentValue(defaultCommentorCredentials);
        }

        return result
                .includeEmptyValue()
                .includeMatchingAs(
                        ACL.SYSTEM2,
                        Jenkins.get(),
                        StandardUsernamePasswordCredentials.class,
                        Collections.emptyList(),
                        CredentialsMatchers.always()
                )
                .includeCurrentValue(defaultCommentorCredentials);
    }
}
