package io.ohmvir.plugins.github.commentor;

import lombok.Getter;

public class GithubComment {
    private @Getter final int id;
    private @Getter final String body;
    private @Getter final String user;

    public GithubComment(int id, String body, String user) {
        this.id = id;
        this.body = body;
        this.user = user;
    }
}
