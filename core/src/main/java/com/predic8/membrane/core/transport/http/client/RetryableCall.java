package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.exchange.*;

@FunctionalInterface
public interface RetryableCall {
    /**
     * Executes the HTTP exchange for a given retry attempt and target destination.
     *
     * @param exc    the exchange being processed
     * @param dest the destination for this attempt
     * @param attempt the attempt counter (starting at 0)
     * @return true if the request was successful and no retry is needed
     * @throws Exception to signal retryable or fatal failure
     */
    boolean execute(Exchange exc, String dest, int attempt) throws Exception;
}

