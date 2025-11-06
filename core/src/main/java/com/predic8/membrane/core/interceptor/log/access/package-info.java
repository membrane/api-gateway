/**
 * Keep logs isolated.
 *
 * Interceptor ({@code .log}): diagnostics/config.
 * Service ({@code .log.access}): emits the MDC-based access line.
 *
 * Separate packages → distinct logger categories and destinations:
 * interceptor diagnostics never land in the access log.
 */
package com.predic8.membrane.core.interceptor.log.access;
