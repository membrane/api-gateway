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

package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.apache.commons.io.*;
import org.slf4j.*;

import java.io.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.util.TimerTaskUtil.createTimerTask;
import static com.predic8.membrane.core.util.xml.XMLTextUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Calendar.*;

/**
 * The output file is UTF-8 encoded.
 */
@MCElement(name = "fileExchangeStore")
public class FileExchangeStore extends AbstractExchangeStore {

    private static final Logger log = LoggerFactory.getLogger(FileExchangeStore.class
            .getName());

    private static final AtomicInteger counter = new AtomicInteger();

    private static final String DATE_FORMAT = "'h'HH'm'mm's'ss'ms'SSS";

    private static final ThreadLocal<DateFormat> dateFormat = new ThreadLocal<>();

    private static final String separator = FileSystems.getDefault().getSeparator();

    public static final String MESSAGE_FILE_PATH = "message.file.path";

    private static final String FILE_BASE = "membrane.fileExchangeStore.base";
    static final String REQUEST_SUFFIX = "Request.msg";
    static final String RESPONSE_SUFFIX = "Response.msg";
    static final String EXCHANGE_SUFFIX = "Exchange.json";

    private static final ObjectMapper om = new ObjectMapper();
    private static final byte[] END_OF_HEADER = "\r\n\r\n".getBytes(US_ASCII);

    private String dir;

    private boolean raw = false;
    private boolean saveBodyOnly = false;
    private int maxDays = -1;
    private int maxIndexed = 10_000;

    private record IndexEntry(long id, StoredExchange stored, Path file) {
    }

    private final AtomicLong nextId = new AtomicLong();

    /**
     * The newest exchanges, oldest first. Guarded by {@code this}.
     */
    private final LinkedHashMap<Long, IndexEntry> index = new LinkedHashMap<>();
    private boolean indexLoaded;
    private long lastModification = System.currentTimeMillis();

    private Timer cleanupTimer;

    /**
     * Schedules the daily deletion of old folders, once. Called from {@link #init(Router)}, after {@code maxDays} has
     * been configured.
     */
    public synchronized void initializeTimer() {
        if (this.maxDays < 0 || cleanupTimer != null) {
            return; // don't do anything if this feature is deactivated or already scheduled
        }

        Timer oldFilesCleanupTimer = new Timer("Clean up old log files", true);
        cleanupTimer = oldFilesCleanupTimer;

        // schedule first run for the night
        Calendar firstRun = Calendar.getInstance();
        firstRun.set(Calendar.HOUR_OF_DAY, 3);
        firstRun.set(Calendar.MINUTE, 14);

        // schedule for the next day if the scheduled execution time is before now
        if (firstRun.before(Calendar.getInstance()))
            firstRun.add(DAY_OF_MONTH, 1);

        oldFilesCleanupTimer.scheduleAtFixedRate(createTimerTask(() -> {
                    try {
                        deleteOldFolders(Calendar.getInstance());
                    } catch (IOException e) {
                        log.error("", e);
                    }
                }),
                firstRun.getTime(),
                24 * 60 * 60 * 1000        // one day
        );
    }

