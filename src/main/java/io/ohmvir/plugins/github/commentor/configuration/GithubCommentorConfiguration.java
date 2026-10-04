package io.ohmvir.plugins.github.commentor.configuration;

import jenkins.model.GlobalConfiguration;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class GithubCommentorConfiguration extends GlobalConfiguration {
    private @Getter @Setter(onMethod_ = @DataBoundSetter) @Nullable String defaultCommentorCredentials;

    @DataBoundConstructor
    public GithubCommentorConfiguration(@Nullable String defaultCommentorCredentials) {
        this.defaultCommentorCredentials = defaultCommentorCredentials;
    }

    public GithubCommentorConfiguration(){
        this.defaultCommentorCredentials = null;
    }

    @Override
    public @NonNull String getDisplayName() {
        return "Github Commentor Configuration";
    }
}
