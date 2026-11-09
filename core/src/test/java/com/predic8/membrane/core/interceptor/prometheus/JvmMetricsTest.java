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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JvmMetricsTest {

    private static final Pattern TYPE_LINE = Pattern.compile("# TYPE (\\w+) (gauge|counter|summary)");
    private static final Pattern SAMPLE_LINE = Pattern.compile("(\\w+)(\\{.*})? (\\S+)");

    @Test
    void allMetricsPresent() {
        final var body = JvmMetrics.collect();
        for (final var family : new String[]{
                "jvm_memory_used_bytes gauge",
                "jvm_memory_max_bytes gauge",
                "jvm_gc_live_data_size_bytes gauge",
                "jvm_buffer_memory_used_bytes gauge",
                "jvm_gc_pause_seconds summary",
                "jvm_threads_live_threads gauge",
                "process_start_time_seconds gauge",
                "jvm_info gauge"}) {
            assertTrue(body.contains("# TYPE %s\n".formatted(family)), family + " missing in:\n" + body);
        }
        assertEquals(getOperatingSystemMXBean() instanceof UnixOperatingSystemMXBean,
                body.contains("# TYPE process_files_open_files gauge\n") && body.contains("# TYPE process_files_max_files gauge\n"), body);
        // process_cpu_usage is omitted when the JVM cannot measure it, so only its format is checked.
        assertTrue(body.contains("jvm_memory_used_bytes{area=\"heap\",id=\""), body);
        assertTrue(body.contains("jvm_memory_used_bytes{area=\"nonheap\",id=\"Metaspace\"}"), body);
        assertTrue(body.contains("jvm_buffer_memory_used_bytes{id=\"direct\"}"), body);
        assertTrue(body.contains("jvm_gc_pause_seconds_count{gc=\""), body);
        assertTrue(body.contains("jvm_gc_pause_seconds_sum{gc=\""), body);
        assertTrue(body.contains("jvm_info{version=\"%s\",".formatted(System.getProperty("java.runtime.version"))), body);
    }

    @Test
    void validExpositionFormat() {
        final var seenFamilies = new HashSet<String>();
        final var seenSeries = new HashSet<String>();
        final var familyTypes = new HashMap<String, String>();
        String family = null;
        for (final var line : JvmMetrics.collect().lines().toList()) {
            final var type = TYPE_LINE.matcher(line);
            if (type.matches()) {
                family = type.group(1);
                assertTrue(seenFamilies.add(family), "Second # TYPE for " + family);
                familyTypes.put(family, type.group(2));
                continue;
            }
            final var sample = SAMPLE_LINE.matcher(line);
            assertTrue(sample.matches(), "Not a sample: " + line);
            assertNotNull(family, "Sample before # TYPE: " + line);
            assertTrue(belongsTo(sample.group(1), family, familyTypes.get(family)), "%s outside of its family %s".formatted(line, family));
            assertTrue(seenSeries.add(sample.group(1) + sample.group(2)), "Duplicate series: " + line);
            assertDoesNotThrow(() -> Double.parseDouble(sample.group(3)), line);
        }
    }

    /**
     * On Linux the JDK opens <code>/proc/self/fd</code> to count the descriptors and throws an {@link InternalError}
     * when that fails, e.g. because the process is out of file descriptors.
     */
    @Test
    void unreadableFileDescriptorCountIsLeftOut() {
        final var os = mock(UnixOperatingSystemMXBean.class);
        when(os.getOpenFileDescriptorCount()).thenThrow(new InternalError("Unable to open directory /proc/self/fd"));
        when(os.getMaxFileDescriptorCount()).thenReturn(1024L);
        final var sb = new StringBuilder();
        JvmMetrics.files(sb, os);
        assertEquals("# TYPE process_files_max_files gauge\nprocess_files_max_files 1024\n", sb.toString());
    }

    @Test
    void unavailableFileDescriptorCountIsLeftOut() {
        final var os = mock(UnixOperatingSystemMXBean.class);
        when(os.getOpenFileDescriptorCount()).thenReturn(10L);
        when(os.getMaxFileDescriptorCount()).thenReturn(-1L);
        final var sb = new StringBuilder();
        JvmMetrics.files(sb, os);
        assertEquals("# TYPE process_files_open_files gauge\nprocess_files_open_files 10\n", sb.toString());
    }

    private static boolean belongsTo(String metric, String family, String type) {
        if (type.equals("summary"))
            return metric.equals(family + "_count") || metric.equals(family + "_sum");
        return metric.equals(family);
    }

    @ParameterizedTest
    @ValueSource(strings = {"G1 Old Gen", "PS Old Gen", "Tenured Gen", "Shenandoah", "Shenandoah Old Gen", "ZHeap", "ZGC Old Generation", "tenured-SOA"})
    void longLivedPools(String name) {
        assertTrue(JvmMetrics.isLongLivedPool(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"G1 Eden Space", "G1 Survivor Space", "PS Eden Space", "Shenandoah Young Gen", "ZGC Young Generation", "Metaspace", "Epsilon Heap"})
    void shortLivedPools(String name) {
        assertFalse(JvmMetrics.isLongLivedPool(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZGC Cycles", "ZGC Minor Cycles", "ZGC Major Cycles", "Shenandoah Cycles", "GPGC New", "GPGC Old"})
    void concurrentCollectors(String name) {
        assertTrue(JvmMetrics.isConcurrentCollector(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"G1 Young Generation", "G1 Old Generation", "G1 Concurrent GC", "PS Scavenge", "PS MarkSweep", "Copy", "MarkSweepCompact",
            "ZGC Pauses", "ZGC Minor Pauses", "ZGC Major Pauses", "Shenandoah Pauses", "GPGC New Pauses"})
    void pausingCollectors(String name) {
        assertFalse(JvmMetrics.isConcurrentCollector(name));
    }
}
