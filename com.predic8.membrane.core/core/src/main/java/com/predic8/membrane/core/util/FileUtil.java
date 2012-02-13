package com.predic8.membrane.core.util;

import java.io.File;

public class FileUtil {
	public static File prefixMembraneHomeIfNeeded(File f) {
		if ( f.isAbsolute() )
			return f;
		
		return new File(System.getenv("MEMBRANE_HOME"), f.getPath());
		
	}
}
