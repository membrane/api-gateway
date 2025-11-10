/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.util;

import org.slf4j.Logger;

import javax.tools.*;
import java.io.*;
import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * This implementation of JavaFileManager can either proxy the standard JavaFileManager
 * (<code>USE_IN_MEM=false</code>) logging API calls but modifying the existing file system,
 * or direct everything into memory (<code>USE_IN_MEM=true</code>).
 *
 * Not all possible use case are implemented for <code>USE_IN_MEM=true</code>.
 */
public class LoggingInMemoryJavaFileManager implements JavaFileManager {
    /**
     * If true, this class directs file system 'write' requests to an in-memory file system. (This has only been tested
     * for certain use cases.)
     *
     * If false, this class logs file system 'write' requests and directs them to the underlying file system.
     */
    private static final boolean USE_IN_MEM = true;

    private static final Logger log = getLogger(LoggingInMemoryJavaFileManager.class);

    private final JavaFileManager fm;

    private final InMemoryData data = new InMemoryData();

    public LoggingInMemoryJavaFileManager(JavaFileManager fm) {
        this.fm = fm;

        InMemoryURLStreamHandler.activate(data);
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        log.debug("getClassLoader({})", location);
        if (USE_IN_MEM && location == StandardLocation.CLASS_OUTPUT) {
            return new InMemoryClassLoader(data);
        }
        return fm.getClassLoader(location);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        StringBuilder l = new StringBuilder("list(%s, %s, %s, %s) -> ".formatted(location, packageName, kinds, recurse));
        Iterable<JavaFileObject> res = fm.list(location, packageName, kinds, recurse);
        l.append("[");
        for (JavaFileObject re : res) {
            l.append(re).append(",");
        }
        log.debug("{}]", l);
        return res;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        log.debug("inferBinaryName({}, {})", location, file);
        return fm.inferBinaryName(location, file);
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        log.debug("isSameFile({}, {})", a, b);
        return fm.isSameFile(a, b);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        log.debug("handleOption({}, {})", current, remaining);
        return fm.handleOption(current, remaining);
    }

    @Override
    public boolean hasLocation(Location location) {
        log.debug("hasLocation({})", location);
        return fm.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        String l = "getJavaFileForInput(%s, %s, %s) -> ".formatted(location, className, kind);
        JavaFileObject res = fm.getJavaFileForInput(location, className, kind);
        log.debug("{}{}", l, res);
        return res;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        String l = "getJavaFileForOutput(%s, %s, %s, %s) -> ".formatted(location, className, kind, sibling);
        JavaFileObject res = USE_IN_MEM ?
                new InMemoryJavaFileObject(data, className.replace('.', '/') + kind.extension, kind) :
                new LoggingJavaFileObject(fm.getJavaFileForOutput(location, className, kind, sibling));
        log.debug("{}{}", l, res);
        return res;
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        String l = "getFileForInput(%s, %s, %s) -> ".formatted(location, packageName, relativeName);
        FileObject res = fm.getFileForInput(location, packageName, relativeName);
        log.debug("{}{}", l, res);
        return res;
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
        String l = "getFileForOutput(%s, %s, %s, %s) -> ".formatted(location, packageName, relativeName, sibling);
        String path = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        FileObject res = USE_IN_MEM ? new InMemoryFileObject(data, path + relativeName) :
                new LoggingFileObject(fm.getFileForOutput(location, packageName, relativeName, sibling));
        log.debug("{}{}", l, res);
        return res;
    }

    @Override
    public void flush() throws IOException {
        log.debug("flush()");
        fm.flush();
    }

    @Override
    public void close() throws IOException {
        log.debug("close()");
        fm.close();
    }

    @Override
    public int isSupportedOption(String option) {
        log.debug("isSupportedOption({})", option);
        return fm.isSupportedOption(option);
    }

    public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException {
        log.debug("listLocationsForModules({})", location);
        return fm.listLocationsForModules(location);
    }

    @Override
    public String inferModuleName(Location location) throws IOException {
        log.debug("inferModuleName({})", location);
        return fm.inferModuleName(location);
    }

}
