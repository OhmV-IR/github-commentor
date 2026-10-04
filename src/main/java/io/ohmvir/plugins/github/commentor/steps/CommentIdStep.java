package io.ohmvir.plugins.github.commentor.steps;

import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import lombok.Getter;

public abstract class CommentIdStep extends IdentifierTypeRequiredStep {
    private @Getter final int commentId;

    public CommentIdStep(String repo, CommentableResourceType identifierType, int commentId) {
        super(repo, identifierType);
        this.commentId = commentId;
    }
}
