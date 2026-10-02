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
package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadingBodyExceptionTest {

    @Test
    void ioErrorsExplainThemselves() {
        assertTrue(new ReadingBodyException(new SocketException("Connection reset")).exceptionMessageIsSufficient());
        assertTrue(new ReadingBodyException(new SocketTimeoutException("Read timed out")).exceptionMessageIsSufficient());
        assertTrue(new ReadingBodyException(new EOFException("Stream ended after 7 of 1000 expected bytes")).exceptionMessageIsSufficient());
        assertTrue(new ReadingBodyException(new SSLException("Tag mismatch")).exceptionMessageIsSufficient());
        assertTrue(new ReadingBodyException(new IOException("Chunk-size exceeds limit")).exceptionMessageIsSufficient());
    }

    @Test
    void decodingFailureNamesTheCoding() {
        assertTrue(new ReadingBodyException(new DecodingException("gzip", new EOFException("Unexpected end of ZLIB input stream"))).exceptionMessageIsSufficient());
    }

    @Test
    void wrappedTwiceStillExplainsItself() {
        var inner = new ReadingBodyException(new SocketException("Connection reset"));
        assertTrue(new ReadingBodyException(inner).exceptionMessageIsSufficient());
    }

    @Test
    void failureWithoutCauseCarriesItsExplanationInTheMessage() {
        assertTrue(new ReadingBodyException("Stream closed").exceptionMessageIsSufficient());
    }

    @Test
    void nonIoCauseNeedsTheStackTrace() {
        assertFalse(new ReadingBodyException(new IllegalStateException("decoder bug")).exceptionMessageIsSufficient());
    }
}
