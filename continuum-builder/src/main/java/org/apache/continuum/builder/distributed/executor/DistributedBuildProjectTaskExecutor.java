package org.apache.continuum.builder.distributed.executor;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.continuum.builder.utils.ContinuumBuildConstant;
import org.apache.continuum.dao.BuildDefinitionDao;
import org.apache.continuum.dao.BuildResultDao;
import org.apache.continuum.dao.ProjectDao;
import org.apache.continuum.dao.ProjectScmRootDao;
import org.apache.continuum.distributed.transport.master.ProxySlaveAgentTransportService;
import org.apache.continuum.model.project.ProjectScmRoot;
import org.apache.continuum.taskqueue.PrepareBuildProjectsTask;
import org.apache.continuum.utils.ContinuumUtils;
import org.apache.continuum.utils.ProjectSorter;
import org.apache.maven.continuum.ContinuumException;
import org.apache.maven.continuum.model.project.BuildDefinition;
import org.apache.maven.continuum.model.project.BuildResult;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.project.ContinuumProjectState;
import org.apache.maven.continuum.store.ContinuumStoreException;
import org.codehaus.plexus.taskqueue.Task;
import org.codehaus.plexus.taskqueue.execution.TaskExecutionException;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedBuildProjectTaskExecutor
    implements DistributedBuildTaskExecutor
{
    private Logger log = LoggerFactory.getLogger( this.getClass() );

    private String buildAgentUrl;

    private long startTime;

    private long endTime;

    /**
     * @plexus.requirement
     */
    private ProjectDao projectDao;

    /**
     * @plexus.requirement
     */
    private ProjectScmRootDao projectScmRootDao;

    /**
     * @plexus.requirement
     */
    private BuildDefinitionDao buildDefinitionDao;

    /**
     * @plexus.requirement
     */
    private BuildResultDao buildResultDao;

    public void setBuildAgentUrl( String buildAgentUrl )
    {
        this.buildAgentUrl = buildAgentUrl;
    }

    public String getBuildAgentUrl()
    {
        return buildAgentUrl;
    }

    public void executeTask( Task task )
        throws TaskExecutionException
    {
        PrepareBuildProjectsTask prepareBuildTask = (PrepareBuildProjectsTask) task;

        try
        {
            ProxySlaveAgentTransportService client = new ProxySlaveAgentTransportService( new URL( buildAgentUrl ) );

            log.info( "initializing buildContext" );
            List buildContext = initializeBuildContext( prepareBuildTask.getProjectsBuildDefinitionsMap(), 
                                                        prepareBuildTask.getTrigger() );

            startTime = System.currentTimeMillis();
            client.buildProjects( buildContext );
            endTime = System.currentTimeMillis();
        }
        catch ( MalformedURLException e )
        {
            log.error( "Invalid URL " + buildAgentUrl + ", not building" );
            throw new TaskExecutionException( "Invalid URL " + buildAgentUrl, e );
        }
        catch ( Exception e )
        {
            log.error( "Error occurred while building task", e );
            endTime = System.currentTimeMillis();
            createResult( prepareBuildTask, ContinuumUtils.throwableToString( e ) );
        }
    }

    private List initializeBuildContext( Map<Integer, Integer> projectsAndBuildDefinitions, 
                                         int trigger )
        throws ContinuumException
    {
        List buildContext = new ArrayList();
        List<Project> projects = new ArrayList<Project>();

        try
        {
            for ( Integer projectId : projectsAndBuildDefinitions.keySet() )
            {
                Project project = projectDao.getProjectWithDependencies( projectId );
                projects.add( project );
            }

            try
            {
                projects = ProjectSorter.getSortedProjects( projects, null );
            }
            catch ( CycleDetectedException e )
            {
                log.info( "Cycle Detected" );
            }

            int ctr = 0;
            String scmRootAddress = "";

            for ( Project project : projects )
            {
                if ( ctr == 0 )
                {
                    List<ProjectScmRoot> scmRoots = projectScmRootDao.getProjectScmRootByProjectGroup( project.getProjectGroup().getId() );
                    for ( ProjectScmRoot scmRoot : scmRoots )
                    {
                        if ( project.getScmUrl().startsWith( scmRoot.getScmRootAddress() ) )
                        {
                            scmRootAddress = scmRoot.getScmRootAddress();
                            scmRoot.setOldState( scmRoot.getState() );
                            scmRoot.setState( ContinuumProjectState.UPDATING );
                            projectScmRootDao.updateProjectScmRoot( scmRoot );
                            break;
                        }
                    }
                }
                
                int buildDefinitionId = projectsAndBuildDefinitions.get( project.getId() );
                BuildDefinition buildDef = buildDefinitionDao.getBuildDefinition( buildDefinitionId );
                BuildResult oldBuildResult =
                    buildResultDao.getLatestBuildResultForBuildDefinition( project.getId(), buildDefinitionId );

                Map context = new HashMap();
                context.put( ContinuumBuildConstant.KEY_PROJECT_GROUP_ID, project.getProjectGroup().getId() );
                context.put( ContinuumBuildConstant.KEY_SCM_ROOT_ADDRESS, scmRootAddress );
                context.put( ContinuumBuildConstant.KEY_PROJECT_ID, project.getId() );
                context.put( ContinuumBuildConstant.KEY_EXECUTOR_ID, project.getExecutorId() );
                context.put( ContinuumBuildConstant.KEY_SCM_URL, project.getScmUrl() );
                context.put( ContinuumBuildConstant.KEY_SCM_USERNAME, project.getScmUsername() );
                context.put( ContinuumBuildConstant.KEY_SCM_PASSWORD, project.getScmPassword() );
                context.put( ContinuumBuildConstant.KEY_BUILD_DEFINITION_ID, buildDefinitionId );
                context.put( ContinuumBuildConstant.KEY_BUILD_FILE, buildDef.getBuildFile() );
                context.put( ContinuumBuildConstant.KEY_GOALS, buildDef.getGoals() );
                context.put( ContinuumBuildConstant.KEY_ARGUMENTS, buildDef.getArguments() );
                context.put( ContinuumBuildConstant.KEY_TRIGGER, trigger );
                context.put( ContinuumBuildConstant.KEY_BUILD_FRESH, buildDef.isBuildFresh() );
                
                buildContext.add( context );
                ctr++;
            }

            return buildContext;
        }
        catch ( ContinuumStoreException e )
        {
            throw new ContinuumException( "Error while initializing build context", e );
        }
    }

    private void createResult( PrepareBuildProjectsTask task, String error )
        throws TaskExecutionException
    {
        try
        {
            ProjectScmRoot scmRoot = projectScmRootDao.getProjectScmRootByProjectGroupAndScmRootAddress( task.getProjectGroupId(), task.getScmRootAddress() );

            if ( scmRoot.getState() == ContinuumProjectState.UPDATING )
            {
                scmRoot.setState( ContinuumProjectState.ERROR );
                scmRoot.setError( error );
                projectScmRootDao.updateProjectScmRoot( scmRoot );
            }
            else
            {
                Map<Integer, Integer> map = task.getProjectsBuildDefinitionsMap();
                for ( Integer projectId : map.keySet() )
                {
                    int buildDefinitionId = map.get( projectId );
                    Project project = projectDao.getProject( projectId );
                    BuildDefinition buildDef = buildDefinitionDao.getBuildDefinition( buildDefinitionId );

                    BuildResult latestBuildResult = buildResultDao.
                                                        getLatestBuildResultForBuildDefinition( projectId, buildDefinitionId );
                    if ( ( latestBuildResult.getStartTime() >= startTime && latestBuildResult.getEndTime() > 0 && 
                           latestBuildResult.getEndTime() < endTime ) || latestBuildResult.getStartTime() < startTime )
                    {
                        BuildResult buildResult = new BuildResult();
                        buildResult.setBuildDefinition( buildDef );
                        buildResult.setError( error );
                        buildResult.setState( ContinuumProjectState.ERROR );
                        buildResult.setTrigger( task.getTrigger() );
                        buildResult.setStartTime( startTime );
                        buildResult.setEndTime( endTime );

                        buildResultDao.addBuildResult( project, buildResult );
                    }
                }

            }
        }
        catch ( ContinuumStoreException e )
        {
            throw new TaskExecutionException( "" );
        }
    }
}