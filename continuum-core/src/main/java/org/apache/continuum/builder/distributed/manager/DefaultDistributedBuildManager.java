package org.apache.continuum.builder.distributed.manager;

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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.continuum.buildagent.NoBuildAgentException;
import org.apache.continuum.buildagent.NoBuildAgentInGroupException;
import org.apache.continuum.builder.distributed.executor.ThreadedDistributedBuildTaskQueueExecutor;
import org.apache.continuum.builder.distributed.util.DistributedBuildUtil;
import org.apache.continuum.builder.utils.ContinuumBuildConstant;
import org.apache.continuum.configuration.BuildAgentConfiguration;
import org.apache.continuum.configuration.BuildAgentGroupConfiguration;
import org.apache.continuum.dao.BuildDefinitionDao;
import org.apache.continuum.dao.BuildResultDao;
import org.apache.continuum.dao.ProjectDao;
import org.apache.continuum.distributed.transport.slave.SlaveBuildAgentTransportClient;
import org.apache.continuum.distributed.transport.slave.SlaveBuildAgentTransportService;
import org.apache.continuum.model.project.ProjectScmRoot;
import org.apache.continuum.taskqueue.BuildProjectTask;
import org.apache.continuum.taskqueue.OverallDistributedBuildQueue;
import org.apache.continuum.taskqueue.PrepareBuildProjectsTask;
import org.apache.continuum.utils.ContinuumUtils;
import org.apache.continuum.utils.ProjectSorter;
import org.apache.continuum.utils.build.BuildTrigger;
import org.apache.maven.continuum.ContinuumException;
import org.apache.maven.continuum.configuration.ConfigurationService;
import org.apache.maven.continuum.model.project.BuildDefinition;
import org.apache.maven.continuum.model.project.BuildResult;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.model.system.Installation;
import org.apache.maven.continuum.model.system.Profile;
import org.apache.maven.continuum.store.ContinuumStoreException;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.StoppingException;
import org.codehaus.plexus.taskqueue.Task;
import org.codehaus.plexus.taskqueue.TaskQueueException;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Maria Catherine Tan
 * @plexus.component role="org.apache.continuum.builder.distributed.manager.DistributedBuildManager"
 */
