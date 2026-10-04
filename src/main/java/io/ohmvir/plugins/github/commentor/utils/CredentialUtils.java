package io.ohmvir.plugins.github.commentor.utils;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.AbortException;
import hudson.Util;
import hudson.model.Run;
import io.ohmvir.plugins.github.commentor.configuration.GithubCommentorConfiguration;

public class CredentialUtils {
    public static String resolveToken(Run<?, ?> run, String credentialsId) throws AbortException {
        String id = credentialsId;
        if(credentialsId == null){
            id = Util.fixEmptyAndTrim(GithubCommentorConfiguration.get().getDefaultCommentorCredentials());
        }
        if(id == null){
            throw new AbortException("Credentials not found");
        }

        StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(id, StandardUsernamePasswordCredentials.class, run);
        if(credentials == null){
            throw new AbortException("Credentials not found");
        }
        CredentialsProvider.track(run, credentials);
        return credentials.getPassword().getPlainText();
    }
}
