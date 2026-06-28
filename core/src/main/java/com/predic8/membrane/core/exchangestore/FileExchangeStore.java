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

import com.predic8.membrane.annot.Constants;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleKey;
import com.predic8.membrane.core.proxies.StatisticCollector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.util.TimerTaskUtil.createTimerTask;
import static com.predic8.membrane.core.util.xml.XMLTextUtil.formatXML;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Calendar.DAY_OF_MONTH;

/**
 * @description <p>When APIs pass through a gateway, you often need a complete record of what was
 * sent and received: for debugging, compliance, or post-incident investigation. The
 * <code>fileExchangeStore</code> captures every HTTP request and response to disk so you can
 * inspect or replay traffic long after it happened, without modifying any upstream service.</p>
 * <p>Each exchange is written as a pair of UTF-8 <code>.msg</code> files under
 * <code>&lt;dir&gt;/YYYY/M/D/</code>, one for the request and one for the response.
 * XML bodies are pretty-printed by default. The exchange property <code>message.file.path</code>
 * is set to the shared base path of the pair so downstream plugins can locate the files.</p>
 * <p>Declare it once in the <code>components:</code> section; Membrane picks it up automatically
 * as the global exchange store — no per-API wiring is needed.</p>
 * <p>When <code>maxDays</code> is non-negative, a nightly background task at 03:14 removes
 * day-directories older than that many days.</p>
 * <pre>
 * fileExchangeStore:
 *   dir: &lt;path&gt;                    # required
 *   [ raw: true | false ]           # default false
 *   [ saveBodyOnly: true | false ]  # default false; ignored when raw is true
 *   [ maxDays: &lt;n&gt; ]               # default -1 (keep forever)
 * </pre>
 * See <code>tutorials/operation/70-FileExchangeStore.yaml</code> and
 * <code>examples/extending-membrane/file-exchangestore</code>.
 * @topic 4. Monitoring, Logging and Statistics
 * @yaml <pre><code>
 * components:
 *   store:
 *     fileExchangeStore:
 *       dir: ./exchanges
 * ---
 * api:
 *   port: 2000
 *   target:
 *     url: https://api.predic8.de
 * </code></pre>
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

    private String dir;

    private boolean raw = false;
    private boolean saveBodyOnly = false;
    private int maxDays = -1;

    public FileExchangeStore() {
        initializeTimer();
    }

    public void initializeTimer() {
        if (this.maxDays < 0) {
            return; // don't do anything if this feature is deactivated
        }

        Timer oldFilesCleanupTimer = new Timer("Clean up old log files", true);

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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void snapInternal(AbstractExchange exc, Flow flow, AbstractBody body) {
        int fileNumber = counter.incrementAndGet();

        StringBuilder buf = getDirectoryNameBuffer(exc.getTime());

        File directory = new File(buf.toString());

        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();

        if (directory.exists() && directory.isDirectory()) {
            buf.append(separator);
            buf.append(getDateFormat().format(exc.getTime().getTime()));
            buf.append("-");
            buf.append(fileNumber);
            exc.setProperty(MESSAGE_FILE_PATH, buf.toString());
            buf.append("-");
            StringBuilder buf2 = new StringBuilder(buf);
            buf.append("Request.msg");
            buf2.append("Response.msg");
            try {
                switch (flow) {
                    case REQUEST -> writeFile(exc.getRequest(), buf.toString(), body);
                    case ABORT, RESPONSE -> {
                        if (exc.getResponse() != null)
                            writeFile(exc.getResponse(), buf2.toString(), body);
                    }
                }
            } catch (Exception e) {
                log.error("While writing {} {} to a file.", exc.getRequest().getUri(), flow, e);
            }
        } else {
            log.error("Directory does not exists or file is not a directory: {}", buf);
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
        return null;
    }

    public List<AbstractExchange> getAllExchangesAsList() {
        return null;
    }

    public void removeAllExchanges(AbstractExchange[] exchanges) {
        // ignore
    }

    public String getDir() {
        return dir;
    }

    /**
     * @description Directory where request and response files are written. Created automatically if it does not exist.
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
     * @description When <code>true</code>, always writes the start line and headers (overriding <code>saveBodyOnly</code>)
     * and copies the body verbatim — no XML pretty-printing.
     * @default false
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
     * @description When <code>true</code>, omits the start line and headers and writes only the body.
     * Ignored when <code>raw</code> is <code>true</code>.
     * @default false
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
     * @description Number of days for which exchange files are kept. A nightly background task at 03:14
     * removes day-directories older than this value. Set to -1 to keep files indefinitely.
     * @default -1
     * @example 60
     */
    @MCAttribute
    public void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
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
