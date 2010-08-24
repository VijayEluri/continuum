package org.apache.continuum.release.distributed.manager;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.continuum.builder.distributed.manager.DistributedBuildManager;
import org.apache.continuum.configuration.BuildAgentConfiguration;
import org.apache.continuum.configuration.BuildAgentConfigurationException;
import org.apache.continuum.dao.BuildResultDao;
import org.apache.continuum.distributed.transport.slave.SlaveBuildAgentTransportClient;
import org.apache.continuum.model.repository.LocalRepository;
import org.apache.continuum.release.distributed.DistributedReleaseUtil;
import org.apache.continuum.release.model.PreparedRelease;
import org.apache.continuum.release.model.PreparedReleaseModel;
import org.apache.continuum.release.model.io.xpp3.ContinuumPrepareReleasesModelXpp3Reader;
import org.apache.continuum.release.model.io.xpp3.ContinuumPrepareReleasesModelXpp3Writer;
import org.apache.maven.continuum.configuration.ConfigurationService;
import org.apache.maven.continuum.installation.InstallationService;
import org.apache.maven.continuum.model.project.BuildResult;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.release.ContinuumReleaseException;
import org.apache.maven.shared.release.ReleaseResult;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @plexus.component role="org.apache.continuum.release.distributed.manager.DistributedReleaseManager"
 */
