package org.apache.maven.continuum;

/*
 * Copyright 2004-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.continuum.core.ContinuumCore;
import org.apache.maven.continuum.core.action.AbstractContinuumAction;
import org.apache.maven.continuum.core.action.AddProjectToCheckOutQueueAction;
import org.apache.maven.continuum.core.action.CreateProjectsFromMetadata;
import org.apache.maven.continuum.core.action.StoreProjectAction;
import org.apache.maven.continuum.execution.ant.AntBuildExecutor;
import org.apache.maven.continuum.execution.maven.m1.MavenOneBuildExecutor;
import org.apache.maven.continuum.execution.maven.m2.MavenTwoBuildExecutor;
import org.apache.maven.continuum.execution.shell.ShellBuildExecutor;
import org.apache.maven.continuum.project.AntProject;
import org.apache.maven.continuum.project.ContinuumBuild;
import org.apache.maven.continuum.project.ContinuumNotifier;
import org.apache.maven.continuum.project.ContinuumProject;
import org.apache.maven.continuum.project.MavenOneProject;
import org.apache.maven.continuum.project.MavenTwoProject;
import org.apache.maven.continuum.project.ShellProject;
import org.apache.maven.continuum.project.builder.ContinuumProjectBuildingResult;
import org.apache.maven.continuum.project.builder.maven.MavenOneContinuumProjectBuilder;
import org.apache.maven.continuum.project.builder.maven.MavenTwoContinuumProjectBuilder;
import org.apache.maven.continuum.scm.ScmResult;
import org.apache.maven.continuum.utils.ProjectSorter;
import org.codehaus.plexus.action.ActionManager;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.dag.CycleDetectedException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l </a>
 * @version $Id$
 */
