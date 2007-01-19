package org.apache.maven.continuum.store;

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

import org.apache.maven.continuum.model.project.BuildDefinition;
import org.apache.maven.continuum.model.project.BuildResult;
import org.apache.maven.continuum.model.project.Profile;
import org.apache.maven.continuum.model.project.Project;
import org.apache.maven.continuum.model.project.ProjectGroup;
import org.apache.maven.continuum.model.project.ProjectNotifier;
import org.apache.maven.continuum.model.project.Schedule;
import org.apache.maven.continuum.model.system.Installation;
import org.apache.maven.continuum.model.system.SystemConfiguration;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 * @todo remove old stuff
 */
public interface ContinuumStore
{
    String ROLE = ContinuumStore.class.getName();

    void removeNotifier( ProjectNotifier notifier )
        throws ContinuumStoreException;

    ProjectNotifier storeNotifier( ProjectNotifier notifier )
        throws ContinuumStoreException;

    Map getDefaultBuildDefinitions();

    /**
     * returns the default build definition of the project, if the project doesn't have on declared the default
     * of the project group will be returned
     * <p/>
     * this should be the most common usage of the default build definition accessing methods
     *
     * @param projectId
     * @return
     * @throws ContinuumStoreException
     * @throws ContinuumObjectNotFoundException
     *
     */
    BuildDefinition getDefaultBuildDefinition( long projectId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    /**
     * returns the default build definition for the project without consulting the project group
     *
     * @param projectId
     * @return
     * @throws ContinuumStoreException
     * @throws ContinuumObjectNotFoundException
     *
     */
    BuildDefinition getDefaultBuildDefinitionForProject( long projectId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    /**
     * returns the default build definition for the project group and there should always be one declared
     *
     * @param projectGroupId
     * @return
     * @throws ContinuumStoreException
     * @throws ContinuumObjectNotFoundException
     *
     */
    BuildDefinition getDefaultBuildDefinitionForProjectGroup( long projectGroupId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    BuildDefinition getBuildDefinition( long buildDefinitionId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    void removeBuildDefinition( BuildDefinition buildDefinition )
        throws ContinuumStoreException;

    BuildDefinition storeBuildDefinition( BuildDefinition buildDefinition )
        throws ContinuumStoreException;

    ProjectGroup addProjectGroup( ProjectGroup group );

    ProjectGroup getProjectGroup( long projectGroupId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    public ProjectGroup getProjectGroupByProjectId( long projectId )
        throws ContinuumObjectNotFoundException;

    void updateProjectGroup( ProjectGroup group )
        throws ContinuumStoreException;

    Collection getAllProjectGroupsWithProjects();

    Collection getAllProjectGroups();

    List getAllProjectsByName();

    List getAllProjectsByNameWithDependencies();

    public List getProjectsWithDependenciesByGroupId( long projectGroupId );

    List getAllProjectsByNameWithBuildDetails();

    List getAllSchedulesByName();

    Schedule addSchedule( Schedule schedule );

    Schedule getScheduleByName( String name )
        throws ContinuumStoreException;

    Schedule storeSchedule( Schedule schedule )
        throws ContinuumStoreException;

    List getAllProfilesByName();

    Profile addProfile( Profile profile );

    Installation addInstallation( Installation installation );

    List getAllInstallations();

    List getAllBuildsForAProjectByDate( long projectId );

    Project getProject( long projectId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    Project getProject( String groupId, String artifactId, String version )
        throws ContinuumStoreException;

    Project getProjectByName( String name )
        throws ContinuumStoreException;

    Map getProjectIdsAndBuildDefinitionsIdsBySchedule( long scheduleId )
        throws ContinuumStoreException;

    Map getProjectGroupIdsAndBuildDefinitionsIdsBySchedule( long scheduleId )
        throws ContinuumStoreException;

    public Map getAggregatedProjectIdsAndBuildDefinitionIdsBySchedule( long scheduleId )
        throws ContinuumStoreException;

    void updateProject( Project project )
        throws ContinuumStoreException;

    void updateProfile( Profile profile )
        throws ContinuumStoreException;

    void updateSchedule( Schedule schedule )
        throws ContinuumStoreException;

    Project getProjectWithBuilds( long projectId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    void removeProfile( Profile profile );

    void removeSchedule( Schedule schedule );

    Project getProjectWithCheckoutResult( long projectId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    BuildResult getBuildResult( long buildId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    void removeProject( Project project );

    void removeProjectGroup( ProjectGroup projectGroup );

    ProjectGroup getProjectGroupWithBuildDetails( long projectGroupId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    List getProjectsInGroup( long projectGroupId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    ProjectGroup getProjectGroupWithProjects( long projectGroupId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    List getAllProjectGroupsWithBuildDetails();

    List getAllProjectsWithAllDetails();

    Project getProjectWithAllDetails( long projectId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    Schedule getSchedule( long scheduleId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    Profile getProfile( long profileId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    ProjectGroup getProjectGroupByGroupId( String groupId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    ProjectGroup getProjectGroupByGroupIdWithBuildDetails( String groupId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    ProjectGroup getProjectGroupByGroupIdWithProjects( String groupId )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    BuildResult getLatestBuildResultForProject( long projectId );

    List getBuildResultsInSuccessForProject( long projectId, long fromDate );

    List getBuildResultsForProject( long projectId, long fromDate );

    Map getLatestBuildResults();

    List getBuildResultByBuildNumber( long projectId, long buildNumber );

    Map getBuildResultsInSuccess();

    void addBuildResult( Project project, BuildResult build )
        throws ContinuumStoreException, ContinuumObjectNotFoundException;

    void updateBuildResult( BuildResult build )
        throws ContinuumStoreException;

    Project getProjectWithBuildDetails( long projectId )
        throws ContinuumObjectNotFoundException, ContinuumStoreException;

    SystemConfiguration addSystemConfiguration( SystemConfiguration systemConf );

    void updateSystemConfiguration( SystemConfiguration systemConf )
        throws ContinuumStoreException;

    SystemConfiguration getSystemConfiguration()
        throws ContinuumStoreException;

    void closeStore();

    Collection getAllProjectGroupsWithTheLot();

    void eraseDatabase();
}
