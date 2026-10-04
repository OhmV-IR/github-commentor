package io.ohmvir.plugins.github.commentor.steps;

import lombok.Getter;

public abstract class RepoRequiredStep extends CommentStep {
    private @Getter final String repo;

    public RepoRequiredStep(String repo) {
        this.repo = repo;
    }
}
