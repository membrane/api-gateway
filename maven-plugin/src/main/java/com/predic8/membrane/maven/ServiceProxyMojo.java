/* Copyright 2009, 2012, 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.maven;

import com.predic8.membrane.core.Router;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

/**
 * Runs Membrane-SOA service proxy within Maven.
 */
@Mojo(name = "run", requiresProject = true, defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST_COMPILE)
public class ServiceProxyMojo
    extends AbstractMojo {
    /**
     * Location of the proxies.xml file.
     * @required
     */
    @Parameter(property="service-proxy.proxiesXml", defaultValue="src/test/resources/proxies.xml", required=true, readonly=true)
    private String proxiesXml;

    /**
     * Launches Membrane-SOA's service-proxy in a new thread.
     * @throws MojoExecutionException if the proxy fails.
     */
    @Override
    public void execute()
        throws MojoExecutionException {

        validateInput(getLog());

        Router.init(proxiesXml, getClass().getClassLoader());

        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException interrupted) {
                // do nothing.
            }
        }
    }

    /**
     * Validates the Mojo input.
     * @param logger the {@link Logger} instance.
     * @throws MojoExecutionException if the configuration is invalid.
     */
    protected void validateInput(Log logger)
        throws MojoExecutionException {
        String message = null;

        if (! new File(proxiesXml).exists()) {
            message = proxiesXml + " does not exist";
        } else if (! new File(proxiesXml).canRead()) {
            message = proxiesXml + " is not readable";
        }

        if (message != null) {
            logger.error(message);
            throw new MojoExecutionException(message);
        }
    }
}
