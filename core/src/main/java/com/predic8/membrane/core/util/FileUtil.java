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

import org.apache.commons.io.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;

import static java.util.Objects.*;
import static java.util.stream.Collectors.*;

public class FileUtil {

	public static final String XML = "xml";
	public static final String JSON = "json";

	public static String readInputStream(InputStream is) {
		return new BufferedReader(new InputStreamReader(is)).lines().collect(joining("\n"));
	}

	public static void writeInputStreamToFile(String filepath, InputStream is) throws IOException {
		requireNonNull(is);
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(filepath))) {
			byte[] buffer = new byte[1024];
			int len;
			while ((len = is.read(buffer)) > 0) {
				os.write(buffer, 0, len);
				os.flush();
			}
		}
	}

	public static boolean isXml(String location) {
		if (location == null)
			return false;
		return XML.equalsIgnoreCase(FilenameUtils.getExtension(location));
	}

	public static boolean isJson(String location) {
		if (location == null)
			return false;
		return JSON.equalsIgnoreCase(FilenameUtils.getExtension(location));
	}

	/**
	 * Checks if string starts / or \
	 * @param filepath
	 * @return boolean true if path starts with / or \
	 */
	public static boolean startsWithSlash(String filepath) {
		return filepath.startsWith("\\") || filepath.startsWith("/");
	}

	public static String toFileURIString(File f) throws URISyntaxException {
		return URIUtil.convertPath2FileURI(f.getAbsolutePath()).toString();
	}

	public static boolean endsWithSlash(String filepath) {
		return filepath.endsWith("/") || filepath.endsWith("\\");
	}

	/**
	 * Resolves the absolute URI string of a file given a parent directory
	 * and a relative child path. If the relative child path ends with a slash,
	 * the returned URI string will also end with a slash.
	 *
	 * @param parent the parent directory as a {@link File} object
	 * @param relativeChild the relative child path as a {@link String}
	 * @return the resolved absolute URI string of the file
	 * @throws URISyntaxException if an error occurs while converting the file path to a URI string
	 */
	public static @NotNull String resolve(File parent, String relativeChild) throws URISyntaxException {
		var res = toFileURIString(new File(parent, relativeChild));
		if (endsWithSlash(relativeChild))
			return res + "/";
		return res;
	}

	/**
	 * Retrieves the filepath directory of the given file path.
	 * foo/ => foo/
	 * foo/bar.txt => foo/
	 *
	 * @param filepath the file path as a string
	 * @return a {@link File} object representing the filepath directory or the file itself if the path ends with a slash
	 */
	public static File getDirectoryPart(String filepath) {
		var parentFile = new File(filepath);
		if (!endsWithSlash(filepath))
			return parentFile.getParentFile();
		return parentFile;
	}
}
