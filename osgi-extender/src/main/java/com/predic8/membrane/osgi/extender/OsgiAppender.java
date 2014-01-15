package com.predic8.membrane.osgi.extender;


import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.osgi.service.log.LogService;

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
