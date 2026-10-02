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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.transport.http.ByteStreamLogging.wrapConnectionInputStream;
import static com.predic8.membrane.core.transport.http.ByteStreamLogging.wrapConnectionOutputStream;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteStreamLoggingTest {

    private Logger logger;
    private Level originalLevel;
    private TestAppender appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LogManager.getLogger(ByteStreamLogging.class.getName());
        originalLevel = logger.getLevel();
        // Logger.setLevel() alone is reset when addAppender() creates the logger's config
        Configurator.setLevel(logger.getName(), Level.TRACE);
        appender = new TestAppender("ByteStreamLoggingTest");
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.removeAppender(appender);
        appender.stop();
        Configurator.setLevel(logger.getName(), originalLevel);
    }

    @Test
    void inputStreamPassesBytesThroughAndLogsThem() throws IOException {
        InputStream in = wrapConnectionInputStream(new ByteArrayInputStream("GET".getBytes(US_ASCII)), "client=>membrane 1");

        assertEquals("GET", new String(in.readAllBytes(), US_ASCII));
        assertEquals(List.of("[client=>membrane 1] [ 71 69 84 ]" + System.lineSeparator() + "GET"), appender.getMessages());
    }

    @Test
    void singleByteReadIsLogged() throws IOException {
        InputStream in = wrapConnectionInputStream(new ByteArrayInputStream("G".getBytes(US_ASCII)), "client=>membrane 1");

        assertEquals('G', in.read());
        assertEquals(-1, in.read());
        assertEquals(List.of("[client=>membrane 1] [ 71 ]" + System.lineSeparator() + "G"), appender.getMessages());
    }

    @Test
    void inputStreamDelegatesMarkAndReset() throws IOException {
        InputStream in = wrapConnectionInputStream(new ByteArrayInputStream("abc".getBytes(US_ASCII)), "client=>membrane 1");

        assertTrue(in.markSupported());
        in.mark(10);
        assertEquals('a', in.read());
        in.reset();
        assertEquals('a', in.read());
    }

    @Test
    void outputStreamPassesBytesThroughAndLogsThem() throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OutputStream out = wrapConnectionOutputStream(target, "membrane=>backend 1");

        out.write('G');
        out.write("ET".getBytes(US_ASCII));

        assertEquals("GET", target.toString(US_ASCII));
        assertEquals(List.of(
                "[membrane=>backend 1] [ 71 ]" + System.lineSeparator() + "G",
                "[membrane=>backend 1] [ 69 84 ]" + System.lineSeparator() + "ET"), appender.getMessages());
    }

    /**
     * Writing an array range must reach the socket stream as one write, not byte by byte.
     */
    @Test
    void arrayRangeIsWrittenInOneCall() throws IOException {
        List<Integer> writeLengths = new ArrayList<>();
        OutputStream target = new OutputStream() {
            @Override
            public void write(int b) {
                writeLengths.add(1);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                writeLengths.add(len);
            }
        };

        wrapConnectionOutputStream(target, "membrane=>backend 1").write("xGETx".getBytes(US_ASCII), 1, 3);

        assertEquals(List.of(3), writeLengths);
        assertEquals(List.of("[membrane=>backend 1] [ 71 69 84 ]" + System.lineSeparator() + "GET"), appender.getMessages());
    }
}
