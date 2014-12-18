package com.enonic.cms.plugin.germ.model;


import java.io.File;

public class RepoSettings {
    public File getFolder() {
        return folder;
    }

    public void setFolder(File folder) {
        this.folder = folder;
    }

    public File getGitFolder() {
        return gitFolder;
    }

    public void setGitFolder(File gitFolder) {
        this.gitFolder = gitFolder;
    }

    public String getSparseCheckoutPath() {
        return sparseCheckoutPath;
    }

    public void setSparseCheckoutPath(String sparseCheckoutPath) {
        this.sparseCheckoutPath = sparseCheckoutPath;
    }

    File folder;
    File gitFolder;

    String sparseCheckoutPath;



}
