package org.apache.maven.continuum.execution.maven.m2;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.continuum.execution.AbstractBuildExecutor;
import org.apache.maven.continuum.execution.ContinuumBuildExecutionResult;
import org.apache.maven.continuum.execution.ContinuumBuildExecutor;
import org.apache.maven.continuum.execution.ContinuumBuildExecutorException;
import org.apache.maven.continuum.model.project.BuildDefinition;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class MavenTwoBuildExecutor
    extends AbstractBuildExecutor
    implements ContinuumBuildExecutor
{
    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public static final String CONFIGURATION_GOALS = "goals";

    public static final String ID = "maven2";

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    /**
     * @plexus.requirement
     */
    private MavenBuilderHelper builderHelper;

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public MavenTwoBuildExecutor()
    {
        super( ID, true );
    }

    // ----------------------------------------------------------------------
    // ContinuumBuilder Implementation
    // ----------------------------------------------------------------------

    public ContinuumBuildExecutionResult build( Project project, BuildDefinition buildDefinition, File buildOutput )
        throws ContinuumBuildExecutorException
    {
        // TODO: get from installation
//        String executable = project.getExecutable();
        String executable = "mvn";

        String arguments = "";

        String buildFile = StringUtils.clean( buildDefinition.getBuildFile() );

        if ( !StringUtils.isEmpty( buildFile ) && !"pom.xml".equals( buildFile ) )
        {
            arguments = "-f " + buildFile + " ";
        }

        arguments +=
            StringUtils.clean( buildDefinition.getArguments() ) + " " + StringUtils.clean( buildDefinition.getGoals() );

        return executeShellCommand( project, executable, arguments, buildOutput );
    }

    public void updateProjectFromCheckOut( File workingDirectory, Project project, BuildDefinition buildDefinition )
        throws ContinuumBuildExecutorException
    {
        File f = getPomFile( buildDefinition, workingDirectory );

        if ( !f.exists() )
        {
            throw new ContinuumBuildExecutorException( "Could not find Maven project descriptor." );
        }

        try
        {
            builderHelper.mapMetadataToProject( f, project );
        }
        catch ( MavenBuilderHelperException e )
        {
            throw new ContinuumBuildExecutorException( "Error while mapping metadata.", e );
        }
    }

    private static File getPomFile( BuildDefinition buildDefinition, File workingDirectory )
    {
        File f = null;

        if ( buildDefinition != null )
        {
            String buildFile = StringUtils.clean( buildDefinition.getBuildFile() );

            if ( !StringUtils.isEmpty( buildFile ) )
            {
                f = new File( workingDirectory, buildFile );
            }
        }

        if ( f == null )
        {
            f = new File( workingDirectory, "pom.xml" );
        }

        return f;
    }

    public List getDeployableArtifacts( File workingDirectory, BuildDefinition buildDefinition )
        throws ContinuumBuildExecutorException
    {
        File f = getPomFile( buildDefinition, workingDirectory );

        if ( !f.exists() )
        {
            throw new ContinuumBuildExecutorException( "Could not find Maven project descriptor '" + f + "'." );
        }

        MavenProject project;
        try
        {
            project = builderHelper.getMavenProject( f );
        }
        catch ( MavenBuilderHelperException e )
        {
            throw new ContinuumBuildExecutorException(
                "Unable to read the Maven project descriptor '" + f + "': " + e.getMessage(), e );
        }

        List artifacts = new ArrayList( 1 );

        // Maven could help us out a lot more here by knowing how to get the deployment artifacts from a project.
        // TODO: this is currently quite lame
        if ( !"pom".equals( project.getPackaging() ) )
        {
            Artifact artifact = project.getArtifact();
            String filename = project.getBuild().getFinalName() + "." + artifact.getArtifactHandler().getExtension();
            artifact.setFile( new File( project.getBuild().getDirectory(), filename ) );
            if ( artifact.getFile().exists() )
            {
                artifacts.add( artifact );
            }
        }
        return artifacts;
    }
}
