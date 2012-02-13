package com.predic8.membrane.core.logger;

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
