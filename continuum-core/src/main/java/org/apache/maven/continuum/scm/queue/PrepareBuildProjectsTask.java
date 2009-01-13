package org.apache.maven.continuum.scm.queue;

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

import org.codehaus.plexus.taskqueue.Task;

/**
 * @author 
 * @version $Id$
 */
public class PrepareBuildProjectsTask
    implements Task
{
    private Map<Integer, Integer> projectsBuildDefinitionsMap;

    private int trigger;

    private int projectGroupId;

    private String scmRootAddress;

    public PrepareBuildProjectsTask( Map<Integer, Integer> projectsBuildDefinitionsMap, int trigger, 
                                     int projectGroupId, String scmRootAddress )
    {
        this.projectsBuildDefinitionsMap = projectsBuildDefinitionsMap;
        this.trigger = trigger;
        this.projectGroupId = projectGroupId;
        this.scmRootAddress = scmRootAddress;
    }
    
    public long getMaxExecutionTime()
    {
        // TODO Auto-generated method stub
        return 0;
    }
    
    public Map<Integer, Integer> getProjectsBuildDefinitionsMap()
    {
        return projectsBuildDefinitionsMap;
    }
    
    public void setProjectsBuildDefinitionsMap( Map<Integer, Integer> projectsBuildDefinitionsMap )
    {
        this.projectsBuildDefinitionsMap = projectsBuildDefinitionsMap;
    }

    public int getTrigger()
    {
        return trigger;
    }

    public void setTrigger( int trigger )
    {
        this.trigger = trigger;
    }

    public int getProjectGroupId()
    {
        return projectGroupId;
    }

    public void setProjectGroupId( int projectGroupId )
    {
        this.projectGroupId = projectGroupId;
    }

    public String getScmRootAddress()
    {
        return scmRootAddress;
    }

    public void setScmRootAddress( String scmRootAddress )
    {
        this.scmRootAddress = scmRootAddress;
    }

    public int getHashCode()
    {
        return this.hashCode();
    }    
}
