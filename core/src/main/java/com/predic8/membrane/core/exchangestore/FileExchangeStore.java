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
import com.predic8.membrane.core.util.ConfigurationException;
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

    /**
     * Exchange properties with the original body lengths of the request and response, as {@link Long}, for exchanges
     * read from the store, as the store does not read bodies when it lists exchanges.
     */
    public static final String REQUEST_BODY_LENGTH = "membrane.fileExchangeStore.requestBodyLength";
    public static final String RESPONSE_BODY_LENGTH = "membrane.fileExchangeStore.responseBodyLength";

    private static final ObjectMapper om = new ObjectMapper();
    private static final byte[] END_OF_HEADER = "\r\n\r\n".getBytes(US_ASCII);

    private String dir;

    private boolean raw = false;
    private boolean saveBodyOnly = false;
    private int maxDays = -1;
    private int maxIndexed = 10_000;

    private record IndexEntry(long id, StoredExchange stored, Path file) {
    }

    private static final AtomicInteger instances = new AtomicInteger();

    /**
     * Exchange properties of this store instance: several stores may snapshot the same exchange into different
     * directories.
     */
    private final String fileBaseProperty = FILE_BASE + "." + instances.incrementAndGet();
    private final String indexIdProperty = fileBaseProperty + ".id";

    /**
     * The highest exchange ID assigned so far. IDs are persisted in the exchange files, so they stay stable across
     * restarts.
     */
    private final AtomicLong nextId = new AtomicLong();

    /**
     * The file in {@link #dir} with the highest ID that may have been assigned. The index holds only the newest
     * exchanges, so after a restart, e.g. with a smaller {@code maxIndexed}, the loaded IDs need not include the
     * highest one. IDs are reserved in blocks, so the file is not written for every exchange.
     */
    static final String RESERVED_ID_FILE = "reserved-exchange-id";
    private static final long ID_BLOCK = 1000;

    /**
     * The highest reserved ID. Guarded by {@code this}.
     */
    private long reservedId;

    /**
     * The newest exchanges, oldest first. Guarded by {@code this}.
     */
    private final Map<Long, IndexEntry> index = new HashMap<>();

    /**
     * The entries of {@link #index}, oldest exchange first. Eviction and admission use the order in which loading the
     * index after a restart selects the files: by exchange time, then by the number in the file name. The ID is no
     * substitute: it is assigned when the metadata is written first, which can happen in another order.
     */
    private final TreeSet<IndexEntry> byAge = new TreeSet<>(OLDEST_FIRST);

    private static final Comparator<IndexEntry> OLDEST_FIRST = Comparator
            .comparingLong((IndexEntry e) -> e.stored().time())
            .thenComparingLong(e -> counterPart(e.file().getFileName().toString()))
            .thenComparing(e -> e.file().getFileName().toString());
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
            // A message without Content-Length, e.g. chunked, has its length only here. The properties of the
            // exchange are not thread safe, and request and response are snapped on different threads: use its lock.
            if (body != null)
                synchronized (exc) {
                    exc.setProperty(flow == Flow.REQUEST ? REQUEST_BODY_LENGTH : RESPONSE_BODY_LENGTH, body.getLength());
                }
            switch (flow) {
                case REQUEST -> {
                    writeFile(exc.getRequest(), base + "-" + REQUEST_SUFFIX, body);
                    // The response was snapped before the request body completed: reference the request file now.
                    // writeExchangeFile() sets the ID and checks for the request file under the same lock, so
                    // either it saw the file or this sees the ID.
                    final boolean indexed;
                    synchronized (exc) {
                        indexed = exc.getProperty(indexIdProperty) != null;
                    }
                    if (indexed)
                        writeExchangeFile(exc);
                }
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
            final var existing = (String) exc.getProperty(fileBaseProperty);
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
            exc.setProperty(fileBaseProperty, base);
            exc.setProperty(MESSAGE_FILE_PATH, base);
            return base;
        }
    }

    /**
     * Writes the metadata of a completed exchange next to its message files and adds it to the index. A repeated
     * call for the same exchange rewrites the file and updates its index entry.
     */
    private void writeExchangeFile(AbstractExchange exc) {
        final var base = getBasePath(exc);
        if (base == null)
            return;
        ensureIndexLoaded();
        // The request and the response snapshot can write the metadata concurrently. Serialize them, so that an
        // older snapshot, e.g. one taken before the request file existed, cannot overwrite a newer one.
        synchronized (exc) {
            final var existing = (Long) exc.getProperty(indexIdProperty);
            final long id = existing != null ? existing : newId();
            exc.setProperty(indexIdProperty, id);
            final var stored = StoredExchange.of(id, exc, Path.of(base).getFileName().toString(), raw, saveBodyOnly,
                    new File(base + "-" + REQUEST_SUFFIX).exists(), exc.getResponse() != null);
            final var file = Path.of(base + "-" + EXCHANGE_SUFFIX);
            try {
                writeAtomically(file, om.writeValueAsString(stored));
            } catch (IOException e) {
                log.error("Could not write {}", file, e);
                return;
            }
            addToIndex(id, stored, file);
        }
    }

    /**
     * Readers never see a partially written file.
     */
    private static void writeAtomically(Path file, String content) throws IOException {
        final var tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
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

    /**
     * Writes the message to a temporary file and moves it into place, so that the file exists only when it is
     * complete: {@link #writeExchangeFile(AbstractExchange)} records an existing request file as stored.
     */
    private void writeFile(Message msg, String path, AbstractBody body) throws Exception {
        final var file = Path.of(path);
        final var tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (FileOutputStream os = new FileOutputStream(tmp.toFile())) {
                writeMessage(msg, body, os);
            }
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private void writeMessage(Message msg, AbstractBody body, OutputStream os) throws Exception {
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

        final var cutoff = startOfFirstKeptDay(now);
        synchronized (this) {
            if (byAge.removeIf(e -> e.stored().time() < cutoff)) {
                index.values().removeIf(e -> e.stored().time() < cutoff);
                modify();
            }
        }
    }

    /**
     * @return the first midnight that is not before {@code cutoff}: day folders before it are deleted
     */
    private static long startOfFirstKeptDay(Calendar cutoff) {
        final var day = (Calendar) cutoff.clone();
        day.set(HOUR_OF_DAY, 0);
        day.set(MINUTE, 0);
        day.set(SECOND, 0);
        day.set(MILLISECOND, 0);
        if (day.before(cutoff))
            day.add(DAY_OF_MONTH, 1);
        return day.getTimeInMillis();
    }

    @Override
    public void init(Router router) {
        ensureIndexLoaded();
        initializeTimer();
    }

    boolean isCleanupScheduled() {
        return cleanupTimer != null;
    }

    private record Loaded(StoredExchange stored, Path file) {
    }

    /**
     * Reads the newest {@code maxIndexed} readable exchange files once. Exchanges keep their persisted IDs; files
     * written before IDs were persisted get new ones.
     */
    private synchronized void ensureIndexLoaded() {
        if (indexLoaded)
            return;
        indexLoaded = true;
        if (dir == null || !Files.isDirectory(Path.of(dir)))
            return;
        final var loaded = new ArrayList<Loaded>();
        visitNewestExchangeFiles(Path.of(dir), maxIndexed, file -> {
            try {
                loaded.add(new Loaded(om.readValue(file.toFile(), StoredExchange.class), file));
            } catch (Exception e) {
                log.warn("Skipping unreadable exchange file {}: {}", file, e.getMessage());
            }
            return loaded.size() < maxIndexed;
        });
        loaded.stream().mapToLong(l -> l.stored().id()).max().ifPresent(max -> nextId.accumulateAndGet(max, Math::max));
        reservedId = readReservedId();
        nextId.accumulateAndGet(reservedId, Math::max);
        for (final var l : loaded.reversed())
            add(new IndexEntry(l.stored().id() > 0 ? l.stored().id() : newId(), l.stored(), l.file()));
        modify();
    }

    /**
     * @return a new ID, higher than every ID assigned before, also before a restart
     */
    private synchronized long newId() {
        final var id = nextId.incrementAndGet();
        if (id > reservedId && dir != null) {
            reservedId = id + ID_BLOCK - 1;
            try {
                writeAtomically(Path.of(dir, RESERVED_ID_FILE), Long.toString(reservedId));
            } catch (IOException e) {
                log.warn("Could not write {}: IDs can be assigned again after a restart. {}", RESERVED_ID_FILE, e.getMessage());
            }
        }
        return id;
    }

    private long readReservedId() {
        final var file = Path.of(dir, RESERVED_ID_FILE);
        if (!Files.exists(file))
            return 0;
        try {
            return Long.parseLong(Files.readString(file, UTF_8).trim());
        } catch (IOException | NumberFormatException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return 0;
        }
    }

    /**
     * @return the newest exchange files, newest first, walking {@code year/month/day} directories backwards.
     */
    static List<Path> newestExchangeFiles(Path root, int limit) {
        final var result = new ArrayList<Path>();
        if (limit > 0)
            visitNewestExchangeFiles(root, limit, f -> {
                result.add(f);
                return result.size() < limit;
            });
        return result;
    }

    /**
     * Passes the exchange files to {@code visitor}, newest first, until it returns false.
     *
     * @param batchSize how many files of a day are selected at first. A busy day can hold many more files than the
     *                  visitor needs, so they are not all kept in memory and sorted. If the visitor wants more, e.g.
     *                  because some files were unreadable, the next batch of older files is selected, twice as large
     *                  as the previous one, so that many unreadable files do not cause many scans of the directory.
     */
    private static void visitNewestExchangeFiles(Path root, int batchSize, java.util.function.Predicate<Path> visitor) {
        for (final var year : numericSubdirsDescending(root))
            for (final var month : numericSubdirsDescending(year))
                for (final var day : numericSubdirsDescending(month)) {
                    Path olderThan = null;
                    var size = batchSize;
                    while (true) {
                        final List<Path> batch;
                        try {
                            batch = newestExchangeFilesOfDay(day, olderThan, size);
                        } catch (IOException e) {
                            log.warn("Could not list {}: {}", day, e.getMessage());
                            break;
                        }
                        for (final var f : batch)
                            if (!visitor.test(f))
                                return;
                        if (batch.size() < size)
                            break;
                        olderThan = batch.getLast();
                        size = (int) Math.min(2L * size, Integer.MAX_VALUE - 8);
                    }
                }
    }

    /**
     * @param olderThan only files older than this one, or null for all
     * @return at most {@code limit} exchange files of the directory, newest first
     */
    private static List<Path> newestExchangeFilesOfDay(Path day, Path olderThan, int limit) throws IOException {
        // The head is the oldest of the selected files, so it is the one to drop when a newer file is found.
        final var newest = new PriorityQueue<Path>(NEWEST_FIRST.reversed());
        try (var files = Files.list(day)) {
            files.filter(f -> f.getFileName().toString().endsWith("-" + EXCHANGE_SUFFIX))
                    .filter(f -> olderThan == null || NEWEST_FIRST.compare(f, olderThan) > 0)
                    .forEach(f -> {
                        newest.add(f);
                        if (newest.size() > limit)
                            newest.poll();
                    });
        }
        final var result = new ArrayList<>(newest);
        result.sort(NEWEST_FIRST);
        return result;
    }

    /**
     * Orders {@code <time>-<counter>-Exchange.json} names by time, then by the numeric counter, newest first.
     */
    static final Comparator<Path> NEWEST_FIRST = Comparator
            .comparing((Path f) -> timePart(f.getFileName().toString()))
            .thenComparingLong(f -> counterPart(f.getFileName().toString()))
            .thenComparing(f -> f.getFileName().toString()) // a total order, also for unexpected names
            .reversed();

    private static String timePart(String name) {
        final var dash = name.indexOf('-');
        return dash < 0 ? name : name.substring(0, dash);
    }

    private static long counterPart(String name) {
        final var dash = name.indexOf('-');
        final var next = dash < 0 ? -1 : name.indexOf('-', dash + 1);
        if (next < 0)
            return -1;
        try {
            return Long.parseLong(name.substring(dash + 1, next));
        } catch (NumberFormatException e) {
            return -1;
        }
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

    private synchronized void addToIndex(long id, StoredExchange stored, Path file) {
        final var entry = new IndexEntry(id, stored, file);
        // An exchange older than all of a full index, e.g. a later snapshot of an evicted one, must not evict a newer one.
        if (!index.containsKey(id) && index.size() >= maxIndexed && OLDEST_FIRST.compare(entry, byAge.first()) < 0)
            return;
        add(entry);
        modify();
    }

    private void add(IndexEntry entry) {
        final var previous = index.put(entry.id(), entry);
        if (previous != null)
            byAge.remove(previous);
        byAge.add(entry);
        while (index.size() > maxIndexed)
            index.remove(byAge.pollFirst().id());
    }

    /**
     * Advances the modification marker on every change, also within the same millisecond.
     */
    private void modify() {
        lastModification = Math.max(lastModification + 1, System.currentTimeMillis());
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
        final var deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        synchronized (this) {
            while (lastKnownModification >= lastModification) {
                final var remaining = (deadline - System.nanoTime()) / 1_000_000;
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
        proxy.setInboundTls(s.inboundTls());
        exc.setProxy(proxy);
        exc.setServer(s.server());
        try {
            if (s.request() != null) {
                final var req = new Request();
                req.setMethod(s.request().method());
                req.setUri(s.request().uri());
                fill(req, s.request(), entry.file(), withBodies);
                exc.setRequest(req);
                if (s.request().bodyLength() != null)
                    exc.setProperty(REQUEST_BODY_LENGTH, s.request().bodyLength());
            }
            if (s.response() != null) {
                final var res = new Response();
                res.setStatusCode(s.response().statusCode());
                res.setStatusMessage(s.response().statusMessage());
                fill(res, s.response(), entry.file(), withBodies);
                exc.setResponse(res);
                if (s.response().bodyLength() != null)
                    exc.setProperty(RESPONSE_BODY_LENGTH, s.response().bodyLength());
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
        if (stored.bodyFormatted()) {
            header.removeFields(Header.CONTENT_ENCODING);
            // The formatted body was written as UTF-8, whatever charset the original declared.
            final var contentType = header.getContentType();
            if (contentType != null)
                header.setContentType(withUtf8Charset(contentType));
        }
        msg.setHeader(header);
        msg.setBody(withBody && stored.file() != null ? readBody(exchangeFile.resolveSibling(stored.file()), stored.headersInFile()) : new EmptyBody());
    }

    static String withUtf8Charset(String contentType) {
        final var parts = contentType.split(";");
        final var sb = new StringBuilder(parts[0].trim());
        for (int i = 1; i < parts.length; i++)
            if (!parts[i].trim().toLowerCase(Locale.ROOT).startsWith("charset="))
                sb.append(';').append(parts[i]);
        return sb.append("; charset=UTF-8").toString();
    }

    /**
     * The body is read from the file only when it is used, so looking at an exchange with a large body does not load
     * the body into memory.
     */
    private static AbstractBody readBody(Path file, boolean headersInFile) throws IOException {
        if (!Files.exists(file))
            return new EmptyBody();
        final var offset = headersInFile ? bodyOffset(file) : 0;
        final var length = Files.size(file) - offset;
        return length <= 0 ? new EmptyBody() : new FileBody(file, offset, length);
    }

    /**
     * A stored body. As long as nothing has read it into memory, {@link #getContentAsStream()} streams it from its
     * file on every call, so a reader that needs only the beginning, or streams it, does not load the whole body.
     * Other accessors, e.g. {@link #getContent()}, read it into memory as usual.
     */
    private static final class FileBody extends Body {
        private final Path file;
        private final long offset;
        private final long length;

        FileBody(Path file, long offset, long length) {
            super(new LazyFileInputStream(file, offset, length), length);
            this.file = file;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public InputStream getContentAsStream() {
            if (isRead())
                return super.getContentAsStream();
            return new LazyFileInputStream(file, offset, length);
        }
    }

    /**
     * @return the position of the body in a file that starts with the start line and header fields; the file size if
     * the header fields do not end
     */
    private static long bodyOffset(Path file) throws IOException {
        try (var in = new BufferedInputStream(Files.newInputStream(file))) {
            long position = 0;
            var matched = 0;
            int b;
            while ((b = in.read()) != -1) {
                position++;
                matched = b == END_OF_HEADER[matched] ? matched + 1 : (b == END_OF_HEADER[0] ? 1 : 0);
                if (matched == END_OF_HEADER.length)
                    return position;
            }
            return position;
        }
    }

    /**
     * Opens the stored body of a message for streaming. Unlike {@link #getExchangeById(long)}, nothing is read into
     * memory, so also very large bodies can be downloaded.
     *
     * @return null if the exchange is not indexed or the message has no stored body
     */
    public InputStream openBody(long id, boolean response) throws IOException {
        final var file = bodyFile(id, response);
        if (file == null)
            return null;
        final var offset = file.headersInFile() ? bodyOffset(file.path()) : 0;
        final var length = Files.size(file.path()) - offset;
        if (length <= 0)
            return null;
        return new LazyFileInputStream(file.path(), offset, length);
    }

    /**
     * @return whether the body was pretty-printed when it was written, so it differs from the original body
     */
    public boolean isBodyFormatted(long id, boolean response) {
        final var file = bodyFile(id, response);
        return file != null && file.formatted();
    }

    private record BodyFile(Path path, boolean headersInFile, boolean formatted) {
    }

    private BodyFile bodyFile(long id, boolean response) {
        ensureIndexLoaded();
        final IndexEntry entry;
        synchronized (this) {
            entry = index.get(id);
        }
        if (entry == null)
            return null;
        final var stored = response ? entry.stored().response() : entry.stored().request();
        if (stored == null || stored.file() == null)
            return null;
        final var path = entry.file().resolveSibling(stored.file());
        return Files.exists(path) ? new BodyFile(path, stored.headersInFile(), stored.bodyFormatted()) : null;
    }

    /**
     * Opens the file on the first read and closes it after {@code length} bytes, at its end, or on an error. A
     * {@link Body} with a known length stops reading after that length, without reading to the end of the stream.
     */
    private static final class LazyFileInputStream extends InputStream {
        private final Path file;
        private final long offset;
        private long remaining;
        private InputStream in;
        private boolean closed;

        LazyFileInputStream(Path file, long offset, long length) {
            this.file = file;
            this.offset = offset;
            this.remaining = length;
        }

        private InputStream in() throws IOException {
            if (closed)
                throw new IOException("Stream closed.");
            if (in == null) {
                in = Files.newInputStream(file);
                in.skipNBytes(offset);
            }
            return in;
        }

        @Override
        public int read() throws IOException {
            final var b = new byte[1];
            return read(b, 0, 1) == -1 ? -1 : b[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed || remaining <= 0) {
                close();
                return -1;
            }
            if (len == 0)
                return 0;
            final int n;
            try {
                n = in().read(b, off, (int) Math.min(len, remaining));
            } catch (IOException | RuntimeException e) {
                close();
                throw e;
            }
            if (n == -1) {
                close();
                return -1;
            }
            remaining -= n;
            if (remaining == 0)
                close();
            return n;
        }

        @Override
        public void close() throws IOException {
            if (closed)
                return;
            closed = true;
            if (in != null)
                in.close();
        }
    }

    /**
     * Metadata of an exchange written to {@code <time>-<number>-Exchange.json} next to its message files.
     */
    /**
     * @param id         the exchange ID in the admin console; 0 in files written before IDs were persisted
     * @param inboundTls whether the client connected with TLS
     */
    record StoredExchange(int version, long id, long time, String originalRequestUri, String status, String errorMessage,
                          long timeReqSent, long timeReqReceived, long timeResSent, long timeResReceived,
                          List<String> destinations, String remoteAddr, String remoteAddrIp, String proxyName,
                          int proxyPort, boolean inboundTls, String server, StoredMessage request,
                          StoredMessage response) {

        static StoredExchange of(long id, AbstractExchange exc, String baseName, boolean raw, boolean saveBodyOnly,
                                 boolean requestWritten, boolean responseWritten) {
            final var proxy = exc.getProxy();
            final var req = exc.getRequest();
            final var res = exc.getResponse();
            return new StoredExchange(1, id, exc.getTime().getTimeInMillis(), exc.getOriginalRequestUri(),
                    exc.getStatus() == null ? null : exc.getStatus().name(), exc.getErrorMessage(),
                    exc.getTimeReqSent(), exc.getTimeReqReceived(), exc.getTimeResSent(), exc.getTimeResReceived(),
                    exc.getDestinations() == null ? List.of() : List.copyOf(exc.getDestinations()),
                    exc.getRemoteAddr(), exc.getRemoteAddrIp(),
                    proxy == null ? null : proxy.getName(), proxy == null ? -1 : proxy.getKey().getPort(),
                    FakeProxy.isInboundTls(proxy), exc.getServer(),
                    req == null ? null : StoredMessage.of(req, req.getMethod(), req.getUri(), null, null,
                            requestWritten ? baseName + "-" + REQUEST_SUFFIX : null, raw, saveBodyOnly,
                            (Long) exc.getProperty(REQUEST_BODY_LENGTH)),
                    res == null ? null : StoredMessage.of(res, null, null, res.getStatusCode(), res.getStatusMessage(),
                            responseWritten ? baseName + "-" + RESPONSE_SUFFIX : null, raw, saveBodyOnly,
                            (Long) exc.getProperty(RESPONSE_BODY_LENGTH)));
        }
    }

    /**
     * @param file          name of the message file in the same directory, or null if none was written
     * @param headersInFile whether the file starts with the start line and header fields
     * @param bodyFormatted whether the body was pretty-printed (and decoded) when it was written
     */
    record StoredMessage(String method, String uri, Integer statusCode, String statusMessage, String version,
                         List<StoredHeader> headers, String file, boolean headersInFile, boolean bodyFormatted,
                         Long bodyLength) {

        static StoredMessage of(Message msg, String method, String uri, Integer statusCode, String statusMessage,
                                String file, boolean raw, boolean saveBodyOnly, Long bodyLength) {
            final var withHeaders = raw || !saveBodyOnly;
            // saveBodyOnly: header fields (e.g. Authorization) are not stored anywhere, also not in the metadata.
            final var headers = !withHeaders ? List.<StoredHeader>of() : Arrays.stream(msg.getHeader().getAllHeaderFields())
                    .map(f -> new StoredHeader(f.getHeaderName().toString(), f.getValue()))
                    .toList();
            return new StoredMessage(method, uri, statusCode, statusMessage, msg.getVersion(), headers, file,
                    withHeaders, !raw && msg.isXML(), bodyLength);
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
            entries = new ArrayList<>(byAge);
        }
        final var result = new ArrayList<AbstractExchange>(entries.size());
        for (final var entry : entries)
            result.add(toExchange(entry, false));
        return result;
    }

    public void removeAllExchanges(AbstractExchange[] exchanges) {
        // ignore
    }

    /**
     * @return statistics per client over the indexed exchanges
     */
    @Override
    public List<? extends ClientStatistics> getClientStatistics() {
        final var clients = new HashMap<String, ClientStatisticsCollector>();
        for (final var exc : getAllExchangesAsList())
            clients.computeIfAbsent(exc.getRemoteAddr(), ClientStatisticsCollector::new).collect(exc);
        return new ArrayList<>(clients.values());
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
        if (maxIndexed < 1)
            throw new ConfigurationException("fileExchangeStore: maxIndexed must be at least 1, but is %d.".formatted(maxIndexed));
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
