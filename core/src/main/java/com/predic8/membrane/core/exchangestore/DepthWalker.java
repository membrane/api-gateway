/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.exchangestore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.DirectoryWalker;

class DepthWalker extends DirectoryWalker<File> {

	final int depth;

	public DepthWalker(int depth) {
		super();
		this.depth = depth;
	}

	public ArrayList<File> getDirectories(File startDirectory) throws IOException {
		ArrayList<File> dirs = new ArrayList<>();
		walk(startDirectory, dirs);
		return dirs;
	}

	@Override
	public boolean handleDirectory(File directory, int depth, Collection<File> results) {
		if (depth == this.depth)
			results.add(directory);
		return true;
	}
}
