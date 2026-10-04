package io.ohmvir.plugins.github.commentor.steps;

import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import lombok.Getter;

public abstract class IdentifierTypeRequiredStep extends RepoRequiredStep {
    private @Getter final CommentableResourceType identifierType;

    public IdentifierTypeRequiredStep(String repo, CommentableResourceType identifierType) {
        super(repo);
        this.identifierType = identifierType;
    }
}
