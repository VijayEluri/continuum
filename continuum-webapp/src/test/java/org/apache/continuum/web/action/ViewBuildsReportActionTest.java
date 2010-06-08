package org.apache.continuum.web.action;

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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.continuum.web.action.stub.ViewBuildsReportActionStub;
import org.apache.maven.continuum.model.project.BuildResult;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.model.project.ProjectGroup;
import org.apache.maven.continuum.Continuum;
import org.jmock.Mock;

import com.opensymphony.xwork2.Action;

public class ViewBuildsReportActionTest
    extends AbstractActionTest
{
    private ViewBuildsReportActionStub action;

    private Mock continuum;

    private List<BuildResult> buildResults = new ArrayList<BuildResult>();

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        action = new ViewBuildsReportActionStub();
        continuum = mock( Continuum.class );
        action.setContinuum( (Continuum) continuum.proxy() );
    }

    public void testInvalidRowCount()
    {
        action.setRowCount( -1 );
        String result = action.execute();

        assertEquals( Action.INPUT, result );
        assertTrue( action.hasFieldErrors() );
        assertFalse( action.hasActionErrors() );
        continuum.verify();
    }

    public void testEndDateBeforeStartDate()
    {
        action.setStartDate( "04/25/2010" );
        action.setEndDate( "04/24/2010" );
        String result = action.execute();

        assertEquals( Action.INPUT, result );
        assertTrue( action.hasFieldErrors() );
        assertFalse( action.hasActionErrors() );
        continuum.verify();
    }

    public void testMalformedStartDate()
    {
        action.setStartDate( "not a date" );
        String result = action.execute();

        assertEquals( Action.ERROR, result );
        assertTrue( action.hasActionErrors() );
        assertFalse( action.hasFieldErrors() );
        continuum.verify();
    }

    public void testMalformedEndDate()
    {
        action.setEndDate( "not a date" );
        String result = action.execute();

        assertEquals( Action.ERROR, result );
        assertTrue( action.hasActionErrors() );
        assertFalse( action.hasFieldErrors() );
        continuum.verify();
    }

    public void testStartDateSameWithEndDate()
    {
        continuum.expects( once() ).method( "getBuildResultsInRange" ).will( returnValue( buildResults ) );
        
        action.setStartDate( "04/25/2010" );
        action.setEndDate( "04/25/2010" );
        String result = action.execute();

        assertSuccessResult( result );
        continuum.verify();
    }

    public void testEndDateWithNoStartDate()
    {
        continuum.expects( once() ).method( "getBuildResultsInRange" ).will( returnValue( buildResults ) );
        action.setEndDate( "04/25/2010" );
        String result = action.execute();

        assertSuccessResult( result );
        continuum.verify();
    }

    public void testExportToCsv()
        throws Exception
    {
        Calendar cal = Calendar.getInstance();
        cal.set( 2010, 1, 1, 1, 1, 1 );
        
        List<BuildResult> results = createBuildResult( cal.getTimeInMillis() );
        
        continuum.expects( once() ).method( "getBuildResultsInRange" ).will( returnValue( results ) );
        action.setProjectGroupId( 0 );
        action.setBuildStatus( 0 );
        action.setStartDate( "" );
        action.setEndDate( "" );
        action.setTriggeredBy( "" );
        
        String result = action.downloadBuildsReport();
        
        assertEquals( "send-file", result );
        assertFileContentsEqual( IOUtils.toString( action.getInputStream() ), cal.getTime().toString() );
        continuum.verify();
    }

    private void assertSuccessResult( String result )
    {
        assertEquals( Action.SUCCESS, result );
        assertFalse( action.hasFieldErrors() );
        assertFalse( action.hasActionErrors() );
    }
    
    private List<BuildResult> createBuildResult( long timeInMillis )
    {
        List<BuildResult> results = new ArrayList<BuildResult>();
        
        BuildResult result = new BuildResult();
        
        ProjectGroup group = new ProjectGroup();
        group.setName( "Test Group" );
        
        Project project = new Project();
        project.setName( "Test Project" );
        project.setProjectGroup( group );

        result.setProject( project );
        result.setState( 2 );
        result.setStartTime( timeInMillis );
        result.setUsername( "test-admin" );
        
        results.add( result );
        
        return results;
    }
    
    private void assertFileContentsEqual( String report, String buildDate )
    {
        String result = "Project Group,Project Name,Build Date,Triggered By,Build Status\n" +
                        "Test Group,Test Project," + buildDate + ",test-admin,Ok\n";
        
        assertEquals( report, result );
    }
}
