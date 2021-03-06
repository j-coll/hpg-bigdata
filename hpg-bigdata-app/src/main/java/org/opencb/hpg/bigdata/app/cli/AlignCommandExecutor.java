/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.hpg.bigdata.app.cli;

/**
 * Created by imedina on 16/03/15.
 */
public class AlignCommandExecutor extends CommandExecutor {

    private CliOptionsParser.AlignCommandOptions alignCommandOptions;

    public AlignCommandExecutor(CliOptionsParser.AlignCommandOptions alignCommandOptions) {
        super(alignCommandOptions.commonOptions.logLevel, alignCommandOptions.commonOptions.verbose,
                alignCommandOptions.commonOptions.conf);

        this.alignCommandOptions = alignCommandOptions;
    }


    /**
     * Parse specific 'align' command options
     */
    public void execute() {
        logger.info("Executing {} CLI options", "align");

        logger.debug("Input file: {}", alignCommandOptions.input);
    }

}
