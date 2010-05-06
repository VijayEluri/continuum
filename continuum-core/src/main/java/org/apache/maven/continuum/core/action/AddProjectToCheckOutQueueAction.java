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

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.continuum.buildmanager.BuildsManager;
import org.apache.continuum.dao.ProjectDao;
import org.apache.maven.continuum.model.project.BuildDefinition;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.utils.WorkingDirectoryService;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 * @plexus.component role="org.codehaus.plexus.action.Action"
 * role-hint="add-project-to-checkout-queue"
 */
public class AddProjectToCheckOutQueueAction
    extends AbstractContinuumAction
{
    /**
     * @plexus.requirement
     */
    private WorkingDirectoryService workingDirectoryService;

    /**
     * @plexus.requirement
     */
    private ProjectDao projectDao;

    /**
     * @plexus.requirement role-hint="parallel"
     */
    private BuildsManager parallelBuildsManager;

    @SuppressWarnings("unchecked")
    public void execute( Map context )
        throws Exception
    {
        Project project = getProject( context, null );
        if ( project == null )
        {
            project = projectDao.getProject( getProjectId( context ) );
        }

        String scmUsername = project.getScmUsername();
        String scmPassword = project.getScmPassword();
        
        if( scmUsername == null || StringUtils.isEmpty( scmUsername ) )
        {
            scmUsername = CheckoutProjectContinuumAction.getScmUsername( context, null );
        }
        
        if( scmPassword == null || StringUtils.isEmpty( scmPassword ) )
        {
            scmPassword = CheckoutProjectContinuumAction.getScmPassword( context, null );
        }
        
        BuildDefinition defaultBuildDefinition = getBuildDefinition( context );
        parallelBuildsManager.checkoutProject( project.getId(), project.getName(),
                                               workingDirectoryService.getWorkingDirectory( project ),
                                               scmUsername, scmPassword, defaultBuildDefinition );
    }
}