public class DefaultContinuum
    extends AbstractLogEnabled
    implements Continuum
{
    /**
     * @plexus.requirement
     */
    private ContinuumCore core;

    /**
     * @plexus.requirement
     */
    private ActionManager actionManager;

    // ----------------------------------------------------------------------
    // Projects
    // ----------------------------------------------------------------------

    public Collection getProjects()
        throws ContinuumException
    {
        return core.getProjects();
    }

    public ContinuumBuild getLatestBuildForProject( String id )
        throws ContinuumException
    {
        return core.getLatestBuildForProject( id );
    }

    // ----------------------------------------------------------------------
    // Queues
    // ----------------------------------------------------------------------

    public boolean isInBuildingQueue( String id )
        throws ContinuumException
    {
        return core.isBuilding( id );
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public void removeProject( String projectId )
        throws ContinuumException
    {
        core.removeProject( projectId );
    }

    public void checkoutProject( String id )
        throws ContinuumException
    {
        Map context = new HashMap();

        context.put( AddProjectToCheckOutQueueAction.KEY_PROJECT_ID, id );

        try
        {
            actionManager.lookup( "add-project-to-checkout-queue" ).execute( context );
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error while adding the project to the check out queue.", e );
        }
    }

    public ContinuumProject getProject( String projectId )
        throws ContinuumException
    {
        return core.getProject( projectId );
    }

    public Collection getAllProjects( int start, int end )
        throws ContinuumException
    {
        return core.getAllProjects( start, end );
    }

    public ScmResult getScmResultForProject( String projectId )
        throws ContinuumException
    {
        return core.getScmResultForProject( projectId );
    }

    // ----------------------------------------------------------------------
    // Building
    // ----------------------------------------------------------------------

    public void buildProjects()
        throws ContinuumException
    {
        buildProjects( true );
    }

    public void buildProjects( boolean force )
        throws ContinuumException
    {
        for ( Iterator i = getProjects().iterator(); i.hasNext(); )
        {
            ContinuumProject project = (ContinuumProject) i.next();

            buildProject( project.getId(), force );
        }

        /*
        try
        {
            for ( Iterator i = getProjectsInBuildOrder().iterator(); i.hasNext(); )
            {
                ContinuumProject project = (ContinuumProject) i.next();

                buildProject( project.getId(), force );
            }
        }
        catch ( CycleDetectedException e )
        {
            getLogger().warn( "Cycle detected while sorting projects for building, falling back to unsorted build." );

            for ( Iterator i = getProjects().iterator(); i.hasNext(); )
            {
                ContinuumProject project = (ContinuumProject) i.next();

                buildProject( project.getId(), force );
            }
        }
        */
    }

    public List getProjectsInBuildOrder()
        throws CycleDetectedException, ContinuumException
    {
        return ProjectSorter.getSortedProjects( getProjects() );
    }

    public void buildProject( String projectId )
        throws ContinuumException
    {
        core.buildProject( projectId, true );
    }

    public void buildProject( String projectId, boolean force )
        throws ContinuumException
    {
        core.buildProject( projectId, force );
    }

    // ----------------------------------------------------------------------
    // Build inforation
    // ----------------------------------------------------------------------

    public ContinuumBuild getBuild( String buildId )
        throws ContinuumException
    {
        return core.getBuild( buildId );
    }

    public Collection getBuildsForProject( String projectId )
        throws ContinuumException
    {
        return core.getBuildsForProject( projectId );
    }

    public Collection getChangedFilesForBuild( String buildId )
        throws ContinuumException
    {
        return core.getChangedFilesForBuild( buildId );
    }

    // ----------------------------------------------------------------------
    // Ant Projects
    // ----------------------------------------------------------------------

    public String addAntProject( AntProject project )
        throws ContinuumException
    {
        project.setExecutorId( AntBuildExecutor.ID );

        return executeAddProjectFromScmActivity( project );
    }

    public AntProject getAntProject( String projectId )
        throws ContinuumException
    {
        return (AntProject) core.getProject( projectId );
    }

    public void updateAntProject( AntProject project )
        throws ContinuumException
    {
        executeUpdateProjectActivity( project );
    }

    // ----------------------------------------------------------------------
    // Maven 1.x projects
    // ----------------------------------------------------------------------

    public ContinuumProjectBuildingResult addMavenOneProject( String metadataUrl )
        throws ContinuumException
    {
        Map context = new HashMap();

        context.put( CreateProjectsFromMetadata.KEY_PROJECT_BUILDER_ID, MavenOneContinuumProjectBuilder.ID );

        context.put( CreateProjectsFromMetadata.KEY_URL, metadataUrl );

        context.put( CreateProjectsFromMetadata.KEY_WORKING_DIRECTORY, core.getWorkingDirectory() );

        ContinuumProjectBuildingResult result;

        try
        {
            // ----------------------------------------------------------------------
            // During the execution of the this action we may find that the metadata
            // isn't good enough for the following reasons:
            //
            // 1) No scm element (repository element for m1)
            // 2) Invalid scm element (repository element for m1)
            // 3) No ciManagement (m2)
            // 4) Invalid ciManagement element (m2)
            // ----------------------------------------------------------------------

            actionManager.lookup( "create-projects-from-metadata" ).execute( context );

            result = (ContinuumProjectBuildingResult) context.get( CreateProjectsFromMetadata.KEY_PROJECT_BUILDING_RESULT );

            if ( result.getWarnings().size() > 0 )
            {
                return result;
            }

            List projects = result.getProjects();

            for ( Iterator i = projects.iterator(); i.hasNext(); )
            {
                ContinuumProject project = (ContinuumProject) i.next();

                project.setExecutorId( MavenOneBuildExecutor.ID );

                context.put( AbstractContinuumAction.KEY_UNVALIDATED_PROJECT, project );

                actionManager.lookup( "validate-project" ).execute( context );

                actionManager.lookup( "store-project" ).execute( context );

                project.setId( (String) context.get( StoreProjectAction.KEY_PROJECT_ID ) );

                actionManager.lookup( "add-project-to-checkout-queue" ).execute( context );
            }
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error adding Maven 1 project.", e );
        }

        return result;
    }

    public String addMavenOneProject( MavenOneProject project )
        throws ContinuumException
    {
        project.setExecutorId( MavenOneBuildExecutor.ID );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        Map context = new HashMap();

        context.put( AbstractContinuumAction.KEY_UNVALIDATED_PROJECT, project );

        try
        {
            actionManager.lookup( "validate-project" ).execute( context );

            actionManager.lookup( "store-project" ).execute( context );

            project.setId( (String) context.get( StoreProjectAction.KEY_PROJECT_ID ) );

            actionManager.lookup( "add-project-to-checkout-queue" ).execute( context );

            return project.getId();
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error adding Maven 1 project.", e );
        }
    }

    public MavenOneProject getMavenOneProject( String projectId )
        throws ContinuumException
    {
        return (MavenOneProject) core.getProject( projectId );
    }

    public void updateMavenOneProject( MavenOneProject project )
        throws ContinuumException
    {
        executeUpdateProjectActivity( project );
    }

    // ----------------------------------------------------------------------
    // Maven 2.x projects
    // ----------------------------------------------------------------------

    public ContinuumProjectBuildingResult addMavenTwoProject( String metadataUrl )
        throws ContinuumException
    {
        // ----------------------------------------------------------------------
        // Initialize the context
        // ----------------------------------------------------------------------

        Map context = new HashMap();

        context.put( CreateProjectsFromMetadata.KEY_PROJECT_BUILDER_ID, MavenTwoContinuumProjectBuilder.ID );

        context.put( CreateProjectsFromMetadata.KEY_URL, metadataUrl );

        context.put( CreateProjectsFromMetadata.KEY_WORKING_DIRECTORY, core.getWorkingDirectory() );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        ContinuumProjectBuildingResult result;

        try
        {
            // ----------------------------------------------------------------------
            // During the execution of the this action we may find that the metadata
            // isn't good enough for the following reasons:
            //
            // 1) No scm element (repository element for m1)
            // 2) Invalid scm element (repository element for m1)
            // 3) No ciManagement (m2)
            // 4) Invalid ciManagement element (m2)
            // ----------------------------------------------------------------------

            actionManager.lookup( "create-projects-from-metadata" ).execute( context );

            result = (ContinuumProjectBuildingResult) context.get( CreateProjectsFromMetadata.KEY_PROJECT_BUILDING_RESULT );

            if ( result.getWarnings().size() > 0 )
            {
                return result;
            }

            List projects = result.getProjects();

            for ( Iterator i = projects.iterator(); i.hasNext(); )
            {
                ContinuumProject project = (ContinuumProject) i.next();

                project.setExecutorId( MavenTwoBuildExecutor.ID );

                context.put( AbstractContinuumAction.KEY_UNVALIDATED_PROJECT, project );

                actionManager.lookup( "validate-project" ).execute( context );

                actionManager.lookup( "store-project" ).execute( context );

                project.setId( (String) context.get( StoreProjectAction.KEY_PROJECT_ID ) );

                actionManager.lookup( "add-project-to-checkout-queue" ).execute( context );
            }
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error adding Maven 2 project.", e );
        }

        return result;
    }

    public String addMavenTwoProject( MavenTwoProject project )
        throws ContinuumException
    {
        project.setExecutorId( MavenTwoBuildExecutor.ID );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        Map context = new HashMap();

        context.put( AbstractContinuumAction.KEY_UNVALIDATED_PROJECT, project );

        try
        {
            actionManager.lookup( "validate-project" ).execute( context );

            actionManager.lookup( "store-project" ).execute( context );

            project.setId( (String) context.get( StoreProjectAction.KEY_PROJECT_ID ) );

            actionManager.lookup( "add-project-to-checkout-queue" ).execute( context );
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error adding Maven 2 project.", e );
        }

        return project.getId();
    }

    public MavenTwoProject getMavenTwoProject( String projectId )
        throws ContinuumException
    {
        return (MavenTwoProject) core.getProject( projectId );
    }

    public void updateMavenTwoProject( MavenTwoProject project )
        throws ContinuumException
    {
        executeUpdateProjectActivity( project );
    }

    public String addShellProject( ShellProject project )
        throws ContinuumException
    {
        project.setExecutorId( ShellBuildExecutor.ID );

        try
        {
            return executeAddProjectFromScmActivity( project );
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error adding Shell project.", e );
        }
    }

    public ShellProject getShellProject( String projectId )
        throws ContinuumException
    {
        return (ShellProject) core.getProject( projectId );
    }

    public void updateShellProject( ShellProject project )
        throws ContinuumException
    {
        executeUpdateProjectActivity( project );
    }

    // ----------------------------------------------------------------------
    // Activities. These should end up as workflows in werkflow
    // ----------------------------------------------------------------------

    private void executeUpdateProjectActivity( ContinuumProject project )
        throws ContinuumException
    {
        core.updateProject( project );
    }

    private String executeAddProjectFromScmActivity( ContinuumProject project )
        throws ContinuumException
    {
        try
        {
            Map context = new HashMap();

            // ----------------------------------------------------------------------
            //
            // ----------------------------------------------------------------------

            context.put( AbstractContinuumAction.KEY_UNVALIDATED_PROJECT, project );

            actionManager.lookup( "validate-project" ).execute( context );

            actionManager.lookup( "store-project" ).execute( context );

            actionManager.lookup( "add-project-to-checkout-queue" ).execute( context );

            return (String) context.get( StoreProjectAction.KEY_PROJECT_ID );
        }
        catch ( Exception e )
        {
            throw new ContinuumException( "Error adding project.", e );
        }
    }

    // ----------------------------------------------------------------------
    // Notification
    // ----------------------------------------------------------------------

    // This whole section needs a scrub but will need to be dealt with generally
    // when we add schedules and profiles to the mix.

    public ContinuumNotifier getNotifier( String projectId, String notifierType )
        throws ContinuumException
    {
        ContinuumProject project = core.getProject( projectId );

        List notifiers = project.getNotifiers();

        ContinuumNotifier notifier = null;

        for ( Iterator i = notifiers.iterator(); i.hasNext(); )
        {
            notifier = (ContinuumNotifier) i.next();

            if ( notifier.getType().equals( notifierType ) )
            {
                break;
            }
        }

        return notifier;
    }

    public void updateNotifier( String projectId, String notifierType, Map configuration )
        throws ContinuumException
    {
        ContinuumNotifier notifier = getNotifier( projectId, notifierType );

        Properties notifierProperties = createNotifierProperties( configuration );

        notifier.setConfiguration( notifierProperties );

        core.storeNotifier( notifier );
    }

    private Properties createNotifierProperties( Map configuration )
    {
        Properties notifierProperties = new Properties();

        for ( Iterator i = configuration.keySet().iterator(); i.hasNext(); )
        {
            Object key = i.next();

            Object value = configuration.get( key );

            if ( value instanceof String )
            {
                notifierProperties.setProperty( (String) key, (String) value );
            }
        }

        return notifierProperties;
    }

    public void addNotifier( String projectId, String notifierType, Map configuration )
        throws ContinuumException
    {
        ContinuumNotifier notifier = new ContinuumNotifier();

        notifier.setType( notifierType );

        // ----------------------------------------------------------------------
        // Needs to be properties ... but data comes in via a Map
        // ----------------------------------------------------------------------

        Properties notifierProperties = createNotifierProperties( configuration );

        notifier.setConfiguration( notifierProperties );

        ContinuumProject project = core.getProject( projectId );

        project.addNotifier( notifier );

        core.updateProject( project );
    }

    public void removeNotifier( String projectId, String notifierType )
        throws ContinuumException
    {
        ContinuumNotifier n = getNotifier( projectId, notifierType );

        if ( n != null )
        {
            core.removeNotifier( n );
        }
    }
}