    public void snap(final AbstractExchange exc, final Flow flow) {
        try {
            Message m = flow == Flow.REQUEST ? exc.getRequest() : exc.getResponse();
            // TODO: [fix me] support multi-snap
            // TODO: [fix me] snap message headers *here*, not in observer
            if (m != null)
                m.addObserver(new SnapshottingObserver(exc, flow));
            else if (flow != Flow.REQUEST)
                writeExchangeFile(exc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void snapInternal(AbstractExchange exc, Flow flow, AbstractBody body) {
        final var base = getBasePath(exc);
        if (base == null)
            return;
        try {
            switch (flow) {
                case REQUEST -> writeFile(exc.getRequest(), base + "-" + REQUEST_SUFFIX, body);
                case ABORT, RESPONSE -> {
                    if (exc.getResponse() != null)
                        writeFile(exc.getResponse(), base + "-" + RESPONSE_SUFFIX, body);
                    writeExchangeFile(exc);
                }
            }
        } catch (Exception e) {
            log.error("While writing {} {} to a file.", exc.getRequest().getUri(), flow, e);
        }
    }

    /**
     * The common prefix of the files of an exchange: request and response share the same number.
     *
     * @return null if the directory could not be created
     */
    private String getBasePath(AbstractExchange exc) {
        synchronized (exc) {
            final var existing = (String) exc.getProperty(FILE_BASE);
            if (existing != null)
                return existing;

            StringBuilder buf = getDirectoryNameBuffer(exc.getTime());

            File directory = new File(buf.toString());

            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();

            if (!directory.isDirectory()) {
                log.error("Directory does not exists or file is not a directory: {}", buf);
                return null;
            }
            buf.append(separator);
            buf.append(getDateFormat().format(exc.getTime().getTime()));
            buf.append("-");
            buf.append(counter.incrementAndGet());
            final var base = buf.toString();
            exc.setProperty(FILE_BASE, base);
            exc.setProperty(MESSAGE_FILE_PATH, base);
            return base;
        }
    }

    /**
     * Writes the metadata of a completed exchange next to its message files and adds it to the index.
     */
    private void writeExchangeFile(AbstractExchange exc) {
        final var base = getBasePath(exc);
        if (base == null)
            return;
        final var stored = StoredExchange.of(exc, Path.of(base).getFileName().toString(), raw, saveBodyOnly,
                new File(base + "-" + REQUEST_SUFFIX).exists(), exc.getResponse() != null);
        final var file = Path.of(base + "-" + EXCHANGE_SUFFIX);
        ensureIndexLoaded();
        try {
            Files.writeString(file, om.writeValueAsString(stored), UTF_8);
        } catch (IOException e) {
            log.error("Could not write {}", file, e);
            return;
        }
        addToIndex(stored, file);
    }

    private StringBuilder getDirectoryNameBuffer(Calendar time) {
        StringBuilder buf = new StringBuilder();
        buf.append(dir);
        buf.append(separator);
        buf.append(time.get(Calendar.YEAR));
        buf.append(separator);
        buf.append((time.get(Calendar.MONTH) + 1));
        buf.append(separator);
        buf.append(time.get(DAY_OF_MONTH));
        return buf;
    }

    private static DateFormat getDateFormat() {
        DateFormat df = dateFormat.get();
        if (df == null) {
            df = new SimpleDateFormat(DATE_FORMAT);
            dateFormat.set(df);
        }
        return df;
    }

    private void writeFile(Message msg, String path, AbstractBody body) throws Exception {
        File file = new File(path);

        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();

        try (FileOutputStream os = new FileOutputStream(file)) {
            if (raw || !saveBodyOnly) {
                msg.writeStartLine(os);
                msg.getHeader().write(os);
                os.write(Constants.CRLF_BYTES);
            }

            if (raw) {
                IOUtils.copy(body.getContentAsStream(), os);
            } else {
                if (msg.isXML()) {
                    os.write(formatXML(body.getContentAsStream(), /*asHTML*/ false).getBytes(UTF_8));
                    return;
                }
                IOUtils.copy(body.getContentAsStream(), os);
            }
        }
    }

    public void deleteOldFolders(Calendar now) throws IOException {
        if (this.maxDays < 0) {
            return; // don't do anything if this feature is deactivated
        }

        now.add(DAY_OF_MONTH, -maxDays);

        ArrayList<File> folders3 = new DepthWalker(3).getDirectories(new File(dir));

        ArrayList<File> deletion = new ArrayList<>();

        for (File f : folders3) {
            int day = Integer.parseInt(f.getName());
            int mon = Integer.parseInt(f.getParentFile().getName());
            int year = Integer.parseInt(f.getParentFile().getParentFile().getName());
            Calendar folderTime = Calendar.getInstance();
            folderTime.clear();
            folderTime.set(year, mon - 1, day);
            if (folderTime.before(now)) {
                deletion.add(f);
            }
        }

        for (File d : deletion) {
            FileUtils.deleteDirectory(d);
        }

        final var cutoff = now.getTimeInMillis();
        synchronized (this) {
            if (index.values().removeIf(e -> e.stored().time() < cutoff))
                modify();
        }
    }

    @Override
    public void init(Router router) {
        ensureIndexLoaded();
        initializeTimer();
    }

    boolean isCleanupScheduled() {
        return cleanupTimer != null;
    }

    /**
     * Reads the newest {@code maxIndexed} exchange files once.
     */
    private synchronized void ensureIndexLoaded() {
        if (indexLoaded)
            return;
        indexLoaded = true;
        if (dir == null || !Files.isDirectory(Path.of(dir)))
            return;
        final var files = newestExchangeFiles(Path.of(dir), maxIndexed);
        for (final var file : files.reversed()) {
            try {
                add(om.readValue(file.toFile(), StoredExchange.class), file);
            } catch (Exception e) {
                log.warn("Skipping unreadable exchange file {}: {}", file, e.getMessage());
            }
        }
        modify();
    }

    /**
     * @return the newest exchange files, newest first, walking {@code year/month/day} directories backwards.
     */
    static List<Path> newestExchangeFiles(Path root, int limit) {
        final var result = new ArrayList<Path>();
        for (final var year : numericSubdirsDescending(root))
            for (final var month : numericSubdirsDescending(year))
                for (final var day : numericSubdirsDescending(month)) {
                    try (var files = Files.list(day)) {
                        final var exchangeFiles = files
                                .filter(f -> f.getFileName().toString().endsWith("-" + EXCHANGE_SUFFIX))
                                .sorted(Comparator.comparing((Path f) -> f.getFileName().toString()).reversed())
                                .toList();
                        for (final var f : exchangeFiles) {
                            if (result.size() >= limit)
                                return result;
                            result.add(f);
                        }
                    } catch (IOException e) {
                        log.warn("Could not list {}: {}", day, e.getMessage());
                    }
                }
        return result;
    }

    private static List<Path> numericSubdirsDescending(Path parent) {
        try (var dirs = Files.list(parent)) {
            return dirs.filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().matches("\\d{1,4}"))
                    .sorted(Comparator.comparingInt((Path d) -> Integer.parseInt(d.getFileName().toString())).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("Could not list {}: {}", parent, e.getMessage());
            return List.of();
        }
    }

    private synchronized void addToIndex(StoredExchange stored, Path file) {
        add(stored, file);
        modify();
    }

    private void add(StoredExchange stored, Path file) {
        final var id = nextId.incrementAndGet();
        index.put(id, new IndexEntry(id, stored, file));
        final var it = index.values().iterator();
        while (index.size() > maxIndexed && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private void modify() {
        lastModification = System.currentTimeMillis();
        notifyAll();
    }

    @Override
    public synchronized long getLastModified() {
        return lastModification;
    }

    @Override
    public void waitForModification(long lastKnownModification) throws InterruptedException {
        synchronized (this) {
            while (lastKnownModification >= lastModification)
                wait();
        }
    }

    @Override
    public boolean waitForModification(long lastKnownModification, long timeoutMillis) throws InterruptedException {
        final var deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (this) {
            while (lastKnownModification >= lastModification) {
                final var remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0)
                    return false;
                wait(remaining);
            }
            return true;
        }
    }

    /**
     * @return the exchange including its bodies, read from disk; null if it is not among the indexed exchanges
     */
    @Override
    public AbstractExchange getExchangeById(long id) {
        ensureIndexLoaded();
        final IndexEntry entry;
        synchronized (this) {
            entry = index.get(id);
        }
        return entry == null ? null : toExchange(entry, true);
    }

    private static AbstractExchange toExchange(IndexEntry entry, boolean withBodies) {
        final var s = entry.stored();
        final var exc = new Exchange(null);
        exc.setId(entry.id());
        final var time = Calendar.getInstance();
        time.setTimeInMillis(s.time());
        exc.setTime(time);
        exc.setOriginalRequestUri(s.originalRequestUri());
        exc.setErrorMessage(s.errorMessage());
        if (s.status() != null)
            exc.setStatus(ExchangeState.valueOf(s.status()));
        exc.setTimeReqSent(s.timeReqSent());
        exc.setTimeReqReceived(s.timeReqReceived());
        exc.setTimeResSent(s.timeResSent());
        exc.setTimeResReceived(s.timeResReceived());
        exc.setDestinations(s.destinations() == null ? new ArrayList<>() : new ArrayList<>(s.destinations()));
        exc.setRemoteAddr(s.remoteAddr());
        exc.setRemoteAddrIp(s.remoteAddrIp());
        final var proxy = new FakeProxy(s.proxyPort());
        proxy.setName(s.proxyName());
        exc.setProxy(proxy);
        exc.setServer(s.server());
        try {
            if (s.request() != null) {
                final var req = new Request();
                req.setMethod(s.request().method());
                req.setUri(s.request().uri());
                fill(req, s.request(), entry.file(), withBodies);
                exc.setRequest(req);
            }
            if (s.response() != null) {
                final var res = new Response();
                res.setStatusCode(s.response().statusCode());
                res.setStatusMessage(s.response().statusMessage());
                fill(res, s.response(), entry.file(), withBodies);
                exc.setResponse(res);
            }
        } catch (IOException e) {
            log.warn("Could not read the messages of {}: {}", entry.file(), e.getMessage());
        }
        return exc;
    }

    private static void fill(Message msg, StoredMessage stored, Path exchangeFile, boolean withBody) throws IOException {
        msg.setVersion(stored.version());
        final var header = new Header();
        if (stored.headers() != null)
            for (final var h : stored.headers())
                header.add(h.name(), h.value());
        if (stored.bodyFormatted())
            header.removeFields(Header.CONTENT_ENCODING);
        msg.setHeader(header);
        msg.setBody(withBody && stored.file() != null ? readBody(exchangeFile.resolveSibling(stored.file()), stored.headersInFile()) : new EmptyBody());
    }

    private static AbstractBody readBody(Path file, boolean headersInFile) throws IOException {
        if (!Files.exists(file))
            return new EmptyBody();
        var bytes = Files.readAllBytes(file);
        if (headersInFile) {
            final var end = indexOf(bytes, END_OF_HEADER);
            bytes = end < 0 ? new byte[0] : Arrays.copyOfRange(bytes, end + END_OF_HEADER.length, bytes.length);
        }
        return bytes.length == 0 ? new EmptyBody() : new Body(bytes);
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++)
                if (data[i + j] != pattern[j])
                    continue outer;
            return i;
        }
        return -1;
    }

    /**
     * Metadata of an exchange written to {@code <time>-<number>-Exchange.json} next to its message files.
     */
    record StoredExchange(int version, long time, String originalRequestUri, String status, String errorMessage,
                          long timeReqSent, long timeReqReceived, long timeResSent, long timeResReceived,
                          List<String> destinations, String remoteAddr, String remoteAddrIp, String proxyName,
                          int proxyPort, String server, StoredMessage request, StoredMessage response) {

        static StoredExchange of(AbstractExchange exc, String baseName, boolean raw, boolean saveBodyOnly,
                                 boolean requestWritten, boolean responseWritten) {
            final var proxy = exc.getProxy();
            final var req = exc.getRequest();
            final var res = exc.getResponse();
            return new StoredExchange(1, exc.getTime().getTimeInMillis(), exc.getOriginalRequestUri(),
                    exc.getStatus() == null ? null : exc.getStatus().name(), exc.getErrorMessage(),
                    exc.getTimeReqSent(), exc.getTimeReqReceived(), exc.getTimeResSent(), exc.getTimeResReceived(),
                    exc.getDestinations() == null ? List.of() : List.copyOf(exc.getDestinations()),
                    exc.getRemoteAddr(), exc.getRemoteAddrIp(),
                    proxy == null ? null : proxy.getName(), proxy == null ? -1 : proxy.getKey().getPort(),
                    exc.getServer(),
                    req == null ? null : StoredMessage.of(req, req.getMethod(), req.getUri(), null, null,
                            requestWritten ? baseName + "-" + REQUEST_SUFFIX : null, raw, saveBodyOnly),
                    res == null ? null : StoredMessage.of(res, null, null, res.getStatusCode(), res.getStatusMessage(),
                            responseWritten ? baseName + "-" + RESPONSE_SUFFIX : null, raw, saveBodyOnly));
        }
    }

    /**
     * @param file          name of the message file in the same directory, or null if none was written
     * @param headersInFile whether the file starts with the start line and header fields
     * @param bodyFormatted whether the body was pretty-printed (and decoded) when it was written
     */
    record StoredMessage(String method, String uri, Integer statusCode, String statusMessage, String version,
                         List<StoredHeader> headers, String file, boolean headersInFile, boolean bodyFormatted) {

        static StoredMessage of(Message msg, String method, String uri, Integer statusCode, String statusMessage,
                                String file, boolean raw, boolean saveBodyOnly) {
            final var headers = Arrays.stream(msg.getHeader().getAllHeaderFields())
                    .map(f -> new StoredHeader(f.getHeaderName().toString(), f.getValue()))
                    .toList();
            return new StoredMessage(method, uri, statusCode, statusMessage, msg.getVersion(), headers, file,
                    raw || !saveBodyOnly, !raw && msg.isXML());
        }
    }

    record StoredHeader(String name, String value) {
    }

    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        throw new RuntimeException(
                "Method getExchanges() is not supported by FileExchangeStore");
    }

    public void remove(AbstractExchange exchange) {
        throw new RuntimeException(
                "Method remove() is not supported by FileExchangeStore");
    }

    public void removeAllExchanges(Proxy proxy) {
        throw new RuntimeException(
                "Method removeAllExchanges() is not supported by FileExchangeStore");
    }

    public StatisticCollector getStatistics(RuleKey ruleKey) {
        return null;
    }

    public Object[] getAllExchanges() {
        return getAllExchangesAsList().toArray();
    }

    /**
     * @return the indexed exchanges with start lines and header fields, but without bodies
     */
    public List<AbstractExchange> getAllExchangesAsList() {
        ensureIndexLoaded();
        final List<IndexEntry> entries;
        synchronized (this) {
            entries = new ArrayList<>(index.values());
        }
        final var result = new ArrayList<AbstractExchange>(entries.size());
        for (final var entry : entries)
            result.add(toExchange(entry, false));
        return result;
    }

    public void removeAllExchanges(AbstractExchange[] exchanges) {
        // ignore
    }

    public String getDir() {
        return dir;
    }

    /**
     * @description Directory where the exchanges are saved.
     * @example logs
     */
    @Required
    @MCAttribute
    public void setDir(String dir) {
        this.dir = dir;
    }

    public boolean isRaw() {
        return raw;
    }

    /**
     * @default false
     * @description If this is true, headers will always be printed (overriding
     * saveBodyOnly) and the body of the exchange won't be
     * formatted nicely.
     * @example true
     */
    @MCAttribute
    public void setRaw(boolean raw) {
        this.raw = raw;
    }

    public boolean isSaveBodyOnly() {
        return saveBodyOnly;
    }

    /**
     * @default false
     * @description If this is true, no headers will be written to the exchange
     * log files.
     * @example true
     */
    @MCAttribute
    public void setSaveBodyOnly(boolean saveBodyOnly) {
        this.saveBodyOnly = saveBodyOnly;
    }

    public int getMaxDays() {
        return maxDays;
    }

    /**
     * @default -1
     * @description Number of days for which exchange logs are preserved. A
     * value smaller than zero deactivates the deletion of old
     * logs.
     * @example 60
     */
    @MCAttribute
    public void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
    }

    public int getMaxIndexed() {
        return maxIndexed;
    }

    /**
     * @default 10000
     * @description Number of the newest exchanges that are kept in an in-memory index, so that they can be listed and
     * viewed in the admin console. Their bodies stay on disk and are read when an exchange is viewed.
     * @example 50000
     */
    @MCAttribute
    public void setMaxIndexed(int maxIndexed) {
        this.maxIndexed = maxIndexed;
    }

    private class SnapshottingObserver extends BodyCollectingMessageObserver {
        private final AbstractExchange exc;
        private final Flow flow;

        public SnapshottingObserver(AbstractExchange exc, Flow flow) {
            super(Strategy.ERROR, -1);
            this.exc = exc;
            this.flow = flow;
        }

        public void bodyRequested(AbstractBody body) {
        }

        public void bodyComplete(AbstractBody body) {
            snapInternal(exc, flow, getBody(body));
        }
    }
}