public class DefaultDistributedBuildManager
    implements DistributedBuildManager, Contextualizable, Initializable
{
    private static final Logger log = LoggerFactory.getLogger( DefaultDistributedBuildManager.class );

    private Map<String, OverallDistributedBuildQueue> overallDistributedBuildQueues =
        Collections.synchronizedMap( new HashMap<String, OverallDistributedBuildQueue>() );

    /**
     * @plexus.requirement
     */
    private ConfigurationService configurationService;

    /**
     * @plexus.requirement
     */
    private ProjectDao projectDao;

    /**
     * @plexus.requirement
     */
    private BuildDefinitionDao buildDefinitionDao;

    /**
     * @plexus.requirement
     */
    private BuildResultDao buildResultDao;

    /**
     * @plexus.requirement
     */
    private DistributedBuildUtil distributedBuildUtil;

    private PlexusContainer container;

    // --------------------------------
    //  Plexus Lifecycle
    // --------------------------------
    public void contextualize( Context context )
        throws ContextException
    {
        container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

    public void initialize()
        throws InitializationException
    {
        List<BuildAgentConfiguration> agents = configurationService.getBuildAgents();

        if ( !agents.isEmpty() )
        {
            synchronized( overallDistributedBuildQueues )
            {
                for ( BuildAgentConfiguration agent : agents )
                {
                    if ( agent.isEnabled() )
                    {
                        try
                        {
                            SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( agent.getUrl() );

                            if ( client.ping() )
                            {
                                log.info(
                                    "agent is enabled, create distributed build queue for build agent '" + agent.getUrl() + "'" );
                                createDistributedBuildQueueForAgent( agent.getUrl() );
                            }
                            else
                            {
                                log.info( "unable to ping build agent '" + agent.getUrl() + "'" );
                            }
                        }
                        catch ( MalformedURLException e )
                        {
                            // do not throw exception, just log it
                            log.info( "Invalid build agent URL " + agent.getUrl() + ", not creating distributed build queue" );
                        }
                        catch ( ContinuumException e )
                        {
                            throw new InitializationException(
                                "Error while initializing distributed build queues", e );
                        }
                        catch ( Exception e )
                        {
                            agent.setEnabled( false );
                            log.info( "unable to ping build agent '" + agent.getUrl() + "': " +
                                ContinuumUtils.throwableToString( e ) );
                        }
                    }
                }
            }
        }
    }

    public void reload()
        throws ContinuumException
    {
        List<BuildAgentConfiguration> agents = configurationService.getBuildAgents();

        if ( agents.isEmpty() )
        {
            return;
        }

        synchronized( overallDistributedBuildQueues )
        {
            for ( BuildAgentConfiguration agent : agents )
            {
                if ( agent.isEnabled() && !overallDistributedBuildQueues.containsKey( agent.getUrl() ) )
                {
                    try
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( agent.getUrl() );
    
                        if ( client.ping() )
                        {
                            log.info( "agent is enabled, create distributed build queue for build agent '" + agent.getUrl() + "'" );
                            createDistributedBuildQueueForAgent( agent.getUrl() );
                        }
                        else
                        {
                            log.info( "unable to ping build agent '" + agent.getUrl() + "'" );
                        }
                    }
                    catch ( MalformedURLException e )
                    {
                        // do not throw exception, just log it
                        log.info( "Invalid build agent URL " + agent.getUrl() + ", not creating distributed build queue" );
                    }
                    catch ( Exception e )
                    {
                        agent.setEnabled( false );
                        log.info( "unable to ping build agent '" + agent.getUrl() + "': " +
                            ContinuumUtils.throwableToString( e ) );
                    }
                }
                else if ( !agent.isEnabled() && overallDistributedBuildQueues.containsKey( agent.getUrl() ) )
                {
                    log.info( "agent is disabled, remove distributed build queue for build agent '" + agent.getUrl() + "'" );
                    removeDistributedBuildQueueOfAgent( agent.getUrl() );
                }
            }
        }
    }

    public void prepareBuildProjects( Map<Integer, Integer>projectsBuildDefinitionsMap, BuildTrigger buildTrigger, int projectGroupId, 
                                      String projectGroupName, String scmRootAddress, int scmRootId, List<ProjectScmRoot> scmRoots )
        throws ContinuumException, NoBuildAgentException, NoBuildAgentInGroupException
    {
    	PrepareBuildProjectsTask task = new PrepareBuildProjectsTask( projectsBuildDefinitionsMap, buildTrigger,
                                                                      projectGroupId, projectGroupName, 
                                                                      scmRootAddress, scmRootId );

        OverallDistributedBuildQueue overallDistributedBuildQueue = getOverallDistributedBuildQueueByGroup( projectGroupId, scmRoots, scmRootId );

        if ( overallDistributedBuildQueue == null )
        {
            log.info( "no overall build queue by group" );

            if ( hasBuildagentGroup( projectsBuildDefinitionsMap ) )
            {
                if ( !hasBuildagentInGroup( projectsBuildDefinitionsMap ) )
                {
                    log.warn( "No build agent configured in build agent group. Not building projects." );
    
                    throw new NoBuildAgentInGroupException( "No build agent configured in build agent group" );
                }
                else
                {
                    // get overall distributed build queue from build agent group
                    overallDistributedBuildQueue = getOverallDistributedBuildQueueByAgentGroup( projectsBuildDefinitionsMap );
                    log.info( "got overall build queue by agent group" );
                }
            }
            else
            {
                // project does not have build agent group
                overallDistributedBuildQueue = getOverallDistributedBuildQueue();
                log.info( "get overall build queue in all agents" );
            }
        }

        if ( overallDistributedBuildQueue != null )
        {
            try
            {
                overallDistributedBuildQueue.addToDistributedBuildQueue( task );
            }
            catch ( TaskQueueException e )
            {
                log.error( "Error while enqueuing prepare build task", e );
                throw new ContinuumException( "Error occurred while enqueuing prepare build task", e );
            }
        }
        else
        {
            log.warn( "No build agent configured. Not building projects." );

            throw new NoBuildAgentException( "No build agent configured" );
        }
    }

    public void removeDistributedBuildQueueOfAgent( String buildAgentUrl )
        throws ContinuumException
    {
        if ( overallDistributedBuildQueues.containsKey( buildAgentUrl ) )
        {
            List<PrepareBuildProjectsTask> tasks = null;

            synchronized( overallDistributedBuildQueues )
            {
                OverallDistributedBuildQueue overallDistributedBuildQueue = overallDistributedBuildQueues.get( buildAgentUrl );

                try
                {
                    if ( overallDistributedBuildQueue.getDistributedBuildTaskQueueExecutor().getCurrentTask() != null )
                    {
                        log.error( "Unable to remove build agent because it is currently being used" );
                        throw new ContinuumException( "Unable to remove build agent because it is currently being used" );
                    }

                    tasks = overallDistributedBuildQueue.getProjectsInQueue();

                    overallDistributedBuildQueue.getDistributedBuildQueue().removeAll( tasks );

                    ( (ThreadedDistributedBuildTaskQueueExecutor) overallDistributedBuildQueue.getDistributedBuildTaskQueueExecutor() ).stop();

                    container.release( overallDistributedBuildQueue );

                    overallDistributedBuildQueues.remove( buildAgentUrl );

                    log.info( "remove distributed build queue for build agent '" + buildAgentUrl + "'" );
                }
                catch ( TaskQueueException e )
                {
                    log.error( "Error occurred while removing build agent " + buildAgentUrl, e );
                    throw new ContinuumException( "Error occurred while removing build agent " + buildAgentUrl, e );
                }
                catch ( ComponentLifecycleException e )
                {
                    log.error( "Error occurred while removing build agent " + buildAgentUrl, e );
                    throw new ContinuumException( "Error occurred while removing build agent " + buildAgentUrl, e );
                }
                catch ( StoppingException e )
                {
                    log.error( "Error occurred while removing build agent " + buildAgentUrl, e );
                    throw new ContinuumException( "Error occurred while removing build agent " + buildAgentUrl, e );
                }
            }
        }
    }

    public Map<String, List<PrepareBuildProjectsTask>> getProjectsInPrepareBuildQueue()
        throws ContinuumException
    {
        Map<String, List<PrepareBuildProjectsTask>> map = new HashMap<String, List<PrepareBuildProjectsTask>>();

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                List<PrepareBuildProjectsTask> tasks = new ArrayList<PrepareBuildProjectsTask>();

                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );

                        List<Map<String, Object>> projects = client.getProjectsInPrepareBuildQueue();
    
                        for ( Map<String, Object> context : projects )
                        {
                            tasks.add( getPrepareBuildProjectsTask( context ) );
                        }
    
                        map.put( buildAgentUrl, tasks );
                    }
                }
                catch ( MalformedURLException e )
                {
                    throw new ContinuumException( "Invalid build agent url: " + buildAgentUrl ); 
                }
                catch ( Exception e )
                {
                    throw new ContinuumException( "Error while retrieving projects in prepare build queue", e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        return map;
    }

    public Map<String, PrepareBuildProjectsTask> getProjectsCurrentlyPreparingBuild()
        throws ContinuumException
    {
        Map<String, PrepareBuildProjectsTask> map = new HashMap<String, PrepareBuildProjectsTask>();

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                        Map<String, Object> project = client.getProjectCurrentlyPreparingBuild();
    
                        if ( !project.isEmpty() )
                        {
                            map.put( buildAgentUrl, getPrepareBuildProjectsTask( project ) );
                        }
                    }
                }
                catch ( MalformedURLException e )
                {
                    throw new ContinuumException( "Invalid build agent url: " + buildAgentUrl );
                }
                catch ( Exception e )
                {
                    throw new ContinuumException( "Error retrieving projects currently preparing build in " + buildAgentUrl, e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        return map;
    }
 
    public Map<String, BuildProjectTask> getProjectsCurrentlyBuilding()
        throws ContinuumException
    {
        Map<String, BuildProjectTask> map = new HashMap<String, BuildProjectTask>();

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                        Map<String, Object> project = client.getProjectCurrentlyBuilding();
    
                        if ( !project.isEmpty() )
                        {
                            map.put( buildAgentUrl, getBuildProjectTask( project ) );
                        }
                    }
                }
                catch ( MalformedURLException e )
                {
                    throw new ContinuumException( "Invalid build agent url: " + buildAgentUrl );
                }
                catch ( Exception e )
                {
                    throw new ContinuumException( "Error retrieving projects currently building in " + buildAgentUrl, e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        return map;
    }

    public Map<String, List<BuildProjectTask>> getProjectsInBuildQueue()
        throws ContinuumException
    {
        Map<String, List<BuildProjectTask>> map = new HashMap<String, List<BuildProjectTask>>();

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                List<BuildProjectTask> tasks = new ArrayList<BuildProjectTask>();

                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                        List<Map<String, Object>> projects = client.getProjectsInBuildQueue();
    
                        for ( Map<String, Object> context : projects )
                        {
                            tasks.add( getBuildProjectTask( context ) );
                        }
    
                        map.put( buildAgentUrl, tasks );
                    }
                }
                catch ( MalformedURLException e )
                {
                    throw new ContinuumException( "Invalid build agent url: " + buildAgentUrl ); 
                }
                catch ( Exception e )
                {
                    throw new ContinuumException( "Error while retrieving projects in build queue", e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        return map;
    }

    public boolean isBuildAgentBusy( String buildAgentUrl )
    {
        synchronized ( overallDistributedBuildQueues )
        {
            OverallDistributedBuildQueue overallDistributedBuildQueue = overallDistributedBuildQueues.get( buildAgentUrl );

            if ( overallDistributedBuildQueue != null && 
                 overallDistributedBuildQueue.getDistributedBuildTaskQueueExecutor().getCurrentTask() != null )
            {
                log.info( "build agent '" + buildAgentUrl + "' is busy" );
                return true;
            }

            log.info( "build agent '" + buildAgentUrl + "' is not busy" );
            return false;
        }
    }

    public void cancelDistributedBuild( String buildAgentUrl )
        throws ContinuumException
    {
        try
        {
            if ( isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
    
                client.cancelBuild();
            }

            // call reload in case we disable the build agent
            reload();
        }
        catch ( MalformedURLException e )
        {
            log.error( "Error cancelling build in build agent: Invalid build agent url " + buildAgentUrl );
            throw new ContinuumException( "Error cancelling build in build agent: Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Error occurred while cancelling build in build agent " + buildAgentUrl, e );
            throw new ContinuumException( "Error occurred while cancelling build in build agent " + buildAgentUrl, e );
        }
    }

    public Map<String, Object> getBuildResult( int projectId )
        throws ContinuumException
    {
        Map<String, Object> map = new HashMap<String, Object>();

        String buildAgentUrl = getBuildAgent( projectId );

        if ( buildAgentUrl == null )
        {
            return null;
        }

        try
        {
            if ( isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );

                Map<String, Object> result = client.getBuildResult( projectId );

                if ( result != null )
                {
                    int buildDefinitionId = ContinuumBuildConstant.getBuildDefinitionId( result );

                    Project project = projectDao.getProjectWithAllDetails( projectId );
                    BuildDefinition buildDefinition = buildDefinitionDao.getBuildDefinition( buildDefinitionId );

                    BuildResult oldBuildResult =
                        buildResultDao.getLatestBuildResultForBuildDefinition( projectId, buildDefinitionId );

                    BuildResult buildResult = distributedBuildUtil.convertMapToBuildResult( result );
                    buildResult.setBuildDefinition( buildDefinition );
                    buildResult.setBuildNumber( project.getBuildNumber() + 1 );
                    buildResult.setModifiedDependencies( distributedBuildUtil.getModifiedDependencies( oldBuildResult, result ) );
                    buildResult.setScmResult( distributedBuildUtil.getScmResult( result ) );

                    String buildOutput = ContinuumBuildConstant.getBuildOutput( result );

                    map.put( ContinuumBuildConstant.KEY_BUILD_RESULT, buildResult );
                    map.put( ContinuumBuildConstant.KEY_BUILD_OUTPUT, buildOutput );
                }
            }
        }
        catch ( MalformedURLException e )
        {
            throw new ContinuumException( "Invalid build agent URL '" + buildAgentUrl + "'" );
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error while retrieving build result for project" + projectId, e );
        }

        // call reload in case we disable the build agent
        reload();

        return map;
    }

    public List<Installation> getAvailableInstallations( String buildAgentUrl )
        throws ContinuumException
    {
        List<Installation> installations = new ArrayList<Installation>();

        try
        {
            if ( isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );

                List<Map<String, String>> installationsList = client.getAvailableInstallations();

                for ( Map context : installationsList )
                {
                    Installation installation = new Installation();
                    installation.setName( ContinuumBuildConstant.getInstallationName( context ) );
                    installation.setType( ContinuumBuildConstant.getInstallationType( context ) );
                    installation.setVarName( ContinuumBuildConstant.getInstallationVarName( context ) );
                    installation.setVarValue( ContinuumBuildConstant.getInstallationVarValue( context ) );
                    installations.add( installation );
                }
            }

            // call reload in case we disable the build agent
            reload();

            return installations;
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Unable to get available installations of build agent", e );
        }
    }

    public String generateWorkingCopyContent( int projectId, String directory, String baseUrl, String imageBaseUrl )
        throws ContinuumException
    {
        BuildResult buildResult = buildResultDao.getLatestBuildResultForProject( projectId );

        if ( buildResult != null )
        {
            String buildAgentUrl = buildResult.getBuildUrl();

            if ( buildAgentUrl == null )
            {
                return "";
            }

            try
            {
                if ( directory == null )
                {
                    directory = "";
                }

                if ( isAgentAvailable( buildAgentUrl ) )
                {
                    SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                    return client.generateWorkingCopyContent( projectId, directory, baseUrl, imageBaseUrl );
                }
            }
            catch ( MalformedURLException e )
            {
                log.error( "Invalid build agent url " + buildAgentUrl );
            }
            catch ( Exception e )
            {
                log.error( "Error while generating working copy content from build agent " + buildAgentUrl, e );
            }
        }

        // call reload in case we disable the build agent
        reload();

        return "";
    }
    
    public String getFileContent( int projectId, String directory, String filename )
        throws ContinuumException
    {
        BuildResult buildResult = buildResultDao.getLatestBuildResultForProject( projectId );

        if ( buildResult != null )
        {
            String buildAgentUrl = buildResult.getBuildUrl();

            if ( buildAgentUrl == null )
            {
                return "";
            }

            try
            {
                if ( isAgentAvailable( buildAgentUrl ) )
                {
                    SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                    return client.getProjectFileContent( projectId, directory, filename );
                }
            }
            catch ( MalformedURLException e )
            {
                log.error( "Invalid build agent url " + buildAgentUrl );
            }
            catch ( Exception e )
            {
                log.error( "Error while retrieving content of " + filename, e );
            }
        }

        // call reload in case we disable the build agent
        reload();

        return "";
    }

    public void removeFromPrepareBuildQueue( String buildAgentUrl, int projectGroupId, int scmRootId )
        throws ContinuumException
    {
        try
        {
            if ( isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                client.removeFromPrepareBuildQueue( projectGroupId, scmRootId );
            }
        }
        catch ( MalformedURLException e )
        {
            log.error( "Unable to remove projectGroupId=" + projectGroupId + " scmRootId=" + scmRootId + 
                       " from prepare build queue: Invalid build agent url " + buildAgentUrl );
            throw new ContinuumException( "Unable to remove projectGroupId=" + projectGroupId + " scmRootId=" + scmRootId + 
                                          " from prepare build queue: Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Error occurred while removing projectGroupId=" + projectGroupId + " scmRootId=" + scmRootId + 
                       " from prepare build queue of agent " + buildAgentUrl, e );
            throw new ContinuumException( "Error occurred while removing projectGroupId=" + projectGroupId + " scmRootId=" +
                                          scmRootId + " from prepare build queue of agent " + buildAgentUrl, e );
        }

        // call reload in case we disable the build agent
        reload();
    }

    public void removeFromBuildQueue( String buildAgentUrl, int projectId, int buildDefinitionId )
        throws ContinuumException
    {
        try
        {
            if ( isAgentAvailable( buildAgentUrl ) )
            {
                SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                client.removeFromBuildQueue( projectId, buildDefinitionId );
            }
        }
        catch ( MalformedURLException e )
        {
            log.error( "Unable to remove project " + projectId + 
                       " from build queue: Invalid build agent url " + buildAgentUrl );
            throw new ContinuumException( "Unable to remove project " + projectId + 
                                          " from build queue: Invalid build agent url " + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.error( "Error occurred while removing project " + projectId +
                       " from build queue of agent " + buildAgentUrl, e );
            throw new ContinuumException( "Error occurred while removing project " + projectId + 
                                          " from build queue of agent " + buildAgentUrl, e );
        }

        // call reload in case we disable the build agent
        reload();
    }

    public void removeFromPrepareBuildQueue( List<String> hashCodes )
        throws ContinuumException
    {
        synchronized ( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                        client.removeFromPrepareBuildQueue( hashCodes );
                    }
                }
                catch ( MalformedURLException e )
                {
                    log.error( "Error trying to remove projects from prepare build queue. Invalid build agent url: " + buildAgentUrl );
                }
                catch ( Exception e )
                {
                    log.error( "Error trying to remove projects from prepare build queue of agent " + buildAgentUrl, e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();
    }

    public void removeFromBuildQueue( List<String> hashCodes )
        throws ContinuumException
    {
        synchronized ( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                        client.removeFromBuildQueue( hashCodes );
                    }
                }
                catch ( MalformedURLException e )
                {
                    log.error( "Error trying to remove projects from build queue. Invalid build agent url: " + buildAgentUrl );
                }
                catch ( Exception e )
                {
                    log.error( "Error trying to remove projects from build queue of agent " + buildAgentUrl, e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();
    }

    public boolean isProjectInAnyPrepareBuildQueue( int projectId, int buildDefinitionId )
        throws ContinuumException
    {
        boolean found = false;

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );

                        List<Map<String, Object>> projects = client.getProjectsAndBuildDefinitionsInPrepareBuildQueue();
    
                        for ( Map<String, Object> context : projects )
                        {
                            int pid = ContinuumBuildConstant.getProjectId( context );
                            int buildId = ContinuumBuildConstant.getBuildDefinitionId( context );

                            if ( pid == projectId && buildId == buildDefinitionId )
                            {
                                found = true;
                                break;
                            }

                        }
                    }

                    if ( found )
                    {
                        break;
                    }
                }
                catch ( MalformedURLException e )
                {
                    throw new ContinuumException( "Invalid build agent url: " + buildAgentUrl ); 
                }
                catch ( Exception e )
                {
                    throw new ContinuumException( "Error while retrieving projects in prepare build queue", e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        if ( found )
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean isProjectInAnyBuildQueue( int projectId, int buildDefinitionId )
        throws ContinuumException
    {
        Map<String, List<BuildProjectTask>> map = getProjectsInBuildQueue();

        for ( String url : map.keySet() )
        {
            for ( BuildProjectTask task : map.get( url ) )
            {
                if ( task.getProjectId() == projectId && task.getBuildDefinitionId() == buildDefinitionId )
                {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isProjectCurrentlyPreparingBuild( int projectId, int buildDefinitionId )
        throws ContinuumException
    {
        boolean found = false;

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                try
                {
                    if ( isAgentAvailable( buildAgentUrl ) )
                    {
                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                        List<Map<String, Object>> projects = client.getProjectsAndBuildDefinitionsCurrentlyPreparingBuild();
    
                        for ( Map<String, Object> context : projects )
                        {
                            int pid = ContinuumBuildConstant.getProjectId( context );
                            int buildId = ContinuumBuildConstant.getBuildDefinitionId( context );
    
                            if ( pid == projectId && buildId == buildDefinitionId )
                            {
                                found = true;
                                break;
                            }
                        }
                    }

                    if ( found )
                    {
                        break;
                    }
                }
                catch ( MalformedURLException e )
                {
                    throw new ContinuumException( "Invalid build agent url: " + buildAgentUrl );
                }
                catch ( Exception e )
                {
                    throw new ContinuumException( "Error retrieving projects currently preparing build in " + buildAgentUrl, e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        if ( found )
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean isProjectCurrentlyBuilding( int projectId, int buildDefinitionId )
        throws ContinuumException
    {
        Map<String, BuildProjectTask> map = getProjectsCurrentlyBuilding();

        for ( String url : map.keySet() )
        {
            BuildProjectTask task = map.get( url );

            if ( task.getProjectId() == projectId && task.getBuildDefinitionId() == buildDefinitionId )
            {
                return true;
            }
        }

        return false;
    }

    private String getBuildAgent( int projectId )
        throws ContinuumException
    {
        String agentUrl = null;

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                OverallDistributedBuildQueue overallDistributedBuildQueue = 
                    overallDistributedBuildQueues.get( buildAgentUrl );
    
                if ( overallDistributedBuildQueue != null )
                {
                    try
                    {
                        if ( isAgentAvailable( buildAgentUrl ) )
                        {
                            SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                            
                            if ( client.isProjectCurrentlyBuilding( projectId ) )
                            {
                                agentUrl = buildAgentUrl;
                                break;
                            }
                        }
                    }
                    catch ( MalformedURLException e )
                    {
                        log.warn( "Unable to check if project " + projectId + " is currently building in agent: Invalid build agent url" + buildAgentUrl );
                    }
                    catch ( Exception e )
                    {
                        log.warn( "Unable to check if project " + projectId + " is currently building in agent", e );
                    }
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        return agentUrl;
    }

    private void createDistributedBuildQueueForAgent( String buildAgentUrl )
        throws ComponentLookupException
    {
        if ( !overallDistributedBuildQueues.containsKey( buildAgentUrl ) )
        {
            OverallDistributedBuildQueue overallDistributedBuildQueue =
                (OverallDistributedBuildQueue) container.lookup( OverallDistributedBuildQueue.class );
            overallDistributedBuildQueue.setBuildAgentUrl( buildAgentUrl );
            overallDistributedBuildQueue.getDistributedBuildTaskQueueExecutor().setBuildAgentUrl( buildAgentUrl );

            overallDistributedBuildQueues.put( buildAgentUrl, overallDistributedBuildQueue );
        }
    }

    private OverallDistributedBuildQueue getOverallDistributedBuildQueueByGroupAndScmRoot( int projectGroupId, int scmRootId )
        throws ContinuumException
    {
        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                OverallDistributedBuildQueue distributedBuildQueue = overallDistributedBuildQueues.get( buildAgentUrl );

                try
                {
                    for ( PrepareBuildProjectsTask task : distributedBuildQueue.getProjectsInQueue() )
                    {
                        if ( task.getProjectGroupId() == projectGroupId && task.getProjectScmRootId() == scmRootId )
                        {
                            return distributedBuildQueue;
                        }
                    }
                }
                catch ( TaskQueueException e )
                {
                    log.error( "Error occurred while retrieving distributed build queue of projectGroupId=" + projectGroupId + " scmRootId=" + scmRootId, e );
                    throw new ContinuumException( "Error occurred while retrieving distributed build queue of group", e );
                }
            }
        }

        return null;
    }

    private OverallDistributedBuildQueue getOverallDistributedBuildQueueByScmRoot( ProjectScmRoot scmRoot, int projectGroupId )
        throws ContinuumException
    {
        OverallDistributedBuildQueue overallDistributedBuildQueue = null;
        int scmRootId = scmRoot.getId();

        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                OverallDistributedBuildQueue distributedBuildQueue = overallDistributedBuildQueues.get( buildAgentUrl );

                try
                {
                    for ( PrepareBuildProjectsTask task : distributedBuildQueue.getProjectsInQueue() )
                    {
                        if ( task.getProjectScmRootId() == scmRootId )
                        {
                            overallDistributedBuildQueue = distributedBuildQueue;
                            break;
                        }
                    }

                    if ( overallDistributedBuildQueue == null )
                    {
                        Task task = distributedBuildQueue.getDistributedBuildTaskQueueExecutor().getCurrentTask();
                        if ( task != null && ( (PrepareBuildProjectsTask) task ).getProjectScmRootId() == scmRootId )
                        {
                            overallDistributedBuildQueue = distributedBuildQueue;
                        }
                    }

                    if ( overallDistributedBuildQueue == null )
                    {
                        if ( isAgentAvailable( buildAgentUrl ) )
                        {
                            List<Project> projects = projectDao.getProjectsInGroup( projectGroupId );
                            List<Integer> pIds = new ArrayList<Integer>();
                            
                            for ( Project project : projects )
                            {
                                if ( project.getScmUrl().startsWith( scmRoot.getScmRootAddress() ) )
                                {
                                    pIds.add( project.getId() );
                                }
                            }

                            SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );

                            if ( client.isProjectScmRootInQueue( scmRootId, pIds ) )
                            {
                                overallDistributedBuildQueue = distributedBuildQueue;
                            }
                        }
                    }

                    if ( overallDistributedBuildQueue != null )
                    {
                        break;
                    }
                }
                catch ( TaskQueueException e )
                {
                    log.error( "Error occurred while retrieving distributed build queue of scmRootId=" + scmRootId, e );
                    throw new ContinuumException( "Error occurred while retrieving distributed build queue of scmRoot", e );
                }
                catch ( MalformedURLException e )
                {
                    log.error( "Error occurred while retrieving distributed build queue of scmRootId=" + scmRootId + 
                               ": Invalid build agent url " + buildAgentUrl );
                    throw new ContinuumException( "Error occurred while retrieving distributed build queue of scmRootId=" + scmRootId + 
                               ": Invalid build agent url " + buildAgentUrl );
                }
                catch ( Exception e )
                {
                    log.error( "Error occurred while retrieving distributed build queue of scmRootId=" + scmRootId, e );
                    throw new ContinuumException( "Error occurred while retrieving distributed build queue of scmRoot", e );
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        return overallDistributedBuildQueue;
    }

    private OverallDistributedBuildQueue getOverallDistributedBuildQueueByGroup( int projectGroupId, List<ProjectScmRoot> scmRoots, int scmRootId )
        throws ContinuumException
    {
        if ( scmRoots != null )
        {
            for ( ProjectScmRoot scmRoot : scmRoots )
            {
                if ( scmRoot.getId() == scmRootId )
                {
                    break;
                }
                else if ( scmRoot.getProjectGroup().getId() == projectGroupId )
                {
                    return getOverallDistributedBuildQueueByScmRoot( scmRoot, projectGroupId );
                }
            }
        }
        return null;
    }

    // need to change this
    private OverallDistributedBuildQueue getOverallDistributedBuildQueueByHashCode( int hashCode )
        throws ContinuumException
    {
        synchronized( overallDistributedBuildQueues )
        {
            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                OverallDistributedBuildQueue distributedBuildQueue = overallDistributedBuildQueues.get( buildAgentUrl );

                try
                {
                    for ( PrepareBuildProjectsTask task : distributedBuildQueue.getProjectsInQueue() )
                    {
                        if ( task.getHashCode() == hashCode )
                        {
                            return distributedBuildQueue;
                        }
                    }
                }
                catch ( TaskQueueException e )
                {
                    log.error( "Error occurred while retrieving distributed build queue", e );
                    throw new ContinuumException( "Error occurred while retrieving distributed build queue", e );
                }
            }
        }

        return null;
    }

    private OverallDistributedBuildQueue getOverallDistributedBuildQueueByAgentGroup( Map<Integer, Integer> projectsAndBuildDefinitionsMap )
        throws ContinuumException
    {
        OverallDistributedBuildQueue whereToBeQueued = null;

        BuildAgentGroupConfiguration buildAgentGroup = getBuildAgentGroup( projectsAndBuildDefinitionsMap );

        if ( buildAgentGroup != null )
        {
            List<BuildAgentConfiguration> buildAgents = buildAgentGroup.getBuildAgents();

            if ( buildAgents != null && buildAgents.size() > 0 )
            {
                List<String> buildAgentUrls = new ArrayList<String>();
                
                for ( BuildAgentConfiguration buildAgent : buildAgents )
                {
                    buildAgentUrls.add( buildAgent.getUrl() );
                }

                synchronized( overallDistributedBuildQueues )
                {
                    int idx = 0;
                    int size = 0;
                    
                    for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
                    {
                        if ( !buildAgentUrls.isEmpty() && buildAgentUrls.contains( buildAgentUrl ) )
                        {
                            OverallDistributedBuildQueue distributedBuildQueue = overallDistributedBuildQueues.get( buildAgentUrl );

                            if ( distributedBuildQueue != null )
                            {
                                try
                                {
                                    if ( isAgentAvailable( buildAgentUrl ) )
                                    {
                                        SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                                        int agentBuildSize = client.getBuildSizeOfAgent();
    
                                        if ( idx == 0 )
                                        {
                                            whereToBeQueued = distributedBuildQueue;
                                            size = agentBuildSize;
                                            idx++;
                                        }
    
                                        if ( agentBuildSize < size )
                                        {
                                            whereToBeQueued = distributedBuildQueue;
                                            size = agentBuildSize;
                                        }
                                    }
                                }
                                catch ( MalformedURLException e )
                                {
                                    log.error( "Error occurred while retrieving distributed build queue: Invalid build agent url " + buildAgentUrl );
                                }
                                catch ( Exception e )
                                {
                                    log.error( "Error occurred while retrieving distributed build queue ", e );
                                }
                            }
                        }
                    }
                }

                // call reload in case we disable a build agent
                reload();
            }
        }
        return whereToBeQueued;
    }

    private OverallDistributedBuildQueue getOverallDistributedBuildQueue()
        throws ContinuumException
    {
        OverallDistributedBuildQueue whereToBeQueued = null;

        synchronized ( overallDistributedBuildQueues )
        {
            if ( overallDistributedBuildQueues.isEmpty() )
            {
                log.info( "No distributed build queues are configured for build agents" );
                return null;
            }

            int idx = 0;
            int size = 0;

            for ( String buildAgentUrl : overallDistributedBuildQueues.keySet() )
            {
                OverallDistributedBuildQueue distributedBuildQueue = overallDistributedBuildQueues.get( buildAgentUrl );

                if ( distributedBuildQueue != null )
                {
                    try
                    {
                        if ( isAgentAvailable( buildAgentUrl ) )
                        {
                            SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );
                            int agentBuildSize = client.getBuildSizeOfAgent();
    
                            if ( idx == 0 )
                            {
                                whereToBeQueued = distributedBuildQueue;
                                size = agentBuildSize;
                                idx++;
                            }
    
                            if ( agentBuildSize < size )
                            {
                                whereToBeQueued = distributedBuildQueue;
                                size = agentBuildSize;
                            }
                        }
                    }
                    catch ( MalformedURLException e )
                    {
                        log.error( "Error occurred while retrieving distributed build queue: invalid build agent url " + buildAgentUrl );
                    }
                    catch ( Exception e )
                    {
                        log.error( "Error occurred while retrieving distributed build queue", e );
                        throw new ContinuumException( "Error occurred while retrieving distributed build queue", e );
                    }
                }
            }
        }

        // call reload in case we disable a build agent
        reload();

        return whereToBeQueued;
    }

    private BuildAgentGroupConfiguration getBuildAgentGroup( Map<Integer, Integer> projectsAndBuildDefinitions )
        throws ContinuumException
    {
        if ( projectsAndBuildDefinitions == null )
        {
            return null;
        }
        
        try
        {
            List<Project> projects = new ArrayList<Project>();

            for ( Integer projectId : projectsAndBuildDefinitions.keySet() )
            {
                projects.add( projectDao.getProjectWithDependencies( projectId ) );
            }

            projects = ProjectSorter.getSortedProjects( projects, log );

            int buildDefinitionId = projectsAndBuildDefinitions.get( projects.get( 0 ).getId() );
            BuildDefinition buildDefinition = buildDefinitionDao.getBuildDefinition( buildDefinitionId );

            Profile profile = buildDefinition.getProfile();

            if ( profile != null && !StringUtils.isEmpty( profile.getBuildAgentGroup() ) )
            {
                String groupName = profile.getBuildAgentGroup();

                BuildAgentGroupConfiguration buildAgentGroup = configurationService.getBuildAgentGroup( groupName );

                return buildAgentGroup;
            }
        }
        catch ( ContinuumStoreException e )
        {
            log.error( "Error while getting build agent group", e );
            throw new ContinuumException( "Error while getting build agent group", e );
        }

        log.info( "profile build agent group is null" );

        return null;
    }
 
    private PrepareBuildProjectsTask getPrepareBuildProjectsTask( Map context )
    {
        int projectGroupId = ContinuumBuildConstant.getProjectGroupId( context );
        int scmRootId = ContinuumBuildConstant.getScmRootId( context );
        String scmRootAddress = ContinuumBuildConstant.getScmRootAddress( context );
        BuildTrigger buildTrigger = new BuildTrigger( ContinuumBuildConstant.getTrigger( context ), ContinuumBuildConstant.getUsername( context ) );

        return new PrepareBuildProjectsTask( null, buildTrigger, projectGroupId, null, scmRootAddress, scmRootId );
    }

    private BuildProjectTask getBuildProjectTask( Map context )
    {
        int projectId = ContinuumBuildConstant.getProjectId( context );
        int buildDefinitionId = ContinuumBuildConstant.getBuildDefinitionId( context );
        BuildTrigger buildTrigger = new BuildTrigger( ContinuumBuildConstant.getTrigger( context ), ContinuumBuildConstant.getUsername( context ) );
        int projectGroupId = ContinuumBuildConstant.getProjectGroupId( context );
        String buildDefinitionLabel = ContinuumBuildConstant.getBuildDefinitionLabel( context );

        return new BuildProjectTask( projectId, buildDefinitionId, buildTrigger, null, buildDefinitionLabel, null, projectGroupId );
    }

    public boolean isAgentAvailable( String buildAgentUrl )
        throws ContinuumException
    {
        try
        {
            SlaveBuildAgentTransportService client = createSlaveBuildAgentTransportClientConnection( buildAgentUrl );

            return client.ping();
        }
        catch ( MalformedURLException e )
        {
            log.warn( "Invalid build agent url" + buildAgentUrl );
        }
        catch ( Exception e )
        {
            log.warn( "Unable to ping build agent: " + buildAgentUrl + "; disabling it..." );
        }

        // disable it
        disableBuildAgent( buildAgentUrl );

        return false;
    }

    private void disableBuildAgent( String buildAgentUrl )
        throws ContinuumException
    {
        List<BuildAgentConfiguration> agents = configurationService.getBuildAgents();

        for ( BuildAgentConfiguration agent : agents )
        {
            if ( agent.getUrl().equals( buildAgentUrl ) )
            {
                agent.setEnabled( false );
                configurationService.updateBuildAgent( agent );

                try
                {
                    configurationService.store();
                }
                catch ( Exception e )
                {
                    throw new ContinuumException( "Unable to disable build agent: " + buildAgentUrl, e );
                }
            }
        }
    }
    
    private boolean hasBuildagentGroup( Map<Integer, Integer> projectsAndBuildDefinitionsMap )
        throws ContinuumException
    {
        BuildAgentGroupConfiguration buildAgentGroup = getBuildAgentGroup( projectsAndBuildDefinitionsMap );

        return buildAgentGroup != null &&
               buildAgentGroup.getName().length() > 0 ? true : false;
    }
    
    private boolean hasBuildagentInGroup( Map<Integer, Integer> projectsAndBuildDefinitionsMap )
        throws ContinuumException
    {
        BuildAgentGroupConfiguration buildAgentGroup = getBuildAgentGroup( projectsAndBuildDefinitionsMap );

        return buildAgentGroup != null &&
               buildAgentGroup.getBuildAgents().size() > 0 ? true : false;
    }

    public SlaveBuildAgentTransportService createSlaveBuildAgentTransportClientConnection( String buildAgentUrl ) 
        throws MalformedURLException, Exception
    {
        return new SlaveBuildAgentTransportClient( new URL( buildAgentUrl ) );
    }

    // for unit testing

    public void setOverallDistributedBuildQueues( Map<String, OverallDistributedBuildQueue> overallDistributedBuildQueues )
    {
        this.overallDistributedBuildQueues = overallDistributedBuildQueues;
    }

    public void setConfigurationService( ConfigurationService configurationService )
    {
        this.configurationService = configurationService;
    }

    public void setProjectDao( ProjectDao projectDao )
    {
        this.projectDao = projectDao;
    }

    public void setBuildDefinitionDao( BuildDefinitionDao buildDefinitionDao )
    {
        this.buildDefinitionDao = buildDefinitionDao;
    }

    public void setBuildResultDao( BuildResultDao buildResultDao )
    {
        this.buildResultDao = buildResultDao;
    }

    public void setContainer( PlexusContainer container )
    {
        this.container = container;
    }

}