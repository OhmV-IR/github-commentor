package io.ohmvir.plugins.github.commentor.utils;

import hudson.AbortException;
import io.ohmvir.plugins.github.commentor.CommentableResourceType;

import java.util.regex.Pattern;

public class IdentifierValidator {
    private static final Pattern REPO_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");

    public static void validateRepo(String repo) throws AbortException {
        if(repo == null){
            throw new AbortException("Invalid repo, null was passed");
        }
        if(!REPO_PATTERN.matcher(repo).matches()){
            throw new AbortException("Invalid repo format, expected owner/name but got " + repo);
        }
    }

    public static void validateIdentifier(String identifier, CommentableResourceType resourceType) throws AbortException {
        if(identifier == null){
            throw new AbortException("Didn't get an identifier");
        }
        switch(resourceType) {
            case ISSUE, PULL_REQUEST:
                try {
                    if (Integer.parseInt(identifier) <= 0) {
                        throw new AbortException("Invalid issue identifier");
                    }
                } catch (NumberFormatException e) {
                    throw new AbortException("Invalid issue identifier");
                }
                break;
            case COMMIT:
                if(!SHA256.matcher(identifier).matches()){
                    throw new AbortException("Invalid commit identifier");
                }
                break;
            case null, default:
                throw new AbortException("Invalid identifier resource type " + resourceType);
        }
    }
}
