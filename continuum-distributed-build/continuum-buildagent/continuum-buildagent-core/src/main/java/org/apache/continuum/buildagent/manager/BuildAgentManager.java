package org.apache.continuum.buildagent.manager;

import java.util.List;

import org.apache.continuum.buildagent.buildcontext.BuildContext;
import org.apache.maven.continuum.ContinuumException;

public interface BuildAgentManager
{
    String ROLE = BuildAgentManager.class.getName();

    void prepareBuildProjects( List<BuildContext> buildContextList )
        throws ContinuumException;
}