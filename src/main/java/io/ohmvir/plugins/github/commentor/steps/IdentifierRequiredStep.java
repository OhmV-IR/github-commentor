package io.ohmvir.plugins.github.commentor.steps;

import io.ohmvir.plugins.github.commentor.CommentableResourceType;
import lombok.Getter;

public abstract class IdentifierRequiredStep extends IdentifierTypeRequiredStep {
    private @Getter final String identifier;

    public IdentifierRequiredStep(String repo, String identifier, CommentableResourceType identifierType) {
        super(repo, identifierType);
        this.identifier = identifier;
    }
}
