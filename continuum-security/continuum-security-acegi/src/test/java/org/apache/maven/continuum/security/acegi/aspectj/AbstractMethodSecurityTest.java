package org.apache.maven.continuum.security.acegi.aspectj;

/*
 * Copyright 2006 The Apache Software Foundation.
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

import org.acegisecurity.AccessDecisionManager;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.TestingAuthenticationToken;
import org.apache.maven.continuum.Continuum;
import org.apache.maven.continuum.model.project.Project;
import org.codehaus.plexus.PlexusTestCase;

/**
 * Test for {@link ContinuumSecurityAspect}, method level security
 * 
 * @author <a href="mailto:carlos@apache.org">Carlos Sanchez</a>
 * @version $Id$
 */
public abstract class AbstractMethodSecurityTest
    extends AbstractContinuumSecurityAspectTest
{

    /**
     * Check that method call proceeds when user has the required role.
     * 
     * @throws Exception
     */
    public void testMethodSecurity()
        throws Exception
    {
        assertNotNull( "continuum property is not set", getContinuum() );
        assertNotNull( "securityInterceptor property in aspect is not set", getAspect().getSecurityInterceptor() );

        SecurityContextHolder.getContext().setAuthentication( getAuthentication( "ADMIN" ) );

        getContinuum().addProject( new Project(), "" );
    }

    /**
     * Check that method call doesn't proceed when user doesn't have the required role.
     * 
     * @throws Exception
     */
    public void testMethodSecurityWithWrongRole()
        throws Exception
    {
        assertNotNull( "continuum property is not set", getContinuum() );
        assertNotNull( "securityInterceptor property in aspect is not set", getAspect().getSecurityInterceptor() );

        SecurityContextHolder.getContext().setAuthentication( getAuthentication( "USER" ) );

        try
        {
            getContinuum().addProject( new Project(), "" );
            fail( AuthenticationException.class.getName() + " was not thrown." );
        }
        catch ( AccessDeniedException e )
        {
            // expected
        }
    }

    /**
     * Check that method call proceeds when user doesn't have the required role
     * but security interceptor is not defined.
     * 
     * @throws Exception
     */
    public void testMethodSecurityWithoutSecurityInterceptor()
        throws Exception
    {
        assertNotNull( "continuum property is not set", getContinuum() );

        SecurityContextHolder.getContext().setAuthentication( getAuthentication( "USER" ) );
        getAspect().setSecurityInterceptor( null );

        getContinuum().addProject( new Project(), "" );
    }
}
