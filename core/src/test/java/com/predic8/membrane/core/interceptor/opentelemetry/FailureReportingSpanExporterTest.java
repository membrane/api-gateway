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
package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.test.TestAppender;
import io.opentelemetry.exporter.otlp.internal.GrpcExporter;
import io.opentelemetry.exporter.otlp.internal.HttpExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.export.GrpcResponse;
import io.opentelemetry.sdk.common.export.GrpcStatusCode;
import io.opentelemetry.sdk.common.export.HttpResponse;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.predic8.membrane.core.interceptor.opentelemetry.FailureReportingSpanExporter.REPORT_INTERVAL;
import static io.opentelemetry.exporter.internal.FailedExportException.*;
import static io.opentelemetry.sdk.common.CompletableResultCode.ofExceptionalFailure;
import static io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureReportingSpanExporterTest {

    private static final String ENDPOINT = "http://collector.example.com:4317";

    private final AtomicLong nanos = new AtomicLong();
    private org.apache.logging.log4j.core.Logger logger;
    private TestAppender appender;

    @BeforeEach
    void attachAppender() {
        logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(FailureReportingSpanExporter.class.getName());
        appender = new TestAppender("FailureReportingSpanExporter");
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void repeatedConnectFailuresAreReportedOnce() {
        var exporter = exporter(() -> ofExceptionalFailure(httpFailedExceptionally(
                new ConnectException("Failed to connect to collector.example.com"))));

        for (int i = 0; i < 10; i++)
            exporter.export(List.of());

        assertEquals(List.of("Cannot export spans to OpenTelemetry collector at " + ENDPOINT + ": Failed to connect to collector.example.com"),
                warnings());
    }

    @Test
    void stillFailingIsReportedAgainAfterInterval() {
        var exporter = exporter(() -> ofExceptionalFailure(grpcFailedExceptionally(new ConnectException("Connection refused"))));

        exporter.export(List.of());
        exporter.export(List.of());
        exporter.export(List.of());
        nanos.addAndGet(REPORT_INTERVAL.toNanos());
        exporter.export(List.of());

        assertEquals(2, warnings().size());
        assertTrue(warnings().get(1).endsWith("(2 more failed exports since the last report)"), warnings().get(1));
    }

    @Test
    void recoveryIsReportedAndResetsThrottle() {
        var fail = new boolean[]{true};
        var exporter = exporter(() -> fail[0] ? ofExceptionalFailure(httpFailedExceptionally(new ConnectException("down"))) : ofSuccess());

        exporter.export(List.of());
        fail[0] = false;
        exporter.export(List.of());
        exporter.export(List.of());
        fail[0] = true;
        exporter.export(List.of());

        assertEquals(List.of(
                "Cannot export spans to OpenTelemetry collector at " + ENDPOINT + ": down",
                "Exporting spans to OpenTelemetry collector at " + ENDPOINT + " works again.",
                "Cannot export spans to OpenTelemetry collector at " + ENDPOINT + ": down"), warnings());
    }

    @Test
    void httpStatusIsReported() {
        var exporter = exporter(() -> ofExceptionalFailure(httpFailedWithResponse(new HttpResponse() {
            public int getStatusCode() { return 401; }
            public String getStatusMessage() { return "Unauthorized"; }
            public byte[] getResponseBody() { return new byte[0]; }
        })));

        exporter.export(List.of());

        assertEquals(List.of("Cannot export spans to OpenTelemetry collector at " + ENDPOINT + ": collector responded with HTTP status 401 Unauthorized"),
                warnings());
    }

    @Test
    void grpcStatusIsReported() {
        var exporter = exporter(() -> ofExceptionalFailure(grpcFailedWithResponse(new GrpcResponse() {
            public GrpcStatusCode getStatusCode() { return GrpcStatusCode.UNIMPLEMENTED; }
            public String getStatusDescription() { return "unknown service"; }
            public byte[] getResponseMessage() { return new byte[0]; }
        })));

        exporter.export(List.of());

        assertEquals(List.of("Cannot export spans to OpenTelemetry collector at " + ENDPOINT + ": collector responded with gRPC status UNIMPLEMENTED unknown service"),
                warnings());
    }

    @Test
    void failureWithoutMessageIsDescribedByType() {
        assertEquals("java.net.SocketTimeoutException",
                FailureReportingSpanExporter.describe(httpFailedExceptionally(new java.net.SocketTimeoutException())));
    }

    @Test
    void otelExporterLoggersAreSilenced() {
        exporter(CompletableResultCode::ofSuccess);

        assertEquals(Level.OFF, Logger.getLogger(HttpExporter.class.getName()).getLevel());
        assertEquals(Level.OFF, Logger.getLogger(GrpcExporter.class.getName()).getLevel());
    }

    /**
     * TestAppender records every level; debug lines (the stack traces) are filtered out by
     * the "Cannot"/"works again" prefixes.
     */
    private List<String> warnings() {
        return appender.getMessages().stream()
                .filter(m -> m.startsWith("Cannot export") || m.startsWith("Exporting spans"))
                .toList();
    }

    private FailureReportingSpanExporter exporter(Supplier<CompletableResultCode> result) {
        return new FailureReportingSpanExporter(new SpanExporter() {
            public CompletableResultCode export(Collection<SpanData> spans) { return result.get(); }
            public CompletableResultCode flush() { return ofSuccess(); }
            public CompletableResultCode shutdown() { return ofSuccess(); }
        }, ENDPOINT, nanos::get);
    }
}
