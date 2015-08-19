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

package com.predic8.membrane.core.util;

import org.apache.commons.logging.*;

public class Timer {
	private static Log log = LogFactory.getLog(Timer.class.getName());

	static private long time;

	static public void reset() {
		time = System.currentTimeMillis();
	}

	static public void log(String txt) {
		long now = System.currentTimeMillis();
		log.debug(txt+ ":" + (now-time));
		time = System.currentTimeMillis();
	}
}
