/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.prometheus;

import com.sun.management.UnixOperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.function.LongSupplier;

import static com.predic8.membrane.core.interceptor.prometheus.PrometheusInterceptor.escapeLabelValue;
import static java.lang.management.ManagementFactory.*;
import static java.lang.management.MemoryType.HEAP;

/**
 * Basic JVM and process metrics in the Prometheus text exposition format, read from the
 * <code>java.lang.management</code> MXBeans on every call. Names, labels and units follow Micrometer, so dashboards
 * built for Spring Boot work at least partially.
 * <p>
 * Stateless, so it is safe to call from concurrent scrapes.
 */
final class JvmMetrics {

    private static final Logger log = LoggerFactory.getLogger(JvmMetrics.class);

    private JvmMetrics() {
    }

    static String collect() {
        final var sb = new StringBuilder();
        memory(sb);
        liveDataSize(sb);
        bufferPools(sb);
        gcPauses(sb);
        family(sb, "jvm_threads_live_threads", "gauge", sample("jvm_threads_live_threads", "", getThreadMXBean().getThreadCount()));
        files(sb, getOperatingSystemMXBean());
        processCpuUsage(sb);
        family(sb, "process_start_time_seconds", "gauge", sample("process_start_time_seconds", "", getRuntimeMXBean().getStartTime() / 1000.0));
        family(sb, "jvm_info", "gauge", sample("jvm_info", "{version=\"%s\",vendor=\"%s\",runtime=\"%s\"}".formatted(
                systemProperty("java.runtime.version"),
                systemProperty("java.vm.vendor"),
                systemProperty("java.runtime.name")), 1));
        return sb.toString();
    }

    private static void memory(final StringBuilder sb) {
        final var used = new StringBuilder();
        final var max = new StringBuilder();
        for (final var pool : getMemoryPoolMXBeans()) {
            final var usage = pool.getUsage();
            if (usage == null) // pool is no longer valid
                continue;
            final var labels = "{area=\"%s\",id=\"%s\"}".formatted(pool.getType() == HEAP ? "heap" : "nonheap", escapeLabelValue(pool.getName()));
            used.append(sample("jvm_memory_used_bytes", labels, usage.getUsed()));
            // -1 if the pool has no maximum, as in Micrometer
            max.append(sample("jvm_memory_max_bytes", labels, usage.getMax()));
        }
        family(sb, "jvm_memory_used_bytes", "gauge", used);
        family(sb, "jvm_memory_max_bytes", "gauge", max);
    }

    /**
     * Usage of the long-lived pools right after the most recent collection: if it keeps growing, objects survive
     * that should not.
     */
    private static void liveDataSize(final StringBuilder sb) {
        var found = false;
        var sum = 0L;
        for (final var pool : getMemoryPoolMXBeans()) {
            if (!isLongLivedPool(pool.getName()))
                continue;
            final var usage = pool.getCollectionUsage();
            if (usage == null)
                continue;
            found = true;
            sum += usage.getUsed();
        }
        if (found)
            family(sb, "jvm_gc_live_data_size_bytes", "gauge", sample("jvm_gc_live_data_size_bytes", "", sum));
    }

    private static void bufferPools(final StringBuilder sb) {
        final var used = new StringBuilder();
        for (final var pool : getPlatformMXBeans(BufferPoolMXBean.class)) {
            used.append(sample("jvm_buffer_memory_used_bytes", "{id=\"%s\"}".formatted(escapeLabelValue(pool.getName())), pool.getMemoryUsed()));
        }
        family(sb, "jvm_buffer_memory_used_bytes", "gauge", used);
    }

    private static void gcPauses(final StringBuilder sb) {
        final var samples = new StringBuilder();
        for (final var gc : getGarbageCollectorMXBeans()) {
            if (isConcurrentCollector(gc.getName()))
                continue;
            final var count = gc.getCollectionCount();
            final var timeMillis = gc.getCollectionTime();
            if (count < 0 || timeMillis < 0) // undefined for this collector
                continue;
            final var labels = "{gc=\"%s\"}".formatted(escapeLabelValue(gc.getName()));
            samples.append(sample("jvm_gc_pause_seconds_count", labels, count));
            samples.append(sample("jvm_gc_pause_seconds_sum", labels, timeMillis / 1000.0));
        }
        family(sb, "jvm_gc_pause_seconds", "summary", samples);
    }

