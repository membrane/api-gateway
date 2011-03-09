/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class ClassloaderUtil {

	public static List<URL> getJarUrls(String folder) {
		
		if (folder.startsWith("file:"))
			folder = folder.substring(5);

		File file = new File(folder);
		if (!file.isDirectory())
			return new ArrayList<URL>();

		String[] jars = file.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}
		});

		List<URL> urls = new ArrayList<URL>();
		
		for (int i = 0; i < jars.length; i++) {
			try {
				urls.add(new URL("file:" + folder + jars[i]));
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}

		return urls;

	}

	public static boolean fileExists(String configFileName) throws MalformedURLException, IOException {
		if (configFileName.startsWith("file:"))
			return new File(new URL(configFileName).getPath()).exists();
		return new File(configFileName).exists();
	}

	public static String getFullQualifiedFolderName(String rootDir, String folder) throws IOException {
		return rootDir + System.getProperty("file.separator") + folder + System.getProperty("file.separator");
	}

	public static URLClassLoader getExternalClassloader(String rootDir) {
		try {
			List<URL> urls = getJarUrls(getFullQualifiedFolderName(rootDir, "lib"));
			urls.add(new URL(getFullQualifiedFolderName(rootDir, "classes")));
			return new URLClassLoader( urls.toArray(new URL[urls.size()]) , ClassloaderUtil.class.getClassLoader() );
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		} 
		return null;
	}

}
