package org.apache.continuum.buildagent.build.execution;

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

import java.io.File;

public class ContinuumAgentBuildExecutionResult
{
    private final File output;

    private final int exitCode;

    public ContinuumAgentBuildExecutionResult( File output, int exitCode )
    {
        this.output = output;

        this.exitCode = exitCode;
    }

    public File getOutput()
    {
        return output;
    }

    public int getExitCode()
    {
        return exitCode;
    }
}
