package org.apache.continuum.xmlrpc.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.continuum.release.distributed.manager.DistributedReleaseManager;
import org.apache.continuum.xmlrpc.utils.BuildTrigger;
import org.apache.maven.continuum.Continuum;
import org.apache.maven.continuum.configuration.ConfigurationService;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.model.project.ProjectGroup;
import org.apache.maven.continuum.release.ContinuumReleaseManager;
import org.apache.maven.continuum.xmlrpc.project.BuildDefinition;
import org.apache.maven.continuum.xmlrpc.project.ContinuumProjectState;
import org.apache.maven.continuum.xmlrpc.project.ReleaseListenerSummary;
import org.apache.maven.continuum.xmlrpc.server.ContinuumServiceImpl;
import org.codehaus.plexus.spring.PlexusInSpringTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit3.JUnit3Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

public class ContinuumServiceImplTest
    extends PlexusInSpringTestCase
{
    private ContinuumServiceImpl continuumService;

    private Mockery context;

    private Continuum continuum;

    private DistributedReleaseManager distributedReleaseManager;

    private ContinuumReleaseManager releaseManager;

    private ConfigurationService configurationService;

    private Project project;

    private Map<String, Object> params;

    @Override
    public void setUp()
        throws Exception
    {
        super.setUp();

        context = new JUnit3Mockery();
        context.setImposteriser( ClassImposteriser.INSTANCE );

        continuumService = new ContinuumServiceImplStub();
        continuum = context.mock( Continuum.class );
        continuumService.setContinuum( continuum );

        distributedReleaseManager = context.mock( DistributedReleaseManager.class );
        releaseManager = context.mock( ContinuumReleaseManager.class );
        configurationService = context.mock( ConfigurationService.class );

        ProjectGroup projectGroup = new ProjectGroup();
        projectGroup.setName( "test-group" );

        project = new Project();
        project.setId( 1 );
        project.setProjectGroup( projectGroup );
        project.setVersion( "1.0-SNAPSHOT" );
        project.setArtifactId( "continuum-test" );
        project.setScmUrl( "scm:svn:http://svn.test.org/repository/project" );
    }

    public void testGetReleasePluginParameters()
        throws Exception
    {
        params = new HashMap<String, Object>();
        params.put( "scm-tag", "" );
        params.put( "scm-tagbase", "" );

        context.checking( new Expectations()
        {
            {
                one( continuum ).getProject( 1 );
                will( returnValue( project ) );

                one( continuum ).getConfiguration();
                will( returnValue( configurationService ) );

                one( configurationService ).isDistributedBuildEnabled();
                will( returnValue( true ) );

                one( continuum ).getDistributedReleaseManager();
                will( returnValue( distributedReleaseManager ) );

                one( distributedReleaseManager ).getReleasePluginParameters( 1, "pom.xml" );
                will( returnValue( params ) );

                one( continuum ).getReleaseManager();
                will( returnValue( releaseManager ) );

                one( releaseManager ).sanitizeTagName( "scm:svn:http://svn.test.org/repository/project",
                                                       "continuum-test-1.0" );
            }
        } );

        Map<String, Object> releaseParams = continuumService.getReleasePluginParameters( 1 );
        assertEquals( "continuum-test-1.0", releaseParams.get( "scm-tag" ) );
        assertEquals( "http://svn.test.org/repository/project/tags", releaseParams.get( "scm-tagbase" ) );

        context.assertIsSatisfied();
    }

    public void testGetListenerWithDistributedBuilds()
        throws Exception
    {
        final Map map = getListenerMap();

        context.checking( new Expectations()
        {
            {
                one( continuum ).getProject( 1 );
                will( returnValue( project ) );

                one( continuum ).getConfiguration();
                will( returnValue( configurationService ) );

                one( configurationService ).isDistributedBuildEnabled();
                will( returnValue( true ) );

                one( continuum ).getDistributedReleaseManager();
                will( returnValue( distributedReleaseManager ) );

                one( distributedReleaseManager ).getListener( "releaseId-1" );
                will( returnValue( map ) );
            }
        } );

        ReleaseListenerSummary summary = continuumService.getListener( 1, "releaseId-1" );
        assertNotNull( summary );
        assertEquals( "incomplete-phase", summary.getPhases().get( 0 ) );
        assertEquals( "completed-phase", summary.getCompletedPhases().get( 0 ) );
    }
    
    public void testPopulateBuildDefinition()
        throws Exception
    {
        ContinuumServiceImplStub continuumServiceStub = new ContinuumServiceImplStub();
        
        BuildDefinition buildDef = createBuildDefinition();
        org.apache.maven.continuum.model.project.BuildDefinition buildDefinition = new org.apache.maven.continuum.model.project.BuildDefinition();
        
        buildDefinition = continuumServiceStub.getBuildDefinition( buildDef, buildDefinition );
        
        assertEquals( buildDef.getArguments(), buildDefinition.getArguments() );
        assertEquals( buildDef.getBuildFile(), buildDefinition.getBuildFile() );
        assertEquals( buildDef.getDescription(), buildDefinition.getDescription() );
        assertEquals( buildDef.getGoals(), buildDefinition.getGoals() );
        assertEquals( buildDef.getType(), buildDefinition.getType() );
        assertEquals( buildDef.isAlwaysBuild(), buildDefinition.isAlwaysBuild() );
        assertEquals( buildDef.isBuildFresh(), buildDefinition.isBuildFresh() );
        assertEquals( buildDef.isDefaultForProject(), buildDefinition.isDefaultForProject() );
    }
    
    public void testBuildProjectWithBuildTrigger()
        throws Exception
    {
        final ProjectGroup projectGroup = new ProjectGroup();
        projectGroup.setName( "test-group" );
        
        BuildTrigger buildTrigger = new BuildTrigger();
        buildTrigger.setTrigger( ContinuumProjectState.TRIGGER_FORCED );
        buildTrigger.setTriggeredBy( "username" );

        BuildDefinition buildDef = createBuildDefinition();
        buildDef.setId( 1 );
    
        context.checking( new Expectations()
        {
            {
                atLeast( 1 ).of( continuum ).getProject( project.getId() );
                will( returnValue( project ) );
                
                atLeast( 1 ).of( continuum ).getProjectGroupByProjectId( project.getId() );
                will( returnValue( projectGroup ) );
            }
        });
    
        int result = continuumService.buildProject( project.getId(), buildDef.getId(), buildTrigger );
        assertEquals( 0, result );
    
    }
    
    private BuildDefinition createBuildDefinition()
    {
        BuildDefinition buildDef = new BuildDefinition();
        buildDef.setArguments( "--batch-mode" );
        buildDef.setBuildFile( "pom.xml" );
        buildDef.setType( "maven2" );
        buildDef.setBuildFresh( false );
        buildDef.setAlwaysBuild( true );
        buildDef.setDefaultForProject( true );
        buildDef.setGoals( "clean install" );
        buildDef.setDescription( "Test Build Definition" );
        
        return buildDef;
    }

    private Map<String, Object> getListenerMap()
    {
        Map<String, Object> map = new HashMap<String, Object>();
        
        map.put( "release-phases", Arrays.asList( "incomplete-phase" ) );
        map.put( "completed-release-phases", Arrays.asList( "completed-phase" ) );
        return map;
    }
}
