package com.enonic.cms.plugin.germ.schedulers;

import com.enonic.cms.plugin.germ.model.RepoSettings;
import com.enonic.cms.plugin.germ.utils.GitUtils;
import com.google.common.base.Strings;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;

/**
 * Created by rfo on 19/02/15.
 */
public class CommonSchedulerTools {

    Logger LOG = LoggerFactory.getLogger(CommonSchedulerTools.class);
    long logGitExecutionsSlowerThanMilliseconds = 100;

    protected final void executeFetchAndReset(File gitFolder,
            RepoSettings repoSettings, String gitSchedulerUsername, String gitSchedulerPassword) throws Exception{

        if (Strings.isNullOrEmpty(gitSchedulerUsername) || Strings.isNullOrEmpty(gitSchedulerPassword)){
            LOG.warn("Git scheduler user/password not configured in germ.properties, cannot fetch from origin. Aborting..");
            return;
        }
        long startTime = System.currentTimeMillis();

        GitUtils gitUtils = new GitUtils();


        FetchResult fetchResult = gitUtils.fetch(gitFolder, gitSchedulerUsername, gitSchedulerPassword);

        long totalTime = System.currentTimeMillis() - startTime;
        if (totalTime> logGitExecutionsSlowerThanMilliseconds){
            LOG.info("Scheduled GIT FETCH took {} ms.", totalTime);
        }

        Collection<TrackingRefUpdate> advertisedRefs = fetchResult.getTrackingRefUpdates();
        if (advertisedRefs.isEmpty()){
            LOG.info("No updates in fetch command, aborting..");
            return;
        }
        for (TrackingRefUpdate ref : advertisedRefs){
            LOG.info("Git scheduler fetched ref old:{} --- new:{}", ref.getOldObjectId(), ref.getNewObjectId());
        }

        String headCommit = gitUtils.getRemoteCommits(gitFolder).clone()[0].getName().substring(0,16);
        LOG.info("Git scheduler reset {} to remote head commit: {}", gitFolder, headCommit);

        gitUtils.reset(repoSettings, headCommit, null);

        totalTime = System.currentTimeMillis() - startTime;
        if (totalTime> logGitExecutionsSlowerThanMilliseconds){
            LOG.info("Scheduled GIT FETCH and GIT RESET took {} ms.", System.currentTimeMillis() - startTime);
        }

    }
}
