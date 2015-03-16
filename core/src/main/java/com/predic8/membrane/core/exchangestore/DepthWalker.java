package com.predic8.membrane.core.exchangestore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.DirectoryWalker;

class DepthWalker extends DirectoryWalker<File> {

	int depth;

	public DepthWalker(int depth) {
		super();
		this.depth = depth;
	}

	public ArrayList<File> getDirectories(File startDirectory) throws IOException {
		ArrayList<File> dirs = new ArrayList<File>();
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