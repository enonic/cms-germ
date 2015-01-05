package com.enonic.cms.plugin.germ.utils;

import com.enonic.cms.plugin.germ.model.RepoSettings;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.internal.storage.file.WindowCache;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by rfo on 19/03/14.
 */
public class GitUtils {
    Logger LOG = LoggerFactory.getLogger(GitUtils.class);

    public GitUtils(){

    }

    public Git getGitInstance(File repositoryFolder) throws IOException{
        return Git.open(repositoryFolder);
    }

    public void initGitRepository(File repositoryFolder) throws GitAPIException {
        Git git = Git.init().setDirectory(repositoryFolder).call();
        WindowCacheConfig windowCacheConfig = new WindowCacheConfig();
        windowCacheConfig.setPackedGitMMAP(false);
        windowCacheConfig.install();
        git.close();
    }

    public void deleteRepository(File repositoryFolder) throws GitAPIException, IOException {
        Repository repository = getRepository(repositoryFolder);
        repository.close();
        FileUtils.delete(repositoryFolder, FileUtils.RECURSIVE);
    }

    public String getGitConfigString(File repositoryFolder, String section, String subsection, String name) throws IOException{
        Repository repository = getRepository(repositoryFolder);
        StoredConfig config = repository.getConfig();
        String configString = config.getString(section,subsection,name);
        repository.close();
        return configString;
    }

    public void setGitConfigString(File repositoryFolder, String section, String subsection, String name, String value) throws IOException{
        Repository repository = getRepository(repositoryFolder);
        StoredConfig config = repository.getConfig();
        config.setString(section,subsection,name,value);
        repository.close();
    }

    public void reset(RepoSettings repoSettings, String sha1) throws IOException, GitAPIException{
        Repository repository = getRepository(repoSettings.getGitFolder());
        ResetCommand resetCommand = new Git(repository).reset();
        resetCommand.setMode(ResetCommand.ResetType.HARD);
        //resetCommand.addPath(repoSettings.getSparseCheckoutPath());
        resetCommand.setRef(sha1);
        resetCommand.call();

        StoredConfig config = repository.getConfig();
        config.setString("germ", "workspace", replaceIllegalGitConfCharacters(repository.getBranch()), sha1);
        config.save();
        repository.close();
    }

    /*This is because there are restrictions on variable names in .git/config file
    * https://www.kernel.org/pub/software/scm/git/docs/git-config.html*/
    public final String replaceIllegalGitConfCharacters(String confValue){
        return new PrettyPathNameCreator(true).generatePrettyPathName(confValue);
    }

    public FetchResult fetch(File repositoryFolder, String username, String password)
            throws URISyntaxException, IOException, GitAPIException{

        FetchResult fetchResult;
        Repository repository = getRepository(repositoryFolder);
        String branch = repository.getBranch();
        Config config = repository.getConfig();
        String refSpec = config.getString("remote", "origin", "fetch");

        RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
        RefSpec spec = new RefSpec(refSpec);
        FetchCommand fetchCommand = new Git(repository).fetch()
            .setRemote(remoteConfig.getName())
            .setRefSpecs(spec);

        if (!Strings.isNullOrEmpty(password) && !Strings.isNullOrEmpty(username)){
            UsernamePasswordCredentialsProvider credentialsProvider =
                    new UsernamePasswordCredentialsProvider(username,password);
            fetchResult = fetchCommand
                    .setTimeout(1000)
                    .setCredentialsProvider(credentialsProvider)
                    .call();
        }else{
            fetchResult  = fetchCommand.call();
        }
        repository.close();
        return fetchResult;
    }

    public CheckoutResult checkoutBranch(String branch, File repositoryFolder) throws GitAPIException, IOException{
        GitUtils gitUtils = new GitUtils();
        Git git = gitUtils.getGitInstance(repositoryFolder);

        CheckoutCommand checkoutCommand = git.checkout()
                .setName(getBranchSimpleName(branch));
        try{
            checkoutCommand.call();
        }catch (GitAPIException e){
            LOG.error(e.getMessage());
        }
        git.close();
        return checkoutCommand.getResult();
    }

    public CheckoutResult checkoutOrCreateBranch(String branch, File repositoryFolder) throws GitAPIException, IOException{
        CheckoutResult checkoutResult = null;
        GitUtils gitUtils = new GitUtils();
        Git git = gitUtils.getGitInstance(repositoryFolder);
        CreateBranchCommand createBranchCommand = git.branchCreate();
        createBranchCommand
            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
            .setName(getBranchSimpleName(branch))
            .setStartPoint("origin/"+getBranchSimpleName(branch));

        try {
            createBranchCommand.call();
        }catch (RefAlreadyExistsException e){
            LOG.info("Branch already exist, do checkout.");
        }
        try {
            checkoutResult = gitUtils.checkoutBranch(branch,repositoryFolder);
        }catch (Exception e){
            LOG.error("Exception in checkoutBranch: {}", e);
        }
        git.close();
        return checkoutResult;
    }


