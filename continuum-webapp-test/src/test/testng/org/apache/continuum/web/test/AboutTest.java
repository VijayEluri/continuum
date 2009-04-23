package org.apache.continuum.web.test;

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

import org.apache.continuum.web.test.parent.AbstractContinuumTest;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Based on AboutTest of Wendy Smoak test.
 * 
 * @author José Morales Martínez
 * @version $Id$
 */
@Test( groups = { "about" }, alwaysRun = true )
public class AboutTest
    extends AbstractContinuumTest
{
    @BeforeSuite
    public void initializeContinuum()
        throws Exception
    {
        super.open();
        getSelenium().open( baseUrl );
        String title = getSelenium().getTitle();
        if ( title.equals( "Create Admin User" ) )
        {
            assertCreateAdmin();
            String fullname = p.getProperty( "ADMIN_FULLNAME" );
            String username = p.getProperty( "ADMIN_USERNAME" );
            String mail = p.getProperty( "ADMIN_MAIL" );
            String password = p.getProperty( "ADMIN_PASSWORD" );
            submitAdminData( fullname, mail, password );
            assertLoginPage();
            submitUserData( username, password, false, true );
            assertAutenticatedPage( username );
            assertEditConfigurationPage();
            submit();
            clickLinkWithText( "Logout" );
        }
        super.close();
    }

    @BeforeTest( groups = { "about" } )
    public void open()
        throws Exception
    {
        super.open();
    }

    public void displayAboutPage()
    {
        getSelenium().open( baseUrl + "/about.action" );
        getSelenium().waitForPageToLoad( maxWaitTimeInMs );
        Assert.assertEquals( "Continuum - About", getSelenium().getTitle() );
    }

    @Override
    @AfterTest( groups = { "about" } )
    public void close()
        throws Exception
    {
        super.close();
    }
}