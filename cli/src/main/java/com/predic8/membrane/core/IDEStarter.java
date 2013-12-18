/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core;

import org.apache.log4j.PropertyConfigurator;

import java.io.File;

public class IDEStarter {
	public static void main(String[] args) {
        // TODO for testing purposes - do not commit
		PropertyConfigurator.configure("conf/log4j.properties");
		RouterCLI.main(args);
	}

}
