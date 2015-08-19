/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.osgi.extender;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.osgi.service.log.LogService;

/**
 * Adapts Membrane's log4j to the logging of the OSGi container.
 */
public class OsgiAppender extends AppenderSkeleton {

	private static LogService logService;

	public static synchronized void setLogService(LogService logService) {
		OsgiAppender.logService = logService;
	}

	@Override
	public void close() {
	}

	@Override
	public boolean requiresLayout() {
		return false;
	}

	@Override
	protected synchronized void append(LoggingEvent event) {
		if (logService == null)
			return;

		int osgiLogLevel;
		Level level = event.getLevel();
		if (level == null || level.toInt() < Level.DEBUG_INT) {
			osgiLogLevel = LogService.LOG_DEBUG;
		} else if (level.toInt() <= Level.INFO_INT) {
			osgiLogLevel = LogService.LOG_INFO;
		} else if (level.toInt() <= Level.WARN_INT) {
			osgiLogLevel = LogService.LOG_WARNING;
		} else {
			osgiLogLevel = LogService.LOG_ERROR;
		}

		String renderedMessage = event.getRenderedMessage();
		if (renderedMessage == null)
			renderedMessage = "";


		ThrowableInformation throwableInformation = event.getThrowableInformation();
		if (throwableInformation != null) {
			Throwable throwable = throwableInformation.getThrowable();
			if (throwable != null) {
				logService.log(osgiLogLevel, renderedMessage, throwable);
				return;
			}
		}

		logService.log(osgiLogLevel, renderedMessage);
	}

}
