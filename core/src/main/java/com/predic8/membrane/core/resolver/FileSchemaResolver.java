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

import com.predic8.membrane.core.util.functionalInterfaces.*;
import org.slf4j.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.util.URIUtil.*;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileSchemaResolver implements SchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(FileSchemaResolver.class.getName());

    WatchService watchService;
    private final ConcurrentHashMap<String, WatchKey> watchServiceForFile = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExceptionThrowingConsumer<InputStream>> watchedFiles = new ConcurrentHashMap<>();

    private final Runnable fileWatchJob = new Runnable() {
        @Override
        public void run() {
            while (!watchedFiles.isEmpty()) {
                for (String url : watchServiceForFile.keySet()) {
                    WatchKey wk = watchServiceForFile.get(url);
                    List<WatchEvent<?>> events = wk.pollEvents();
                    for (WatchEvent<?> event : events) {
                        Path changedFile = ((Path) event.context());
                        Path urlPath = Paths.get(url).getFileName();
                        if (changedFile.toString().equals(urlPath.toString())) {
                            try {
                                ExceptionThrowingConsumer<InputStream> inputStreamConsumer = watchedFiles.get(url);
                                watchServiceForFile.remove(url);
                                watchedFiles.remove(url);
                                inputStreamConsumer.accept(resolve(url));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                try {
                    //noinspection BusyWait
                    Thread.sleep(1000); // fileWatchIntervalInSeconds
                } catch (InterruptedException ignored) {
                }
            }
            fileWatcher = null;
        }
    };
    Thread fileWatcher = null;

    @Override
    public List<String> getSchemas() {
        var l = new ArrayList<String>();
        l.add("file");
        l.add(null); // List.of() does not work with null
        return l;
    }

    /**
     * @param fileUrl URL pointing to a file e.g. file:///users/viktor/foo
     * @return InputStream for file
     */
    public InputStream resolve(String fileUrl) throws ResourceRetrievalException {
        String path = pathFromFileURI(fileUrl);
        log.debug("Resolving {}", path);
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            log.error("File {} not found: {}",path, e.getMessage());
            throw new ResourceRetrievalException(fileUrl, e);
        }
    }

    @Override
    public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) {
        url = Paths.get(pathFromFileURI(url)).toAbsolutePath().toString();
        if (watchService == null) {
            try {
                watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException ignored) {
            }
        }
        Path path = Paths.get(url).getParent();
        WatchKey watchKey = null;
        try {
            watchKey = path.register(watchService, ENTRY_MODIFY);
        } catch (IOException ignored) {
        }
        watchServiceForFile.put(url, watchKey);
        watchedFiles.put(url, consumer);

        if (fileWatcher == null) {
            fileWatcher = new Thread(fileWatchJob);
        }
        if (!fileWatcher.isAlive()) {
            fileWatcher.start();
        }
    }

    @Override
    public List<String> getChildren(String url) {
        String[] children = new File(pathFromFileURI(url)).list();
        if (children == null)
            return null;

        return Arrays.asList(children);
    }

    @Override
    public long getTimestamp(String url) {
        return new File(pathFromFileURI(url)).lastModified();
    }
}
