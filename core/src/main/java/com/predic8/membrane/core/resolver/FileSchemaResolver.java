/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.resolver;

import com.google.common.collect.Lists;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FileSchemaResolver implements SchemaResolver {

	WatchService watchService;
	ConcurrentHashMap<String,WatchKey> watchServiceForFile = new ConcurrentHashMap<String, WatchKey>();
	ConcurrentHashMap<String,Consumer<InputStream>> watchedFiles = new ConcurrentHashMap<String, Consumer<InputStream>>();
	int fileWatchIntervalInSeconds = 1;
	Runnable fileWatchJob = new Runnable() {
		@Override
		public void run() {
			while(watchedFiles.size() > 0){
				for(String url : watchServiceForFile.keySet()){
					WatchKey wk = watchServiceForFile.get(url);
					List<WatchEvent<?>> events = wk.pollEvents();
					for(WatchEvent<?> event : events){
						Path changedFile = ((Path) event.context());
						Path urlPath = Paths.get(url).getFileName();
						if(changedFile.toString().equals(urlPath.toString())){
							try {
								watchedFiles.get(url).call(resolve(url));
							} catch (ResourceRetrievalException ignored) {
								ignored.printStackTrace();
							}
						}
					}
				}

				try {
					Thread.sleep(fileWatchIntervalInSeconds*1000);
				} catch (InterruptedException ignored) {
				}
			}
			fileWatcher = null;
		}
	};
	Thread fileWatcher = null;



	@Override
	public List<String> getSchemas() {
		return Lists.newArrayList("file", null);
	}

	public InputStream resolve(String url) throws ResourceRetrievalException {
		try {
			return new FileInputStream(new File(normalize(url)));
		} catch (FileNotFoundException e) {
			throw new ResourceRetrievalException(url, e);
		}
	}

	@Override
	public void observeChange(String url, Consumer<InputStream> consumer) throws ResourceRetrievalException {
		url = Paths.get(normalize(url)).toAbsolutePath().toString();
		if(watchService == null){
			try {
				watchService = FileSystems.getDefault().newWatchService();
			} catch (IOException ignored) {
			}
		}
		Path path = Paths.get(url).getParent();
		System.out.println("Watching directory: "+ path.toString());
		WatchKey watchKey = null;
		try {
			watchKey = path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
		} catch (IOException ignored) {
		}
		watchServiceForFile.put(url,watchKey);
		watchedFiles.put(url,consumer);

		if(fileWatcher == null){
			fileWatcher = new Thread(fileWatchJob);
		}
		if(!fileWatcher.isAlive()){
			fileWatcher.start();
		}
	}

	public static String normalize(String uri) {
		if(uri.startsWith("file:///")) {
			if (uri.length() > 9 && uri.charAt(9) == '/')
				uri = uri.charAt(8) + ":\\" + uri.substring(9);
			else
				uri = "/" + uri.substring(8);
		}
		if(uri.startsWith("file://")) {
			if (uri.length() > 8 && uri.charAt(8) == '/')
				uri = uri.charAt(7) + ":\\" + uri.substring(9);
			else
				uri = "/" + uri.substring(7);
		}
		if(uri.startsWith("file:")) {
			uri = uri.substring(5);
		}
		return uri;
	}

	@Override
	public List<String> getChildren(String url) {
		String[] children = new File(normalize(url)).list();
		if (children == null)
			return null;
		ArrayList<String> res = new ArrayList<String>(children.length);
		for (String child : children)
			res.add(child);
		return res;
	}

	@Override
	public long getTimestamp(String url) {
		return new File(normalize(url)).lastModified();
	}
}
