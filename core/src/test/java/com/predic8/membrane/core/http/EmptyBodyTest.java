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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class EmptyBodyTest {

    @Test
    void isReadFromTheStart() {
        final var body = new EmptyBody();

        assertTrue(body.isRead());
        assertFalse(body.hasFailed());
        assertTrue(body.isContentRetained());
    }

    @Test
    void observerIsCompletedOnceWhenAddedAndNotAgainOnWrite() {
        final var body = new EmptyBody();
        final var events = new ArrayList<String>();
        body.addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody completed) {
                events.add("complete");
            }
        });

        body.write(new PlainBodyTransferer(new ByteArrayOutputStream()), false);

        assertEquals(1, events.size());
    }

    @Test
    void discardAndReadDoNothing() {
        final var body = new EmptyBody();

        assertDoesNotThrow(body::discard);
        assertDoesNotThrow(body::read);
        assertEquals(0, body.getContent().length);
    }
}