    static void files(final StringBuilder sb, final OperatingSystemMXBean bean) {
        if (!(bean instanceof UnixOperatingSystemMXBean os)) // e.g. Windows
            return;
        fileDescriptors(sb, "process_files_open_files", os::getOpenFileDescriptorCount);
        fileDescriptors(sb, "process_files_max_files", os::getMaxFileDescriptorCount);
    }

    /**
     * The JDK throws an {@link InternalError} when it cannot read the count, e.g. on Linux when
     * <code>/proc/self/fd</code> cannot be opened because the process is out of file descriptors. An
     * <code>Error</code> is not caught by the request handling, so it would drop the whole scrape. The metric is left
     * out instead, as it is when the bean returns a negative count.
     */
    private static void fileDescriptors(final StringBuilder sb, final String name, final LongSupplier count) {
        final long value;
        try {
            value = count.getAsLong();
        } catch (InternalError e) {
            log.debug("Cannot read {}.", name, e);
            return;
        }
        if (value < 0) // not available, e.g. on OpenJ9
            return;
        family(sb, name, "gauge", sample(name, "", value));
    }

    private static void processCpuUsage(final StringBuilder sb) {
        if (!(getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os))
            return;
        final var load = os.getProcessCpuLoad();
        if (load < 0) // not available on this platform
            return;
        family(sb, "process_cpu_usage", "gauge", sample("process_cpu_usage", "", load));
    }

    /**
     * Long-lived (old generation) pools, matched by name like Micrometer's <code>JvmMemory.isLongLivedPool</code>.
     * Pool names per collector: G1 "G1 Old Gen", Parallel "PS Old Gen", Serial "Tenured Gen",
     * generational Shenandoah "Shenandoah Old Gen", Shenandoah "Shenandoah", ZGC "ZHeap",
     * generational ZGC "ZGC Old Generation", OpenJ9 "tenured-SOA"/"tenured-LOA"/"balanced-old".
     */
    static boolean isLongLivedPool(final String name) {
        return name.endsWith("Old Gen")
               || name.endsWith("Tenured Gen")
               || name.equals("Shenandoah")
               || name.equals("ZHeap")
               || name.equals("ZGC Old Generation")
               || name.endsWith("balanced-old")
               || name.contains("tenured")
               || name.equals("JavaHeap");
    }

    /**
     * Collector beans of concurrent collectors that report the duration of whole GC cycles, most of it spent
     * concurrently with the application, not pause time. Counting them as pauses would massively overstate the GC
     * overhead. Like Micrometer: "ZGC Cycles", "ZGC Minor Cycles", "ZGC Major Cycles", "Shenandoah Cycles" and Azul
     * C4's "GPGC" beans other than its "... Pauses".
     * <p>
     * "G1 Concurrent GC" is not one of them: it reports the stop-the-world Remark and Cleanup pauses. Micrometer
     * reports them as concurrent phase time, because their GC notifications carry the cause "No GC".
     */
    static boolean isConcurrentCollector(final String name) {
        return name.equals("Shenandoah Cycles")
               || (name.startsWith("ZGC") && name.endsWith("Cycles"))
               || (name.startsWith("GPGC") && !name.endsWith("Pauses"));
    }

    private static String systemProperty(final String key) {
        return escapeLabelValue(System.getProperty(key, "unknown"));
    }

    /**
     * Concatenation instead of <code>formatted()</code> on purpose: <code>Long.toString()</code> and
     * <code>Double.toString()</code> are locale independent, and their output, including exponents, is valid in the
     * format.
     */
    private static String sample(final String name, final String labels, final Number value) {
        return name + labels + " " + value + "\n";
    }

    private static void family(final StringBuilder sb, final String name, final String type, final CharSequence samples) {
        if (samples.isEmpty())
            return;
        sb.append("# TYPE ").append(name).append(' ').append(type).append('\n');
        sb.append(samples);
    }
}
