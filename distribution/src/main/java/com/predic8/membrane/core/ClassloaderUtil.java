/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import java.io.*;
import java.net.*;
import java.util.*;

public class ClassloaderUtil {

	public static List<URL> getJarUrls(String folder) {

		if (folder.startsWith("file:"))
			folder = folder.substring(5);

		File file = new File(folder);
		if (!file.isDirectory())
			return new ArrayList<>();

		List<URL> urls = new ArrayList<>();
		for (String jar : getJarFilenames(file)) {
			try {
				urls.add(new URL("file:" + folder + jar));
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		return urls;
	}

	private static String[] getJarFilenames(File file) {
		return file.list((dir, name) -> name.endsWith(".jar"));
	}

	public static String getFullQualifiedFolderName(String rootDir, String folder) {
		return rootDir + System.getProperty("file.separator") + folder + System.getProperty("file.separator");
	}

	public static URLClassLoader getExternalClassloader(String rootDir) {
		try {
			List<URL> urls = getJarUrls(getFullQualifiedFolderName(rootDir, "lib"));
			urls.add(new URL(getFullQualifiedFolderName(rootDir, "classes")));
			return new URLClassLoader(urls.toArray(new URL[0]), ClassloaderUtil.class.getClassLoader());
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}
}
