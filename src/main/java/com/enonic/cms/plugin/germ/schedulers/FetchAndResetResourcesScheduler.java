package com.enonic.cms.plugin.germ.schedulers;

import com.enonic.cms.api.plugin.PluginConfig;
import com.enonic.cms.api.plugin.ext.TaskHandler;
import com.enonic.cms.plugin.germ.GermPluginController;
import com.enonic.cms.plugin.germ.utils.GitUtils;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Properties;

@Component
public class FetchAndResetResourcesScheduler extends TaskHandler{

    PluginConfig pluginConfig;
    String gitSchedulerUsername;
    String gitSchedulerPassword;

    @Autowired
    public void setPluginConfig(List<PluginConfig> pluginConfig) {
        //TODO: Strange hack with List<PluginConfig> here, srs is investigating
        this.pluginConfig = pluginConfig.get(0);
        folderWithResources = new File(this.pluginConfig.getString("folderWithResources"));
        gitFolderWithResources = new File(folderWithResources + "/.git");
        this.gitSchedulerUsername = this.pluginConfig.getString("gitSchedulerUsername");
        this.gitSchedulerPassword = this.pluginConfig.getString("gitSchedulerPassword");
    }

    File folderWithResources;
    File gitFolderWithResources;

    Logger LOG = LoggerFactory.getLogger(FetchAndResetResourcesScheduler.class);

    @Override
    public void execute(Properties properties) throws Exception {
        if (Strings.isNullOrEmpty(gitSchedulerUsername) || Strings.isNullOrEmpty(gitSchedulerPassword)){
            LOG.warn("git user/password not configured in germ.properties, cannot fetch from origin. Aborting..");
            return;
        }
        long startTime = System.currentTimeMillis();
        LOG.info("FetchAndResetResourcesScheduler");
        LOG.info("Git folder with resources: " + gitFolderWithResources);
        executeFetchAndReset();
        long totalTime = System.currentTimeMillis() - startTime;
        LOG.info("Fetch and reset of resources took {} seconds.", totalTime);
    }

    private final void executeFetchAndReset() throws Exception{
        GitUtils gitUtils = new GitUtils();
        gitUtils.fetch(gitFolderWithResources,gitSchedulerUsername,gitSchedulerPassword);
        String headCommit = gitUtils.getHeadCommit(gitFolderWithResources);
        LOG.info("Head commit is {}", headCommit);
        gitUtils.reset(GermPluginController.resourcesRepoSettings, headCommit, null);
    }
}