    public CheckoutResult checkoutFile(String file, String sha1, File repositoryFolder) throws GitAPIException, IOException{
        GitUtils gitUtils = new GitUtils();
        Git git = gitUtils.getGitInstance(repositoryFolder);

        CheckoutCommand checkoutCommand = git.checkout()
                .addPath(file)
                .setStartPoint(sha1);
        try{
            checkoutCommand.call();
        }catch (GitAPIException e){
            LOG.error(e.getMessage());
        }
        git.close();
        return checkoutCommand.getResult();
    }

    public File[] getRepositoryFiles(File repositoryFolder, FilenameFilter filter){
        return repositoryFolder.listFiles(filter);
    }

    public File[] getRepositoryFiles(File repositoryFolder){

        return repositoryFolder.listFiles();
    }

    public RevCommit[] getLocalCommits(File repositoryFolder) throws IOException, GitAPIException, NullPointerException{
        Repository repository = getRepository(repositoryFolder);
        int numberOfCommits = 100;

        Iterable<RevCommit> allLogs = new Git(repository)
                .log()
                .call();

        RevCommit[] allCommits = Iterables.toArray(allLogs, RevCommit.class);
        repository.close();

        return allCommits;
    }

    public RevCommit[] getRemoteCommits(File repositoryFolder) throws IOException, GitAPIException, NullPointerException{
        Repository repository = getRepository(repositoryFolder);
        String branch = repository.getBranch();
        int numberOfCommits = 100;

        ObjectId branchObject = repository.resolve("refs/remotes/origin/"+branch);
        LOG.info("branchObject : " + branchObject.getName());

        List<RevCommit> commits = new ArrayList<RevCommit>();
        RevCommit commit = null;
        RevWalk walk = new RevWalk(repository);
        walk.sort(RevSort.NONE);
        RevCommit head = walk.parseCommit(branchObject);
        walk.markStart(head);

        for (int i = 0; i<numberOfCommits;i++){
            commit = walk.next();
            commits.add(commit);
        }
        walk.dispose();

        RevCommit[] allCommits = Iterables.toArray(commits, RevCommit.class);
        repository.close();

        return allCommits;
    }

    public void addRemote(File repositoryFolder, String originUrl) throws IOException {
        Repository repository = getRepository(repositoryFolder);
        StoredConfig config = repository.getConfig();
        config.setString("remote", "origin", "url", originUrl);
        config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.save();
        repository.close();
    }

    public void removeRemote(File repositoryFolder) throws IOException {
        Repository repository = getRepository(repositoryFolder);
        StoredConfig config = repository.getConfig();
        config.unset("remote", "origin", "url");
        //config.unset("remote", "origin", "fetch");
        config.save();
        repository.close();
    }

    public String getRemoteOrigin(File repositoryFolder) throws IOException {
        Repository repository = getRepository(repositoryFolder);
        Config config = repository.getConfig();
        Set<String> remotes = config.getSubsections("remote");
        for (String remote : remotes) {
            if ("origin".equals(remote)) {
                String remoteOrigin = config.getString("remote", "origin", "url");
                repository.close();
                return remoteOrigin;
            }
        }
        repository.close();
        return null;
    }

    public Set<String> clean(File repositoryFolder) throws GitAPIException, IOException{
        Git git = getGitInstance(repositoryFolder);
        Set<String> result = git.clean().setCleanDirectories(true).call();
        git.close();
        return result;
    }

    public RebaseResult rebase(File repositoryFolder) throws GitAPIException, IOException{
        Git git = getGitInstance(repositoryFolder);
        RebaseCommand rebaseCommand = git.rebase();
        rebaseCommand.setUpstream("HEAD");
        RebaseResult rebaseResult = rebaseCommand.call();
        git.close();
        return rebaseResult;
    }
      
    public Status getStatus(File repositoryFolder) throws IOException, GitAPIException {
        Repository repository = getRepository(repositoryFolder);

        if (!repository.getDirectory().exists()) {
            throw new IOException("No git repository exists.");
        }
        Status status = new Git(repository).status().call();
        repository.close();

        return status;
    }

    public List<Ref> getRemoteBranches(File repositoryFolder) throws Exception{
        Git git = getGitInstance(repositoryFolder);
        ListBranchCommand listBranchCommand = git.branchList();
        List<Ref> remotebranches = listBranchCommand.setListMode(ListBranchCommand.ListMode.REMOTE).call();
        git.close();
        return remotebranches;
    }

    public List<Ref> getLocalBranches(File repositoryFolder) throws Exception{
        Git git = getGitInstance(repositoryFolder);
        ListBranchCommand listBranchCommand = git.branchList();
        List<Ref> localbranches = listBranchCommand.call();
        git.close();
        return localbranches;
    }

    public Repository getRepository(File repositoryFolder) throws IOException {

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder.setGitDir(repositoryFolder)
                .readEnvironment()
                .findGitDir()
                .build();
        return repository;
    }

    public String getBranchSimpleName(String branch){
        return StringUtils.substringAfterLast(branch,"/");
    }


}
