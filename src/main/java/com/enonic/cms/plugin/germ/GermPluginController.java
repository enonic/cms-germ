package com.enonic.cms.plugin.germ;

import com.enonic.cms.api.client.Client;
import com.enonic.cms.api.plugin.PluginConfig;
import com.enonic.cms.api.plugin.PluginEnvironment;
import com.enonic.cms.api.plugin.ext.http.HttpController;
import com.enonic.cms.plugin.germ.model.RepoSettings;
import com.enonic.cms.plugin.germ.utils.GitUtils;
import com.enonic.cms.plugin.germ.utils.Helper;
import com.enonic.cms.plugin.germ.utils.ResponseMessage;
import com.enonic.cms.plugin.germ.utils.systemcommand.SystemCommandExecutor;
import com.enonic.cms.plugin.germ.view.TemplateEngineProvider;
import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.xpath.XPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.WebContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class GermPluginController extends HttpController {

    public GermPluginController() throws Exception {
        setDisplayName("G.E.R.M - Git Enonic Release Management");
        setUrlPatterns(new String[]{"/admin/site/[\\d]*/germ.*"});
        setPriority(10);

        pluginsFilenameFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                String lowercaseName = name.toLowerCase();
                if (lowercaseName.endsWith(".jar")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
    }

    public static final RepoSettings pluginRepoSettings = new RepoSettings();
    public static final RepoSettings resourcesRepoSettings = new RepoSettings();

    String pathToGit;
    String allowedAdminGroupKey;
    String allowedUserGroupKey;

    @Autowired
    Client client;

    @Autowired
    PluginEnvironment pluginEnvironment;

    PluginConfig pluginConfig;
    FilenameFilter pluginsFilenameFilter;

    boolean needsAuthentication;

    //TODO: This should perhaps not be global
    String url = "";

    @Autowired
    public void setPluginConfig(List<PluginConfig> pluginConfig) {
        //TODO: Strange hack with List<PluginConfig> here, srs is investigating
        this.pluginConfig = pluginConfig.get(0);
        this.needsAuthentication = this.pluginConfig.getBoolean("needsAuthentication", true);
        this.allowedAdminGroupKey = this.pluginConfig.getString("allowedAdminGroupKey");
        this.allowedUserGroupKey = this.pluginConfig.getString("allowedUserGroupKey");

        resourcesRepoSettings.setFolder(new File(this.pluginConfig.getString("folderWithResources")));
        resourcesRepoSettings.setGitFolder(new File(resourcesRepoSettings.getFolder() + "/.git"));
        resourcesRepoSettings.setSparseCheckoutPath(this.pluginConfig.getString("sparseCheckoutResourcesPath"));

        pluginRepoSettings.setFolder(new File(this.pluginConfig.getString("folderWithPlugins")));
        pluginRepoSettings.setGitFolder(new File(pluginRepoSettings.getFolder() + "/.git"));
        pluginRepoSettings.setSparseCheckoutPath(this.pluginConfig.getString("sparseCheckoutPluginPath"));

        pathToGit = this.pluginConfig.getString("pathToGit");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TemplateEngineProvider templateEngineProvider;

    Logger LOG = LoggerFactory.getLogger(GermPluginController.class);

    static ConcurrentHashMap<String, List> messages = new ConcurrentHashMap<String, List>();

    private void attemptAuthentication() {

        String cmd = pluginEnvironment.getCurrentRequest().getParameter("cmd");

        if (Strings.isNullOrEmpty(cmd) || !"authenticate".equalsIgnoreCase(cmd)) {
            return;
        }

        String germUsername = pluginEnvironment.getCurrentRequest().getParameter("germusername");
        String germPassword = pluginEnvironment.getCurrentRequest().getParameter("germpassword");

        if (Strings.isNullOrEmpty(germUsername) || Strings.isNullOrEmpty(germPassword)) {
            return;
        }

        try {
            client.logout(false);
            LOG.info("Try to log in {}/{}", germUsername, germPassword);
            String username = client.login(germUsername, germPassword);
            LOG.info("Logged in user {}", username);
        } catch (Exception e) {
            addErrorMessage("Authentication failed");
        }
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //Add multiple response messages with addInfoMessage(), addWarningMessage() or addErrorMessage();
        List<ResponseMessage> responseMessages = new ArrayList<ResponseMessage>();
        messages.put(pluginEnvironment.getCurrentSession().getId(), responseMessages);

        if (needsAuthentication) {
            attemptAuthentication();
        }

        String username = client.getUserName();
        Document userContext = client.getUserContext();
        Boolean adminGroupMembership = false;
        Boolean userGroupMembership = false;

        if (needsAuthentication) {
            try {
                Element adminEl = ((Element) XPath.selectSingleNode(userContext, "//user/memberships/group[@key='" + this.allowedAdminGroupKey + "']"));
                if (adminEl != null) {
                    adminGroupMembership = new Boolean(adminEl.getAttributeValue("direct-membership"));
                }
                Element userEl = ((Element) XPath.selectSingleNode(userContext, "//user/memberships/group[@key='" + this.allowedUserGroupKey + "']"));
                if (userEl != null) {
                    userGroupMembership = new Boolean(userEl.getAttributeValue("direct-membership"));
                }
            } catch (Exception e) {
                LOG.info("Access denied " + e.getMessage());
            }
            LOG.info("*********************Username************************");
            LOG.info(client.getUserName());
        }


        //Set parameters on context to make them available in Thymeleaf html view
        WebContext context = new WebContext(request, response, request.getSession().getServletContext());

        String url = extractUrlFromRequest();

        String requestPath = StringUtils.substringAfterLast(pluginEnvironment.getCurrentRequest().getRequestURI(), "/germ/");
        context.setVariable("requestPath", requestPath);
        context.setVariable("url", url);

        if (needsAuthentication && !adminGroupMembership) {
            addErrorMessage("Access denied");
            context.setVariable("accessDenied", true);
        }else {
            context.setVariable("accessDenied", false);
            addRequestPathContext(requestPath, context);
        }

        response.setContentType("text/html");

        context.setVariable("messages", messages.get(pluginEnvironment.getCurrentSession().getId()));
        messages.remove(pluginEnvironment.getCurrentSession().getId());
        try {
            templateEngineProvider.setApplicationContext(applicationContext);
            templateEngineProvider.get().process("germ", context, response.getWriter());
        } catch (Exception e) {
            addErrorMessage(e.getMessage());
            templateEngineProvider.get().process("errors/404", context, response.getWriter());
        }
        client.logout();
    }

    public void runCmd(Repository repository, RepoSettings repoSettings, WebContext context) {
        GitUtils gitUtils = new GitUtils();
        String cmd = pluginEnvironment.getCurrentRequest().getParameter("cmd");
        try {
            if (!Strings.isNullOrEmpty(cmd)) {
                if ("init".equals(cmd)) {
                    gitUtils.initGitRepository(repoSettings.getFolder());
                    addInfoMessage("Git repository successfully initiated in " + repoSettings.getGitFolder());
                } else if ("deleterepo".equals(cmd)) {
                    gitUtils.deleteRepository(repoSettings.getGitFolder());
                    addInfoMessage("Git repository successfully deleted");
                } else if ("rmremote".equals(cmd)) {
                    gitUtils.removeRemote(repoSettings.getGitFolder());
                    addInfoMessage("Remote origin successfully removed");
                } else if ("addremote".equals(cmd)) {
                    String originUrl = pluginEnvironment.getCurrentRequest().getParameter("originUrl");
                    if (Strings.isNullOrEmpty(originUrl)) {
                        addWarningMessage("OriginUrl missing");
                        return;
                    }
                    gitUtils.addRemote(repoSettings.getGitFolder(), originUrl);
                    addInfoMessage("Remote origin " + originUrl + " successfully added");
                } else if ("fetch".equals(cmd)) {
                    String gitusername = pluginEnvironment.getCurrentRequest().getParameter("gitusername");
                    String gitpassword = pluginEnvironment.getCurrentRequest().getParameter("gitpassword");
                    FetchResult fetchResult = gitUtils.fetch(repoSettings.getGitFolder(), gitusername, gitpassword);
                    if (fetchResult != null && fetchResult.getMessages().length() > 0) {
                        addInfoMessage(fetchResult.getMessages());
                    }
                    addInfoMessage("Fetch from remote origin " + fetchResult.getURI() + " was successful.");
                } else if ("checkout".equals(cmd)) {
                    String branch = pluginEnvironment.getCurrentRequest().getParameter("branch");
                    if (Strings.isNullOrEmpty(branch)) {
                        addWarningMessage("'" + branch + "' is not a valid branch for checkout");
                        return;
                    }
                    try {
                        LOG.info("Check wether to do normal or sparse checkout");
                        if (repository.getConfig().getBoolean("core", null, "sparsecheckout", false)) {
                            LOG.info("System command checkout");
                            branch = StringUtils.substringAfterLast(branch, "/");
                            List<String> command = new ArrayList<String>();
                            command.add("cmd.exe");
                            command.add("/c");
                            command.add("cd " + repoSettings.getFolder() + " && " + pathToGit + " checkout -f " + branch);
                            SystemCommandExecutor commandExecutor = new SystemCommandExecutor(command);
                            int result = commandExecutor.executeCommand();

                            // get the stdout and stderr from the command that was run
                            StringBuilder stdout = commandExecutor.getStandardOutputFromCommand();
                            StringBuilder stderr = commandExecutor.getStandardErrorFromCommand();

                            LOG.info("The numeric result of the command was {}", result);
                            LOG.info("Std.out: {}", stdout);
                            LOG.info("Std.err: {}", stderr);
                        } else {
                            LOG.info("Normal checkout with jgit");
                            CheckoutResult checkoutResult = gitUtils.checkoutOrCreateBranch(branch, repoSettings.getGitFolder());
                            context.setVariable("checkoutResult", checkoutResult);
                            addInfoMessage("Successfully checked out " + branch);
                        }
                    } catch (Exception e) {
                        LOG.error("Exception {}", e);
                        addErrorMessage(e.getMessage());
                    }
                } else if ("checkoutfile".equals(cmd)) {
                    String checkoutfile = pluginEnvironment.getCurrentRequest().getParameter("checkoutfile");
                    String checkoutfile_sha1 = pluginEnvironment.getCurrentRequest().getParameter("sha1");
                    if (Strings.isNullOrEmpty(checkoutfile)) {
                        addWarningMessage("'" + checkoutfile + "' is not a valid file for checkout");
                        return;
                    }
                    if (Strings.isNullOrEmpty(checkoutfile_sha1)) {
                        addWarningMessage("'" + checkoutfile_sha1 + "' is not a valid git commit");
                        return;
                    }

                    try {
                        CheckoutResult checkoutResult = gitUtils.checkoutFile(checkoutfile, checkoutfile_sha1, repoSettings.getGitFolder());
                        context.setVariable("checkoutResult", checkoutResult);
                        addInfoMessage("Successfully checked out " + checkoutfile);
                    } catch (Exception e) {
                        addErrorMessage(e.getMessage());
                    }
                } else if ("rebase".equals(cmd)) {
                    try {
                        RebaseResult rebaseResult = gitUtils.rebase(repoSettings.getGitFolder());
                        context.setVariable("rebaseResult", rebaseResult);
                    } catch (Exception e) {
                        addErrorMessage(e.getMessage());
                    }
                } else if ("clean".equals(cmd)) {
                    Set<String> cleanedFiles = gitUtils.clean(repoSettings.getGitFolder());
                    context.setVariable("cleanedFiles", cleanedFiles);
                } else if ("reset".equals(cmd)) {
                    String sha1 = pluginEnvironment.getCurrentRequest().getParameter("sha1");
                    if (Strings.isNullOrEmpty(sha1)) {
                        addWarningMessage("SHA-1 is not set, cannot reset.");
                    }

                    gitUtils.reset(repoSettings, sha1, pathToGit);

                    addInfoMessage("Successfully reset to " + sha1);
                } else if ("enablesparsecheckout".equals(cmd)) {
                    LOG.info("Enable sparse checkout for {}", repoSettings.getGitFolder());
                    StoredConfig config = repository.getConfig();
                    config.setBoolean("core", null, "sparsecheckout", true);
                    try {
                        Path infoFolder = FileSystems.getDefault().getPath(repoSettings.getGitFolder() + "/info");

                        Path sparsecheckoutFile = FileSystems.getDefault().getPath(repoSettings.getGitFolder() + "/info/sparse-checkout");
                        if (!Files.isDirectory(infoFolder, LinkOption.NOFOLLOW_LINKS)) {
                            /*Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxrwxrwx");
                            FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(permissions);
                            infoFolder = Files.createDirectory(infoFolder, fileAttributes);*/
                            infoFolder = Files.createDirectory(infoFolder);
                            LOG.info("Created directory {}", infoFolder.toAbsolutePath());
                        }
                        if (!Files.exists(sparsecheckoutFile)) {
                            Files.write(sparsecheckoutFile, repoSettings.getSparseCheckoutPath().getBytes());
                            LOG.info("Created file {}", sparsecheckoutFile.toAbsolutePath());
                        }
                        config.save();
                    } catch (Exception e) {
                        LOG.error("Exception while enabeling sparse-checkout: {}", e);
                    }
                    addInfoMessage("Enabled sparse-checkout for " + repoSettings.getGitFolder());
                } else if ("disablesparsecheckout".equals(cmd)) {
                    LOG.info("Disable sparse checkout for {}", repoSettings.getGitFolder());
                    StoredConfig config = repository.getConfig();
                    config.setBoolean("core", null, "sparsecheckout", false);
                    config.save();
                    addInfoMessage("Disabled sparse-checkout for " + repoSettings.getGitFolder());
                } else if ("setissuetrackerurl".equals(cmd)) {
                    String issuetrackerurl = pluginEnvironment.getCurrentRequest().getParameter("issuetrackerurl");
                    //repoSettings.setIssueTrackerUrl(issuetrackerurl);
                    LOG.info("Set issuetracker url for {}", repoSettings.getGitFolder());
                    StoredConfig config = repository.getConfig();
                    config.setString("germ", "workspace", "issuetrackerurl", issuetrackerurl);
                    config.save();
                    addInfoMessage("Added issuetracker url for " + repoSettings.getGitFolder());
                } else if ("setissuetrackerlinkpattern".equals(cmd)) {
                    String issuetrackerlinkpattern = pluginEnvironment.getCurrentRequest().getParameter("issuetrackerlinkpattern");
                    //repoSettings.setIssueTrackerLinkPattern(issuetrackerlinkpattern);
                    LOG.info("Set issuetracker link pattern for {}", repoSettings.getGitFolder());
                    StoredConfig config = repository.getConfig();
                    config.setString("germ", "workspace", "issuetrackerlinkpattern", issuetrackerlinkpattern);
                    config.save();
                    addInfoMessage("Added issuetracker link pattern for " + repoSettings.getGitFolder());
                } else if ("setschedulerauthentication".equals(cmd)) {
                    String gitschedulerusername = pluginEnvironment.getCurrentRequest().getParameter("gitschedulerusername");
                    String gitschedulerpassword = pluginEnvironment.getCurrentRequest().getParameter("gitschedulerpassword");
                    StoredConfig config = repository.getConfig();
                    config.setString("germ", "workspace", "gitschedulerusername", gitschedulerusername);
                    config.setString("germ", "workspace", "gitschedulerpassword", Helper.encryptPassword(gitschedulerpassword));
                    config.save();
                    addInfoMessage("Added scheduler authentication for " + repoSettings.getGitFolder());
                }
            }
        } catch (Exception e) {
            addErrorMessage(e.getMessage());
        }
    }

    public void addCommonContext(WebContext context, File repositoryFolder) {
        GitUtils gitUtils = new GitUtils();

        try {
            List<Ref> localbranches = gitUtils.getLocalBranches(repositoryFolder);
            context.setVariable("localbranches", localbranches);
        } catch (Exception e) {
            LOG.info("No local branches: " + e.getMessage());
        }

        try {
            List<Ref> remotebranches = gitUtils.getRemoteBranches(repositoryFolder);
            context.setVariable("remotebranches", remotebranches);
        } catch (Exception e) {
            LOG.info("No remote branches: " + e.getMessage());
        }

        try {
            RevCommit[] localBranchCommits = gitUtils.getLocalCommits(repositoryFolder);
            context.setVariable("localBranchCommits", localBranchCommits);
        } catch (Exception e) {
            LOG.info("getLocalCommits: " + e.getMessage());
        }

        try {
            RevCommit[] remoteBranchCommits = gitUtils.getRemoteCommits(repositoryFolder);
            context.setVariable("remoteBranchCommits", remoteBranchCommits);
        } catch (Exception e) {
            LOG.info("getRemoteCommits: " + e.getMessage());
        }

        try {
            Status status = gitUtils.getStatus(repositoryFolder);
            context.setVariable("status", status);
        } catch (Exception e) {
            LOG.info("No status:" + e.getMessage());
        }

        try {
            context.setVariable("resourcesRepository", gitUtils.getRepository(resourcesRepoSettings.getGitFolder()));
            context.setVariable("resourcesRemoteUrl", gitUtils.getRemoteOrigin(resourcesRepoSettings.getGitFolder()));
            context.setVariable("resourcesRemoteBranches", gitUtils.getRemoteBranches(resourcesRepoSettings.getGitFolder()));
        } catch (Exception e) {
            LOG.info(e.getMessage());
        }
        try {
            context.setVariable("pluginsRepository", gitUtils.getRepository(pluginRepoSettings.getGitFolder()));
            context.setVariable("pluginsRemoteUrl", gitUtils.getRemoteOrigin(pluginRepoSettings.getGitFolder()));
            context.setVariable("pluginsRemoteBranches", gitUtils.getRemoteBranches(pluginRepoSettings.getGitFolder()));
        } catch (Exception e) {
            LOG.info(e.getMessage());
        }

        try {
            StoredConfig config = gitUtils.getRepository(repositoryFolder).getConfig();
            context.setVariable("issuetrackerurl", config.getString("germ", "workspace", "issuetrackerurl"));
            context.setVariable("issuetrackerlinkpattern", config.getString("germ", "workspace", "issuetrackerlinkpattern"));
        } catch (Exception e) {

        }

        if (!"anonymous".equals(client.getUserName())){
            context.setVariable("germusername", client.getUserName());
        }

    }

    public void pluginsStatus(WebContext context) {
        plugins(context);
    }

    public void resourcesStatus(WebContext context) {
        resources(context);
    }

    public void resourcesRemote(WebContext context) {
        resources(context);
    }

    public void pluginsRemote(WebContext context) {
        plugins(context);
    }

    public void pluginsFiles(WebContext context) {
        GitUtils gitUtils = new GitUtils();
        plugins(context);
        context.setVariable("files", gitUtils.getRepositoryFiles(pluginRepoSettings.getFolder()));
    }

    public void resourcesFiles(WebContext context) {
        GitUtils gitUtils = new GitUtils();
        resources(context);
        context.setVariable("files", gitUtils.getRepositoryFiles(resourcesRepoSettings.getFolder()));
    }

    public void resourcesSettings(WebContext context) {
        GitUtils gitUtils = new GitUtils();

        try {
            Repository repository = gitUtils.getRepository(resourcesRepoSettings.getGitFolder());
            runCmd(repository, resourcesRepoSettings, context);
            context.setVariable("gitschedulerusername", gitUtils.getGitConfigString(resourcesRepoSettings.getGitFolder(), "germ", "workspace", "gitschedulerusername"));
            addCommonContext(context, resourcesRepoSettings.getGitFolder());
        } catch (Exception e) {
            addWarningMessage(e.getMessage());
        }
    }

    public void pluginsSettings(WebContext context) {
        GitUtils gitUtils = new GitUtils();

        try {
            Repository repository = gitUtils.getRepository(pluginRepoSettings.getGitFolder());
            runCmd(repository, pluginRepoSettings, context);
            context.setVariable("gitschedulerusername", gitUtils.getGitConfigString(resourcesRepoSettings.getGitFolder(), "germ", "workspace", "gitschedulerusername"));
            addCommonContext(context, pluginRepoSettings.getGitFolder());
        } catch (Exception e) {
            addWarningMessage(e.getMessage());
        }
    }


    public void plugins(WebContext context) {
        GitUtils gitUtils = new GitUtils();

        try {
            Repository repository = gitUtils.getRepository(pluginRepoSettings.getGitFolder());
            runCmd(repository, pluginRepoSettings, context);
            addCommonContext(context, pluginRepoSettings.getGitFolder());

            StoredConfig config = repository.getConfig();
            context.setVariable("repository", repository);
            context.setVariable("method", "plugins");
            context.setVariable("directory", pluginRepoSettings.getFolder());
            context.setVariable("gitDirectory", pluginRepoSettings.getGitFolder());
            context.setVariable("originUrl", gitUtils.getRemoteOrigin(pluginRepoSettings.getGitFolder()));
            context.setVariable("headCommit", config.getString("germ", "workspace", gitUtils.replaceIllegalGitConfCharacters(repository.getBranch())));
            context.setVariable("checkoutfiles", gitUtils.getRepositoryFiles(pluginRepoSettings.getFolder(), pluginsFilenameFilter));
            /*context.setVariable("sparseCheckoutPath", pluginRepoSettings.getSparseCheckoutPath());
            context.setVariable("sparseCheckoutActivated",config.getBoolean("core", "sparsecheckout", false));*/
            //context.setVariable("hasUncommittedChanges", gitUtils.getStatus(pluginRepoSettings.getGitFolder()).hasUncommittedChanges());
        } catch (Exception e) {
            addErrorMessage(e.getMessage());
        }
    }

    public void resources(WebContext context) {
        GitUtils gitUtils = new GitUtils();

        try {
            Repository repository = gitUtils.getRepository(resourcesRepoSettings.getGitFolder());

            runCmd(repository, resourcesRepoSettings, context);
            addCommonContext(context, resourcesRepoSettings.getGitFolder());

            StoredConfig config = repository.getConfig();
            context.setVariable("repository", gitUtils.getRepository(resourcesRepoSettings.getGitFolder()));
            context.setVariable("method", "resources");
            context.setVariable("directory", resourcesRepoSettings.getFolder());
            context.setVariable("gitDirectory", resourcesRepoSettings.getGitFolder());
            context.setVariable("originUrl", gitUtils.getRemoteOrigin(resourcesRepoSettings.getGitFolder()));
            context.setVariable("headCommit", config.getString("germ", "workspace", gitUtils.replaceIllegalGitConfCharacters(repository.getBranch())));
            /*context.setVariable("sparseCheckoutPath", resourcesRepoSettings.getSparseCheckoutPath());
            context.setVariable("sparseCheckoutActivated",config.getBoolean("core", "sparsecheckout", false));*/
            //context.setVariable("hasUncommittedChanges", gitUtils.getStatus(resourcesRepoSettings.getGitFolder()).hasUncommittedChanges());
        } catch (Exception e) {
            addWarningMessage(e.getMessage());
        }
    }

    public void expertmode(WebContext context) throws Exception {
        GitUtils gitUtils = new GitUtils();
        //Repository repository = gitUtils.getRepository(gitFolderWithResources);
        context.setVariable("originUrl", gitUtils.getRemoteOrigin(resourcesRepoSettings.getGitFolder()));

        String cmd = pluginEnvironment.getCurrentRequest().getParameter("cmd");
        if ("fetch".equals(cmd)) {
            String gitusername = pluginEnvironment.getCurrentRequest().getParameter("gitusername");
            String gitpassword = pluginEnvironment.getCurrentRequest().getParameter("gitpassword");
            FetchResult fetchResult = gitUtils.fetch(resourcesRepoSettings.getGitFolder(), gitusername, gitpassword);
            if (fetchResult != null && fetchResult.getMessages().length() > 0) {
                addInfoMessage(fetchResult.getMessages());
            }
            addInfoMessage("Fetch from remote origin " + fetchResult.getURI() + " was successful.");
        } else if ("reset".equals(cmd)) {
            String sha1 = pluginEnvironment.getCurrentRequest().getParameter("sha1");
            if (Strings.isNullOrEmpty(sha1)) {
                addWarningMessage("SHA-1 is not set, cannot reset.");
            } else {
                addInfoMessage("SHA-1 is " + sha1);
            }
            gitUtils.reset(resourcesRepoSettings, sha1, pathToGit);
            addInfoMessage("Successfully reset to " + sha1);
        }
    }

    public void addRequestPathContext(String requestPath, WebContext context) {
        String methodName = resolveMethodNameFromRequestPath(requestPath);
        try {
            Method method = this.getClass().getMethod(methodName, WebContext.class);
            method.invoke(this, context);
        } catch (NoSuchMethodException e) {
            LOG.info("NoSuchMethodException. No method {} defined for requestPath {}. " + e.getMessage(), methodName, requestPath);
        } catch (IllegalAccessException e) {
            LOG.error("IllegalAccessException with reflection " + e.getMessage());
        } catch (InvocationTargetException e) {
            LOG.error("InvocationTargetException with reflection " + e.getMessage());
        }
    }

    public String resolveMethodNameFromRequestPath(String requestPath) {
        String[] words = requestPath.split("/");
        String methodName = "";
        for (String word : words) {
            methodName += StringUtils.capitalize(word.toLowerCase());
        }
        methodName = StringUtils.uncapitalize(methodName);
        LOG.info("Methodname:" + methodName);

        return methodName;
    }

    private String extractUrlFromRequest() {
        String pathTranslated = pluginEnvironment.getCurrentRequest().getPathTranslated();
        LOG.info("Germ path: {} ", pathTranslated);
        if (pathTranslated == null) {
            pathTranslated = pluginEnvironment.getCurrentRequest().getRequestURI();
        }
        if ("".equals(url) && pathTranslated != null) {
            String appServerUrl = StringUtils.substringBeforeLast(pluginEnvironment.getCurrentRequest().getRequestURL().toString(), "/germ");
            String pathWithForwardSlashes = pathTranslated.replaceAll("\\\\", "/");
            String pathBeforeSite = StringUtils.substringBefore(pathWithForwardSlashes, "/site");
            String adminJunctionName = StringUtils.substringAfterLast(pathBeforeSite, "/");
            url = StringUtils.replace(appServerUrl, "admin", adminJunctionName);
        }
        return url;
    }


    private void addInfoMessage(String message) {
        addMessage(message, ResponseMessage.MessageType.INFO);
    }

    private void addWarningMessage(String message) {
        addMessage(message, ResponseMessage.MessageType.WARNING);
    }

    private void addErrorMessage(String message) {
        addMessage(message, ResponseMessage.MessageType.ERROR);
    }

    private void addMessage(String message, ResponseMessage.MessageType messageType) {
        if (messageType.compareTo(ResponseMessage.MessageType.ERROR) == 0) {
            LOG.error(message);
        } else if (messageType.compareTo(ResponseMessage.MessageType.WARNING) == 0) {
            LOG.warn(message);
        } else if (messageType.compareTo(ResponseMessage.MessageType.INFO) == 0) {
            LOG.info(message);
        }
        ResponseMessage responseMessage = new ResponseMessage(message, messageType);
        try {
            messages.get(pluginEnvironment.getCurrentSession().getId()).add(responseMessage);
        } catch (Exception e) {
            LOG.error(this.getClass().getSimpleName(), "Error getting messages, and adding response message");
        }

    }

}
