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
public class FetchAndResetToHeadPluginsScheduler extends TaskHandler{

    PluginConfig pluginConfig;
    String gitSchedulerUsername;
    String gitSchedulerPassword;
    String salt;

    File folderWithPlugins;
    File gitFolderWithPlugins;

    @Autowired
    public void setPluginConfig(List<PluginConfig> pluginConfig) {
        //TODO: Strange hack with List<PluginConfig> here, srs is investigating
        this.pluginConfig = pluginConfig.get(0);
        folderWithPlugins = new File(this.pluginConfig.getString("folderWithPlugins"));
        gitFolderWithPlugins = new File(folderWithPlugins + "/.git");
        this.salt = this.pluginConfig.getString("salt");
    }

    Logger LOG = LoggerFactory.getLogger(FetchAndResetToHeadPluginsScheduler.class);

    @Override
    public void execute(Properties properties) throws Exception {
        LOG.info("execute FetchAndResetToHeadPluginsScheduler");
        CommonSchedulerTools commonSchedulerTools = new CommonSchedulerTools();
        commonSchedulerTools.executeFetchAndReset(gitFolderWithPlugins,
                GermPluginController.pluginRepoSettings);
    }


}
