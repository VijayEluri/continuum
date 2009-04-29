package org.apache.maven.continuum.core.action;

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

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.continuum.execution.ContinuumBuildExecutorConstants;
import org.apache.maven.continuum.model.project.BuildDefinition;
import org.apache.maven.continuum.model.project.BuildDefinitionTemplate;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.project.builder.ContinuumProjectBuilder;
import org.apache.maven.continuum.project.builder.ContinuumProjectBuildingResult;
import org.apache.maven.continuum.project.builder.manager.ContinuumProjectBuilderManager;
import org.apache.maven.continuum.utils.ContinuumUrlValidator;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.spring.PlexusInSpringTestCase;
import org.jmock.Mock;
import org.jmock.MockObjectTestCase;

public class CreateProjectsFromMetadataTest
    extends MockObjectTestCase
{
    private CreateProjectsFromMetadataAction action;
    
    private ContinuumProjectBuildingResult result;

    protected void setUp()
        throws Exception
    {
        action = new CreateProjectsFromMetadataAction();
        action.enableLogging( new ConsoleLogger( Logger.LEVEL_DEBUG, "" ) );
        
        recordBuildProjectFromHttp();
    }

    private void recordBuildProjectFromHttp()
        throws Exception
    {
        result = new ContinuumProjectBuildingResult();
        
        Mock projectBuilderManagerMock = mock( ContinuumProjectBuilderManager.class );
        
        action.setProjectBuilderManager( (ContinuumProjectBuilderManager) projectBuilderManagerMock.proxy() );
        
        action.setUrlValidator( new ContinuumUrlValidator() );
        Mock projectBuilder = mock( ContinuumProjectBuilder.class );

        projectBuilderManagerMock.expects( once() ).method( "getProjectBuilder" ).will(
            returnValue( projectBuilder.proxy() ) );
        projectBuilder.expects( once() ).method( "buildProjectsFromMetadata" ).will(
            returnValue( result ) );

        projectBuilder.expects( once() ).method( "getDefaultBuildDefinitionTemplate" ).will(
            returnValue( getDefaultBuildDefinitionTemplate() ) );
    }

    private void invokeBuildSettings()
    {
        Mock mavenSettingsBuilderMock = mock( MavenSettingsBuilder.class );
        action.setMavenSettingsBuilder( (MavenSettingsBuilder) mavenSettingsBuilderMock.proxy() );
        mavenSettingsBuilderMock.expects( once() ).method( "buildSettings" ).will( returnValue( new Settings() ) );
    }

    private BuildDefinitionTemplate getDefaultBuildDefinitionTemplate()
        throws Exception
    {
        BuildDefinition bd = new BuildDefinition();

        bd.setDefaultForProject( true );

        bd.setGoals( "clean install" );

        bd.setArguments( "-B" );

        bd.setBuildFile( "pom.xml" );

        bd.setType( ContinuumBuildExecutorConstants.MAVEN_TWO_BUILD_EXECUTOR );

        BuildDefinitionTemplate bdt = new BuildDefinitionTemplate();
        bdt.addBuildDefinition( bd );
        return bdt;
    }

    @SuppressWarnings("unchecked")
    public void testExecuteWithNonRecursiveMode()
        throws Exception
    {
        invokeBuildSettings();
        
        Map<String, Object> context = new HashMap<String, Object>();
        context.put( AbstractContinuumAction.KEY_URL,
                     "http://svn.apache.org/repos/asf/maven/continuum/trunk/pom.xml" );
        context.put( CreateProjectsFromMetadataAction.KEY_PROJECT_BUILDER_ID, "id" );
        context.put( CreateProjectsFromMetadataAction.KEY_LOAD_RECURSIVE_PROJECTS, true );
        context.put( CreateProjectsFromMetadataAction.KEY_CHECKOUT_PROJECTS_IN_SINGLE_DIRECTORY, false );

        action.execute( context );

        ContinuumProjectBuildingResult result =
            (ContinuumProjectBuildingResult) context.get( CreateProjectsFromMetadataAction.KEY_PROJECT_BUILDING_RESULT )
            ;

        assertFalse(
            "Should not have errors but had " + result.getErrorsAsString() + " (this test requires internet access)",
            result.hasErrors() );
    }

    public void testExecuteWithRecursiveMode()
        throws Exception
    {        
        invokeBuildSettings();
        
        Map<String, Object> context = new HashMap<String, Object>();
        context.put( AbstractContinuumAction.KEY_URL,
                     "http://svn.apache.org/repos/asf/maven/archiva/trunk/pom.xml" );
        context.put( CreateProjectsFromMetadataAction.KEY_PROJECT_BUILDER_ID, "id" );
        context.put( CreateProjectsFromMetadataAction.KEY_LOAD_RECURSIVE_PROJECTS, false );
        context.put( CreateProjectsFromMetadataAction.KEY_CHECKOUT_PROJECTS_IN_SINGLE_DIRECTORY, false );

        action.execute( context );

        ContinuumProjectBuildingResult result =
            (ContinuumProjectBuildingResult) context.get( CreateProjectsFromMetadataAction.KEY_PROJECT_BUILDING_RESULT )
            ;

        assertFalse(
            "Should not have errors but had " + result.getErrorsAsString() + " (this test requires internet access)",
            result.hasErrors() );
    }
    
    // TODO: deng --> should this work with non-recursive mode?    
    public void testExecuteWithCheckoutProjectsInSingleDirectory()
        throws Exception
    {   
        Project project = new Project();
        project.setGroupId( "org.apache.continuum" );
        project.setArtifactId( "parent-project" );
        project.setVersion( "1.0-SNAPSHOT" );
        project.setId( 6 );
        project.setName( "parent-project" );
        project.setScmUrl( "scm:local:src/test-projects:flat-multi-module/parent-project" );
        
        this.result.addProject( project );
        
        project = new Project();
        project.setGroupId( "org.apache.continuum" );
        project.setArtifactId( "module-a" );
        project.setVersion( "1.0-SNAPSHOT" );
        project.setId( 7 );
        project.setName( "module-a" );
        project.setScmUrl( "scm:local:src/test-projects:flat-multi-module/module-a" );
        
        this.result.addProject( project );
        
        project = new Project();
        project.setGroupId( "org.apache.continuum" );
        project.setArtifactId( "module-b" );
        project.setVersion( "1.0-SNAPSHOT" );
        project.setId( 8 );
        project.setName( "module-b" );
        project.setScmUrl( "scm:local:src/test-projects:flat-multi-module/module-b" );
        
        this.result.addProject( project );
                        
        // assert using scm url set in root!
        Map<String, Object> context = new HashMap<String, Object>();
        context.put( AbstractContinuumAction.KEY_URL,
                     "file://" + PlexusInSpringTestCase.getBasedir() + "/src/test-projects/flat-multi-module/parent-project/pom.xml" );
        context.put( CreateProjectsFromMetadataAction.KEY_PROJECT_BUILDER_ID, "id" );
        context.put( CreateProjectsFromMetadataAction.KEY_LOAD_RECURSIVE_PROJECTS, true );
        context.put( CreateProjectsFromMetadataAction.KEY_CHECKOUT_PROJECTS_IN_SINGLE_DIRECTORY, true );

        action.execute( context );

        ContinuumProjectBuildingResult result =
            (ContinuumProjectBuildingResult) context.get( CreateProjectsFromMetadataAction.KEY_PROJECT_BUILDING_RESULT )
            ;

        assertFalse(
            "Should not have errors but had " + result.getErrorsAsString() + " (this test requires internet access)",
            result.hasErrors() );
        assertEquals( "Incorrect SCM Root Url for flat multi-module project.",
                      "scm:local:src/test-projects:flat-multi-module/", context.get( AbstractContinuumAction.KEY_URL ) );
    }
}
