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

package com.predic8.membrane.osgi.logger;

import org.apache.commons.logging.*;
import org.eclipse.core.runtime.*;

public class MembraneLogListener implements ILogListener {

	private static Log log = LogFactory.getLog(MembraneLogListener.class.getName());
	
	@Override
	public void logging(IStatus status, String plugin) {
		
		switch (status.getCode()) {
		
		case IStatus.INFO:
			log.info(status.getMessage());
			break;
			
		case IStatus.WARNING:
			log.warn(status.getMessage());
			break;
			
		case IStatus.ERROR:
			log.error(status.getMessage());
			break;
			
		default:
				log.debug(status.getMessage());	
		}
		
	}

}
