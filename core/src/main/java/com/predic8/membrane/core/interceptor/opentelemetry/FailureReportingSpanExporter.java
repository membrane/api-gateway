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

import io.opentelemetry.exporter.internal.FailedExportException;
import io.opentelemetry.exporter.internal.FailedExportException.GrpcExportException;
import io.opentelemetry.exporter.internal.FailedExportException.HttpExportException;
import io.opentelemetry.exporter.otlp.internal.GrpcExporter;
import io.opentelemetry.exporter.otlp.internal.HttpExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.function.LongSupplier;
import java.util.logging.Level;

import static com.predic8.membrane.core.util.ExceptionUtil.concatMessageAndCauseMessages;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Reports failed span exports as one short, throttled line instead of the OTel exporter's
 * per-attempt stack traces: a warning when exports start failing, a reminder every
 * {@link #REPORT_INTERVAL} while they keep failing, and an info line once they work again.
 * The stack trace of every failure is logged at debug.
 */
class FailureReportingSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(FailureReportingSpanExporter.class);

    static final Duration REPORT_INTERVAL = Duration.ofMinutes(5);

    private static final int MAX_BODY_BYTES = 1024;

    /**
     * The OTel exporters log every failed export with a stack trace through java.util.logging.
     * This class reports those failures instead, so their loggers are turned off. The fields keep
     * strong references: JUL holds loggers weakly, and a collected logger loses its level.
     */
    private static final java.util.logging.Logger OTEL_HTTP_EXPORTER_LOG = java.util.logging.Logger.getLogger(HttpExporter.class.getName());
    private static final java.util.logging.Logger OTEL_GRPC_EXPORTER_LOG = java.util.logging.Logger.getLogger(GrpcExporter.class.getName());

    private sealed interface State {}
    private record Healthy() implements State {}
    private record Failing(long lastReportNanos, long unreported) implements State {}

    private final SpanExporter delegate;
    private final String endpoint;
    private final LongSupplier nanoTime;

    private State state = new Healthy();

    FailureReportingSpanExporter(SpanExporter delegate, String endpoint) {
        this(delegate, endpoint, System::nanoTime);
    }

    FailureReportingSpanExporter(SpanExporter delegate, String endpoint, LongSupplier nanoTime) {
        this.delegate = delegate;
        this.endpoint = endpoint;
        this.nanoTime = nanoTime;
        OTEL_HTTP_EXPORTER_LOG.setLevel(Level.OFF);
        OTEL_GRPC_EXPORTER_LOG.setLevel(Level.OFF);
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        CompletableResultCode result = delegate.export(spans);
        // Completes on the exporter's own I/O thread.
        result.whenComplete(() -> {
            if (result.isSuccess())
                onSuccess();
            else
                onFailure(result.getFailureThrowable());
        });
        return result;
    }

    private void onSuccess() {
        boolean recovered;
        synchronized (this) {
            recovered = state instanceof Failing;
            state = new Healthy();
        }
        if (recovered)
            log.info("Exporting spans to OpenTelemetry collector at {} works again.", endpoint);
    }

    private void onFailure(@Nullable Throwable failure) {
        log.debug("Export failure details:", failure);

        long now = nanoTime.getAsLong();
        long unreported;
        synchronized (this) {
            switch (state) {
                case Healthy ignored -> unreported = 0;
                case Failing f when now - f.lastReportNanos() >= REPORT_INTERVAL.toNanos() -> unreported = f.unreported();
                case Failing f -> {
                    state = new Failing(f.lastReportNanos(), f.unreported() + 1);
                    return;
                }
            }
            state = new Failing(now, 0);
        }

        if (unreported == 0)
            log.warn("Cannot export spans to OpenTelemetry collector at {}: {}", endpoint, describe(failure));
        else
            log.warn("Cannot export spans to OpenTelemetry collector at {}: {} ({} more failed exports since the last report)", endpoint, describe(failure), unreported);
    }

    static String describe(@Nullable Throwable failure) {
        if (failure instanceof HttpExportException e && e.getResponse() != null)
            return "collector responded with HTTP status %d %s%s".formatted(e.getResponse().getStatusCode(), e.getResponse().getStatusMessage(), describeBody(e.getResponse().getResponseBody()));
        if (failure instanceof GrpcExportException e && e.getResponse() != null)
            return "collector responded with gRPC status %s %s".formatted(e.getResponse().getStatusCode(), e.getResponse().getStatusDescription());
        if (failure == null)
            return "unknown reason";
        // Without a response, the FailedExportException only wraps the I/O error
        Throwable cause = failure instanceof FailedExportException && failure.getCause() != null ? failure.getCause() : failure;
        String messages = concatMessageAndCauseMessages(cause);
        return messages.isBlank() ? cause.getClass().getName() : messages;
    }

    /**
     * The start of the collector's response body, which usually names the reason for a rejection.
     * Truncated, and control characters are replaced so the body cannot inject log lines.
     */
    private static String describeBody(byte @Nullable [] body) {
        if (body == null || body.length == 0)
            return "";
        final var text = new String(body, 0, Math.min(body.length, MAX_BODY_BYTES), UTF_8)
                .replaceAll("[\\p{Cntrl}\\uFFFD]+", " ").strip();
        if (text.isEmpty())
            return "";
        return " (response body: %s%s)".formatted(text, body.length > MAX_BODY_BYTES ? "..." : "");
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }
}
