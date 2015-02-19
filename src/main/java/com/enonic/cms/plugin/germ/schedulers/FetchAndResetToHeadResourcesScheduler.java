package com.enonic.cms.plugin.germ.schedulers;

import com.enonic.cms.api.plugin.PluginConfig;
import com.enonic.cms.api.plugin.ext.TaskHandler;
import com.enonic.cms.plugin.germ.GermPluginController;
import com.enonic.cms.plugin.germ.utils.GitUtils;
import com.google.common.base.Strings;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Properties;

@Component
public class FetchAndResetToHeadResourcesScheduler extends TaskHandler{

    PluginConfig pluginConfig;
    String gitSchedulerUsername;
    String gitSchedulerPassword;

    File folderWithResources;
    File gitFolderWithResources;

    @Autowired
    public void setPluginConfig(List<PluginConfig> pluginConfig) {
        //TODO: Strange hack with List<PluginConfig> here, srs is investigating
        this.pluginConfig = pluginConfig.get(0);
        folderWithResources = new File(this.pluginConfig.getString("folderWithResources"));
        gitFolderWithResources = new File(folderWithResources + "/.git");
        this.gitSchedulerUsername = this.pluginConfig.getString("gitSchedulerUsername");
        this.gitSchedulerPassword = this.pluginConfig.getString("gitSchedulerPassword");
    }

    Logger LOG = LoggerFactory.getLogger(FetchAndResetToHeadResourcesScheduler.class);

    @Override
    public void execute(Properties properties) throws Exception {
        LOG.info("execute FetchAndResetToHeadResourcesScheduler");
        gitSchedulerUsername = properties.getProperty("gitusername", gitSchedulerUsername);
        gitSchedulerPassword = properties.getProperty("gitpassword", gitSchedulerPassword);
        CommonSchedulerTools commonSchedulerTools = new CommonSchedulerTools();
        commonSchedulerTools.executeFetchAndReset(gitFolderWithResources,
                GermPluginController.resourcesRepoSettings, gitSchedulerUsername, gitSchedulerPassword);
    }

}
