package org.apache.continuum.web.test.parent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import org.testng.Assert;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

public abstract class AbstractReleaseTest
    extends AbstractContinuumTest
{
    public void releasePrepareProject( String username, String password, String tagBase, String tag, String releaseVersion, String developmentVersion, boolean success )
        throws Exception
    {
        goToReleasePreparePage();
        setFieldValue( "scmUsername", username );
        setFieldValue( "scmPassword", password );
        setFieldValue( "scmTag", tag );
        setFieldValue( "scmTagBase", tagBase );
        setFieldValue( "prepareGoals", "clean" );
        setFieldValue( "relVersions", releaseVersion );
        setFieldValue( "devVersions", developmentVersion );
        submit();

        assertRelease( success );
    }

    public void releasePerformProjectWithProvideParameters( String username, String password, String tagBase, String tag, String scmUrl, boolean success )
        throws Exception
    {
        goToReleasePerformProvideParametersPage();
        setFieldValue( "scmUrl", scmUrl );
        setFieldValue( "scmUsername", username );
        setFieldValue( "scmPassword", password );
        setFieldValue( "scmTag", tag );
        setFieldValue( "scmTagBase", tagBase );
        setFieldValue( "goals", "clean deploy" );
        submit();
    
        assertRelease( success );
    }

    public void goToReleasePreparePage()
    {
        clickLinkWithLocator( "goal", false );
        submit();
        assertReleasePreparePage();
    }

    public void goToReleasePerformProvideParametersPage()
    {
        clickLinkWithLocator( "//input[@name='goal' and @value='perform']", false );
        submit();
        assertReleasePerformProvideParametersPage();
    }

    public void assertReleasePreparePage()
    {
        assertPage( "Continuum - Release Project" );
        assertTextPresent( "Prepare Project for Release" );
        assertTextPresent( "Release Prepare Parameters" );
        assertTextPresent( "SCM Username" );
        assertTextPresent( "SCM Password" );
        assertTextPresent( "SCM Tag" );
        assertTextPresent( "SCM Tag Base" );
        assertTextPresent( "SCM Comment Prefix" );
        assertTextPresent( "Preparation Goals" );
        assertTextPresent( "Arguments" );
        assertTextPresent( "Build Environment" );
        assertTextPresent( "Release Version" );
        assertTextPresent( "Next Development Version" );
        assertButtonWithValuePresent( "Submit" );
    }

    public void assertReleasePerformProvideParametersPage()
    {
        assertPage( "Continuum - Perform Project Release" );
        assertTextPresent( "Perform Project Release" );
        assertTextPresent( "Release Perform Parameters" );
        assertTextPresent( "SCM Connection URL" );
        assertTextPresent( "SCM Username" );
        assertTextPresent( "SCM Password" );
        assertTextPresent( "SCM Tag" );
        assertTextPresent( "SCM Tag Base" );
        assertTextPresent( "Perform Goals" );
        assertTextPresent( "Arguments" );
        assertTextPresent( "Build Environment" );
        assertButtonWithValuePresent( "Submit" );
    }

    public void assertRelease( boolean success )
        throws Exception
    {
        // condition for release is complete; "Done" button or "Release Error" in page is present
        String condition = "( selenium.browserbot.getCurrentWindow().document.getElementById( 'releaseCleanup_0' ) != null || " +
                           "selenium.browserbot.getCurrentWindow().document.body.innerHTML.search( 'Release Error' ) > 0 )";

        waitForCondition( condition );

        assertButtonWithValuePresent( "Rollback changes" );
    
        if ( success )
        {
            assertImgWithAltNotPresent( "Error" );
        }
        else
        {
            assertImgWithAlt( "Error" );
        }
    }

    public void assertPreparedReleasesFileCreated()
        throws Exception
    {
        File file = new File( "target/conf/prepared-releases.xml" );
        Assert.assertTrue( file.exists(), "prepared-releases.xml was not created" );

        FileInputStream fis = new FileInputStream( file );
        BufferedReader reader = new BufferedReader( new InputStreamReader( fis ) );

        String BUILD_AGENT_URL = getProperty( "BUILD_AGENT_NAME2" );
        String strLine;
        StringBuffer str = new StringBuffer();
        while( ( strLine = reader.readLine() ) != null )
        {
            str.append( strLine );
        }

        Assert.assertTrue( str.toString().contains( "<buildAgentUrl>" + BUILD_AGENT_URL + "</buildAgentUrl>" ), "prepared-releases.xml was not populated" );
    }
}