public class DefaultDistributedReleaseManager
    implements DistributedReleaseManager
{
    private static final Logger log = LoggerFactory.getLogger( DefaultDistributedReleaseManager.class );

    public final String PREPARED_RELEASES_FILENAME = "prepared-releases.xml";

    /**
     * @plexus.requirement
     */
    BuildResultDao buildResultDao;

    /**
     * @plexus.requirement
     */
    InstallationService installationService;

    /**
     * @plexus.requirement
     */
    ConfigurationService configurationService;

    /**
     * @plexus.requirement
     */
    DistributedBuildManager distributedBuildManager;

    private Map<String, Map<String, Object>> releasesInProgress;

    public Map getReleasePluginParameters( int projectId, String pomFilename )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = getDefaultBuildagent( projectId );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                return client.getReleasePluginParameters( projectId, pomFilename );
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to retrieve release plugin parameters because build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to retrieve release plugin parameters", e );
            throw new ContinuumReleaseException( "Failed to retrieve release plugin parameters", e );
        }
    }

    public List<Map<String, String>> processProject( int projectId, String pomFilename, boolean autoVersionSubmodules )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        BuildResult buildResult = buildResultDao.getLatestBuildResultForProject( projectId );

        String buildAgentUrl = buildResult.getBuildUrl();

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                return client.processProject( projectId, pomFilename, autoVersionSubmodules );
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to process project for releasing because build agent " + buildAgentUrl + " is unavailable" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to process project for releasing", e );
            throw new ContinuumReleaseException( "Failed to process project for releasing", e );
        }
    }

    public String releasePrepare( Project project, Properties releaseProperties, Map<String, String> releaseVersion,
                                  Map<String, String> developmentVersion, Map<String, String> environments, String username )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = environments.get( DistributedReleaseUtil.KEY_BUILD_AGENT_URL );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
    
                String releaseId =
                    client.releasePrepare( createProjectMap( project ), releaseProperties,
                                           releaseVersion, developmentVersion, environments, username );
    
                addReleasePrepare( releaseId, buildAgentUrl, releaseVersion.get( releaseId ), "prepare" );
    
                addReleaseInProgress( releaseId, "prepare", project.getId(), username );
    
                return releaseId;
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to prepare release project because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to prepare release project " + project.getName(), e );
            throw new ContinuumReleaseException( "Failed to prepare release project " + project.getName(), e );
        }
    }

    public ReleaseResult getReleaseResult( String releaseId )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = getBuildAgentUrl( releaseId );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                Map<String, Object> result = client.getReleaseResult( releaseId );
    
                ReleaseResult releaseResult = new ReleaseResult();
                releaseResult.setStartTime( DistributedReleaseUtil.getStartTime( result ) );
                releaseResult.setEndTime( DistributedReleaseUtil.getEndTime( result ) );
                releaseResult.setResultCode( DistributedReleaseUtil.getReleaseResultCode( result ) );
                releaseResult.getOutputBuffer().append( DistributedReleaseUtil.getReleaseOutput( result ) );
    
                return releaseResult;
            }

            // call reload in case we disable a build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to get release result of " + releaseId + 
                                                 " because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to get release result of " + releaseId, e );
            throw new ContinuumReleaseException( "Failed to get release result of " + releaseId, e );
        }
    }

    public Map getListener( String releaseId )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = getBuildAgentUrl( releaseId );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                return client.getListener( releaseId );
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to get listener for " + releaseId + 
                                                 " because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to get listener for " + releaseId, e );
            throw new ContinuumReleaseException( "Failed to get listener for " + releaseId, e );
        }
    }

    public void removeListener( String releaseId )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = getBuildAgentUrl( releaseId );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                client.removeListener( releaseId );
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to remove listener of " + releaseId + 
                                                 " because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to remove listener of " + releaseId, e );
            throw new ContinuumReleaseException( "Failed to remove listener of " + releaseId, e );
        }
    }

    public String getPreparedReleaseName( String releaseId )
        throws ContinuumReleaseException
    {
        String buildAgentUrl = getBuildAgentUrl( releaseId );

        if ( StringUtils.isBlank( buildAgentUrl ) )
        {
            log.info( "Unable to get prepared release name because no build agent found for " + releaseId );
            return null;
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                return client.getPreparedReleaseName( releaseId );
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to get prepared release name of " + releaseId + 
                                                 " because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to get prepared release name of " + releaseId, e );
            throw new ContinuumReleaseException( "Failed to get prepared release name of " + releaseId, e );
        }
    }

    public void releasePerform( int projectId, String releaseId, String goals, String arguments,
                                boolean useReleaseProfile, LocalRepository repository, String username )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        List<PreparedRelease> releases = getPreparedReleases();

        for ( PreparedRelease release: releases )
        {
            if ( release.getReleaseId().equals( releaseId ) )
            {
                release.setReleaseType( "perform" );
                savePreparedReleases( releases );
                break;
            }
        }

        String buildAgentUrl = getBuildAgentUrl( releaseId );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        if ( goals == null )
        {
            goals = "";
        }

        if ( arguments == null )
        {
            arguments = "";
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put( DistributedReleaseUtil.KEY_USERNAME, username );

        if ( repository != null )
        {   
            map.put( DistributedReleaseUtil.KEY_LOCAL_REPOSITORY_NAME, repository.getName() );         
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                client.releasePerform( releaseId, goals, arguments, useReleaseProfile, map, username );
    
                addReleaseInProgress( releaseId, "perform", projectId, username );
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to perform release of " + releaseId + 
                                                 " because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to perform release of " + releaseId, e );
            throw new ContinuumReleaseException( "Failed to perform release of " + releaseId, e );
        }
    }

    public String releasePerformFromScm( int projectId, String goals, String arguments, boolean useReleaseProfile,
                                         LocalRepository repository, String scmUrl, String scmUsername,
                                         String scmPassword, String scmTag, String scmTagBase, Map environments, String username )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = (String) environments.get( DistributedReleaseUtil.KEY_BUILD_AGENT_URL );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        if ( goals == null )
        {
            goals = "";
        }

        if ( arguments == null )
        {
            arguments = "";
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put( DistributedReleaseUtil.KEY_USERNAME, username );

        if ( repository != null )
        {            
            map.put( DistributedReleaseUtil.KEY_LOCAL_REPOSITORY_NAME, repository.getName() );         
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                String releaseId =
                    client.releasePerformFromScm( goals, arguments, useReleaseProfile, map, scmUrl, scmUsername,
                                                  scmPassword, scmTag, scmTagBase, environments, username );
    
                addReleasePrepare( releaseId, buildAgentUrl, scmTag, "perform" );
                addReleaseInProgress( releaseId, "perform", projectId, username );
    
                return releaseId;
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to perform release because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to perform release", e );
            throw new ContinuumReleaseException( "Failed to perform release", e );
        }
    }

    public void releaseRollback( String releaseId, int projectId )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = getBuildAgentUrl( releaseId );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                client.releaseRollback( releaseId, projectId );
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Unable to rollback release " + releaseId + 
                                                 " because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Unable to rollback release " + releaseId, e );
            throw new ContinuumReleaseException( "Unable to rollback release " + releaseId, e );
        }
    }

    public String releaseCleanup( String releaseId )
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        String buildAgentUrl = getBuildAgentUrl( releaseId );

        if ( !checkBuildAgent( buildAgentUrl ) )
        {
            throw new BuildAgentConfigurationException( buildAgentUrl );
        }

        try
        {
            if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportClient client = new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                String result = client.releaseCleanup( releaseId );
    
                removeFromReleaseInProgress( releaseId );
                removeFromPreparedReleases( releaseId );
    
                return result;
            }

            // call reload in case we disable the build agent
            distributedBuildManager.reload();

            throw new ContinuumReleaseException( "Failed to cleanup release of " + releaseId + 
                                                 " because the build agent " + buildAgentUrl + " is not available" );
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid build agent url " + buildAgentUrl );
            throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Failed to cleanup release of " + releaseId, e );
            throw new ContinuumReleaseException( "Failed to cleanup release of " + releaseId, e );
        }
    }

    public List<Map<String, Object>> getAllReleasesInProgress()
        throws ContinuumReleaseException, BuildAgentConfigurationException
    {
        List<Map<String, Object>> releases = new ArrayList<Map<String, Object>>();
        Map<String, Map<String, Object>> releasesMap = new HashMap<String, Map<String, Object>>();

        if ( releasesInProgress != null && !releasesInProgress.isEmpty() )
        {
            for ( String releaseId : releasesInProgress.keySet() )
            {
                String buildAgentUrl = getBuildAgentUrl( releaseId );

                if ( StringUtils.isNotBlank( buildAgentUrl ) )
                {
                    if ( !checkBuildAgent( buildAgentUrl ) )
                    {
                        throw new BuildAgentConfigurationException( buildAgentUrl );
                    }

                    try
                    {
                        if ( distributedBuildManager.isAgentAvailable( buildAgentUrl ) )
                        {
                            SlaveBuildAgentTransportClient client =
                                new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
                            Map map = client.getListener( releaseId );
    
                            if ( map != null && !map.isEmpty() )
                            {
                                Map<String, Object> release = releasesInProgress.get( releaseId );
                                release.put( DistributedReleaseUtil.KEY_RELEASE_ID, releaseId );
                                release.put( DistributedReleaseUtil.KEY_BUILD_AGENT_URL, buildAgentUrl );
    
                                releases.add( release );
    
                                releasesMap.put( releaseId, releasesInProgress.get( releaseId ) );
                            }
                        }
                    }
                    catch ( MalformedURLException e )
                    {
                        log.error( "Invalid build agent url " + buildAgentUrl );
                        throw new ContinuumReleaseException( "Invalid build agent url " + buildAgentUrl );
                    }
                    catch ( Exception e )
                    {
                        log.error( "Failed to get all releases in progress ", e );
                        throw new ContinuumReleaseException( "Failed to get all releases in progress ", e );
                    }
                }
            }

            releasesInProgress = releasesMap;
        }

        try
        {
            // call reload in case we disable a build agent
            distributedBuildManager.reload();
        }
        catch ( Exception e )
        {
            throw new ContinuumReleaseException( e.getMessage(), e );
        }

        return releases;
    }
    
    public String getDefaultBuildagent( int projectId )
    {
        BuildResult buildResult = buildResultDao.getLatestBuildResultForProject( projectId );
        
        return buildResult != null ? buildResult.getBuildUrl() : null;
    }

    private Map createProjectMap( Project project )
    {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put( DistributedReleaseUtil.KEY_PROJECT_ID, project.getId() );
        map.put( DistributedReleaseUtil.KEY_GROUP_ID, project.getGroupId() );
        map.put( DistributedReleaseUtil.KEY_ARTIFACT_ID, project.getArtifactId() );
        map.put( DistributedReleaseUtil.KEY_SCM_URL, project.getScmUrl() );
        if ( project.getProjectGroup().getLocalRepository() != null )
        {
            map.put( DistributedReleaseUtil.KEY_LOCAL_REPOSITORY_NAME,
                     project.getProjectGroup().getLocalRepository().getName() );
        }

        return map;
    }

    private List<PreparedRelease> getPreparedReleases()
        throws ContinuumReleaseException
    {
        File file = getPreparedReleasesFile();

        if ( file.exists() )
        {
            FileInputStream fis = null;
            try
            {
                fis = new FileInputStream( file );
                ContinuumPrepareReleasesModelXpp3Reader reader = new ContinuumPrepareReleasesModelXpp3Reader();
                PreparedReleaseModel model = reader.read( new InputStreamReader( fis ) );

                return model.getPreparedReleases();
            }
            catch ( IOException e )
            {
                log.error( e.getMessage(), e );
                throw new ContinuumReleaseException( "Unable to get prepared releases", e );
            }
            catch ( XmlPullParserException e )
            {
                log.error( e.getMessage(), e );
                throw new ContinuumReleaseException( e.getMessage(), e );
            }
            finally
            {
                if ( fis != null )
                {
                    IOUtil.close( fis );
                }
            }
        }

        return null;
    }

    private void addReleasePrepare( String releaseId, String buildAgentUrl, String releaseName, String releaseType )
        throws ContinuumReleaseException
    {
        PreparedRelease release = new PreparedRelease();
        release.setReleaseId( releaseId );
        release.setBuildAgentUrl( buildAgentUrl );
        release.setReleaseName( releaseName );
        release.setReleaseType( releaseType );

        List<PreparedRelease> preparedReleases = getPreparedReleases();

        if ( preparedReleases == null )
        {
            preparedReleases = new ArrayList<PreparedRelease>();
        }

        boolean found = false;

        for ( PreparedRelease preparedRelease : preparedReleases )
        {
            if ( preparedRelease.getReleaseId().equals( release.getReleaseId() ) &&
                 preparedRelease.getReleaseName().equals( release.getReleaseName() ) )
            {
                preparedRelease.setBuildAgentUrl( release.getBuildAgentUrl() );
                found = true;
            }
        }

        if ( !found )
        {
            preparedReleases.add( release );
        }

        savePreparedReleases( preparedReleases );
    }

    private void addReleaseInProgress( String releaseId, String releaseType, int projectId, String username )
    {
        if ( releasesInProgress == null )
        {
            releasesInProgress = new HashMap<String, Map<String, Object>>();
        }

        Map<String, Object> map = new HashMap<String, Object>();
        map.put( DistributedReleaseUtil.KEY_RELEASE_GOAL, releaseType );
        map.put( DistributedReleaseUtil.KEY_PROJECT_ID, projectId );
        map.put( DistributedReleaseUtil.KEY_USERNAME, username );

        releasesInProgress.put( releaseId, map );
    }

    private void removeFromReleaseInProgress( String releaseId )
    {
        if ( releasesInProgress != null && releasesInProgress.containsKey( releaseId ) )
        {
            releasesInProgress.remove( releaseId );
        }
    }

    private String getBuildAgentUrl( String releaseId )
        throws ContinuumReleaseException
    {
        List<PreparedRelease> preparedReleases = getPreparedReleases();

        if ( preparedReleases != null )
        {
            for ( PreparedRelease preparedRelease : preparedReleases )
            {
                if ( preparedRelease.getReleaseId().equals( releaseId ) )
                {
                    return preparedRelease.getBuildAgentUrl();
                }
            }
        }

        return null;
    }

    private File getPreparedReleasesFile()
    {
        return new File( System.getProperty( "appserver.base" ) + File.separator + "conf" + File.separator +
            PREPARED_RELEASES_FILENAME );
    }

    private boolean checkBuildAgent( String buildAgentUrl )
    {
        BuildAgentConfiguration buildAgent = configurationService.getBuildAgent( buildAgentUrl );

        if ( buildAgent != null && buildAgent.isEnabled() )
        {
            return true;
        }

        log.info( "Build agent: " + buildAgentUrl + " is either disabled or removed" );
        return false;
    }

    private void removeFromPreparedReleases( String releaseId )
        throws ContinuumReleaseException
    {
        List<PreparedRelease> releases = getPreparedReleases();

        for ( PreparedRelease release : releases )
        {
            if ( release.getReleaseId().equals( releaseId ) )
            {
                if ( release.getReleaseType().equals( "perform" ) )
                {
                    releases.remove( release );
                    savePreparedReleases( releases );
                    break;
                }
            }
        }
    }

    private void savePreparedReleases( List<PreparedRelease> preparedReleases)
        throws ContinuumReleaseException
    {
        File file = getPreparedReleasesFile();

        if ( !file.exists() )
        {
            file.getParentFile().mkdirs();
        }

        PreparedReleaseModel model = new PreparedReleaseModel();
        model.setPreparedReleases( preparedReleases );

        try
        {
            ContinuumPrepareReleasesModelXpp3Writer writer = new ContinuumPrepareReleasesModelXpp3Writer();
            FileWriter fileWriter = new FileWriter( file );
            writer.write( fileWriter, model );
            fileWriter.flush();
            fileWriter.close();
        }
        catch ( IOException e )
        {
            throw new ContinuumReleaseException( "Failed to write prepared releases in file", e );
        }
    }
    
    // for unit test
    
    public void setBuildResultDao( BuildResultDao buildResultDao )
    {
        this.buildResultDao = buildResultDao;
    }
}