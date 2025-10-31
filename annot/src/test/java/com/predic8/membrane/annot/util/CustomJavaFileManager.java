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

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This implementation of JavaFileManager can either proxy the standard JavaFileManager
 * (<code>USE_IN_MEM=false</code>) logging API calls but modifying the existing file system,
 * or direct everything into memory (<code>USE_IN_MEM=true</code>).
 *
 * Not all possible use case are implemented for <code>USE_IN_MEM=true</code>.
 */
public class CustomJavaFileManager implements JavaFileManager {
    /**
     * If true, this class directs file system 'write' requests to an in-memory file system. (This has only been tested
     * for certain use cases.)
     *
     * If false, this class logs file system 'write' requests and directs them to the underlying file system.
     */
    private static final boolean USE_IN_MEM = true;

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CustomJavaFileManager.class);

    private final JavaFileManager fm;

    public CustomJavaFileManager(JavaFileManager fm) {
        this.fm = fm;
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        log.debug("getClassLoader(" + location + ")");
        return fm.getClassLoader(location);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        StringBuilder l = new StringBuilder("list(" + location + ", " + packageName + ", " + kinds + ", " + recurse + ") -> ");
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
        log.debug("inferBinaryName(" + location + ", " + file + ")");
        return fm.inferBinaryName(location, file);
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        log.debug("isSameFile(" + a + ", " + b + ")");
        return fm.isSameFile(a, b);
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining) {
        log.debug("handleOption(" + current + ", " + remaining + ")");
        return fm.handleOption(current, remaining);
    }

    @Override
    public boolean hasLocation(Location location) {
        log.debug("hasLocation(" + location + ")");
        return fm.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
        String l = ("getJavaFileForInput(" + location + ", " + className + ", " + kind + ") -> ");
        JavaFileObject res = fm.getJavaFileForInput(location, className, kind);
        log.debug("{}{}", l, res);
        return res;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        String l = ("getJavaFileForOutput(" + location + ", " + className + ", " + kind + ", " + sibling + ") -> ");
        JavaFileObject res = USE_IN_MEM ?
                new InMemoryJavaFileObject(location, className.replace('.', '/') + kind.extension, kind) :
                new LoggingJavaFileObject(fm.getJavaFileForOutput(location, className, kind, sibling));
        log.debug("{}{}", l, res);
        return res;
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {
        String l = ("getFileForInput(" + location + ", " + packageName + ", " + relativeName + ") -> ");
        FileObject res = fm.getFileForInput(location, packageName, relativeName);
        log.debug("{}{}", l, res);
        return res;
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
        String l = ("getFileForOutput(" + location + ", " + packageName + ", " + relativeName + ", " + sibling + ") -> ");
        String path = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        FileObject res = USE_IN_MEM ? new InMemoryFileObject(location, path + relativeName) :
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
        log.debug("isSupportedOption(" + option + ")");
        return fm.isSupportedOption(option);
    }

    public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException {
        log.debug("listLocationsForModules(" + location + ")");
        return fm.listLocationsForModules(location);
    }

    @Override
    public String inferModuleName(Location location) throws IOException {
        log.debug("inferModuleName(" + location + ")");
        return fm.inferModuleName(location);
    }

    private Map<URI, byte[]> content = new HashMap<>();

    public class InnerFileObject extends SimpleJavaFileObject {
        public InnerFileObject(URI uri, Kind kind) {
            super(uri, kind);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            byte[] bytes = content.get(toUri());
            if (bytes == null)
                return "";
            return new String(bytes, UTF_8);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return new ByteArrayOutputStream() {
                @Override
                public void flush() throws IOException {
                    super.flush();
                    content.put(toUri(), toByteArray());
                    log.debug("wrote " + toUri() + " : " + new String(toByteArray(), UTF_8));
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    content.put(toUri(), toByteArray());
                    log.debug("wrote " + toUri() + " : " + new String(toByteArray(), UTF_8));
                }
            };
        }
    }

    public class InMemoryFileObject implements FileObject {
        protected final SimpleJavaFileObject inner;
        private final JavaFileManager.Location location;

        public InMemoryFileObject(JavaFileManager.Location location, String path) {
            inner = new InnerFileObject(URI.create("string:///" + path), JavaFileObject.Kind.OTHER);
            this.location = location;
        }

        public InMemoryFileObject(JavaFileManager.Location location, String path, JavaFileObject.Kind kind) {
            inner = new InnerFileObject(URI.create("string:///" + path), kind);
            this.location = location;
        }

        @Override
        public URI toUri() {
            log.debug("toUri() " + inner.toUri());
            return inner.toUri();
        }

        @Override
        public String getName() {
            log.debug("getName() " + inner.toUri());
            return inner.getName();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            log.debug("openInputStream() " + inner.toUri());
            return inner.openInputStream();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            log.debug("openOutputStream() " + inner.toUri());
            return inner.openOutputStream();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            log.debug("openReader(" + ignoreEncodingErrors + ") " + inner.toUri());
            return inner.openReader(ignoreEncodingErrors);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            log.debug("getCharContent(" + ignoreEncodingErrors + ") " + inner.toUri());
            return inner.getCharContent(ignoreEncodingErrors);
        }

        @Override
        public Writer openWriter() throws IOException {
            log.debug("openWriter() " + inner.toUri());
            return inner.openWriter();
        }

        @Override
        public long getLastModified() {
            log.debug("getLastModified() " + inner.toUri());
            return inner.getLastModified();
        }

        @Override
        public boolean delete() {
            log.debug("delete() " + inner.toUri());
            return inner.delete();
        }

        @Override
        public String toString() {
            return "InMemoryFileObject[" + inner.toString() + "]";
        }
    }

    public class InMemoryJavaFileObject extends InMemoryFileObject implements JavaFileObject {
        public InMemoryJavaFileObject(JavaFileManager.Location location, String path, Kind kind) {
            super(location, path, kind);
        }

        @Override
        public Kind getKind() {
            log.debug("getKind() " + inner.toUri());
            return inner.getKind();
        }

        @Override
        public boolean isNameCompatible(String simpleName, Kind kind) {
            log.debug("isNameCompatible(" + simpleName + ", " + kind + ") " + inner.toUri());
            return inner.isNameCompatible(simpleName, kind);
        }

        @Override
        public NestingKind getNestingKind() {
            log.debug("getNestingKind() " + inner.toUri());
            return inner.getNestingKind();
        }

        @Override
        public Modifier getAccessLevel() {
            log.debug("getAccessLevel() " + inner.toUri());
            return inner.getAccessLevel();
        }
    }

    public class LoggingFileObject implements FileObject {
        private final FileObject inner;

        public LoggingFileObject(FileObject inner) {
            this.inner = inner;
        }

        @Override
        public URI toUri() {
            log.debug("toUri() " + inner.toUri());
            return inner.toUri();
        }

        @Override
        public String getName() {
            log.debug("getName() " + inner.toUri());
            return inner.getName();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            log.debug("openInputStream() " + inner.toUri());
            return inner.openInputStream();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            log.debug("openOutputStream() " + inner.toUri());
            return inner.openOutputStream();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            log.debug("openReader(" + ignoreEncodingErrors + ") " + inner.toUri());
            return inner.openReader(ignoreEncodingErrors);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            log.debug("getCharContent(" + ignoreEncodingErrors + ") " + inner.toUri());
            return inner.getCharContent(ignoreEncodingErrors);
        }

        @Override
        public Writer openWriter() throws IOException {
            log.debug("openWriter() " + inner.toUri());
            return inner.openWriter();
        }

        @Override
        public long getLastModified() {
            log.debug("getLastModified() " + inner.toUri());
            return inner.getLastModified();
        }

        @Override
        public boolean delete() {
            log.debug("delete() " + inner.toUri());
            return inner.delete();
        }
    }

    public class LoggingJavaFileObject extends LoggingFileObject implements JavaFileObject {
        private final JavaFileObject inner;

        public LoggingJavaFileObject(JavaFileObject inner) {
            super(inner);
            this.inner = inner;
        }

        @Override
        public Kind getKind() {
            log.debug("getKind() " + inner.toUri());
            return inner.getKind();
        }

        @Override
        public boolean isNameCompatible(String simpleName, Kind kind) {
            log.debug("isNameCompatible(" + simpleName + ", " + kind + ") " + inner.toUri());
            return inner.isNameCompatible(simpleName, kind);
        }

        @Override
        public NestingKind getNestingKind() {
            log.debug("getNestingKind() " + inner.toUri());
            return inner.getNestingKind();
        }

        @Override
        public Modifier getAccessLevel() {
            log.debug("getAccessLevel() " + inner.toUri());
            return inner.getAccessLevel();
        }

    }

}
