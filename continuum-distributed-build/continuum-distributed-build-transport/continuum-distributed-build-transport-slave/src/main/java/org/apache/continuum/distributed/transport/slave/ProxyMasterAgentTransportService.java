package org.apache.continuum.distributed.transport.slave;

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

import java.net.URL;
import java.util.Map;

import org.apache.continuum.distributed.transport.MasterBuildAgentTransportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.xmlrpc.AuthenticationInfo;
import com.atlassian.xmlrpc.Binder;
import com.atlassian.xmlrpc.BindingException;
import com.atlassian.xmlrpc.DefaultBinder;

/**
 * ProxyMasterAgentTransportService
 */
public class ProxyMasterAgentTransportService
    implements MasterBuildAgentTransportService
{
    private Logger log = LoggerFactory.getLogger( this.getClass() );
    
    MasterBuildAgentTransportService master;
    
    public ProxyMasterAgentTransportService( URL serviceUrl )
        throws Exception
    {
        this( serviceUrl, null, null );
    }

    public ProxyMasterAgentTransportService( URL serviceUrl, String login, String password )
        throws Exception
    {
        Binder binder = new DefaultBinder();
        AuthenticationInfo authnInfo = new AuthenticationInfo( login, password );
        
        try
        {
            master = binder.bind( MasterBuildAgentTransportService.class, serviceUrl, authnInfo );
        }
        catch ( BindingException e )
        {
            log.error( "Can't bind service interface " + MasterBuildAgentTransportService.class.getName() + " to " + serviceUrl.toExternalForm() + " using " + authnInfo.getUsername() + ", " + authnInfo.getPassword(), e );
            throw new Exception( "Can't bind service interface " + MasterBuildAgentTransportService.class.getName() + " to " + serviceUrl.toExternalForm() + " using " + authnInfo.getUsername() + ", " + authnInfo.getPassword(), e);
        }
    }

    public Boolean returnBuildResult( Map buildResult )
        throws Exception
    {
        Boolean result = null;
        
        try
        {
            result = master.returnBuildResult( buildResult );
            log.info( "Returning the build result." );
        }
        catch ( Exception e )
        {
            log.error( "Failed to return the build result.", e );
            throw new Exception( "Failed to return the build result", e);
        }
        
        return result;
    }

    public Boolean returnScmResult( Map scmResult )
        throws Exception
    {
        Boolean result = null;
        
        try
        {
            result = master.returnScmResult( scmResult );
            log.info( "Returning the scm result." );
        }
        catch ( Exception e )
        {
            log.error( "Failed to return the SCM result.", e );
            throw new Exception( "Failed to return the SCM result", e);
        }
        
        return result;
    }
    
    public Boolean ping()
        throws Exception
    {
        Boolean result = null;
        
        try
        {
            result = master.ping();
            log.info( "Ping " + ( result.booleanValue() ? "ok" : "failed" ) );
        }
        catch ( Exception e )
        {
            log.info( "Ping error" );
            throw new Exception( "Ping error", e );
        }
        
        return result;
    }
}
