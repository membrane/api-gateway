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

package com.predic8.membrane.core.http.xml;

import com.predic8.membrane.core.http.CloseTrackingRequest;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLOutputFactory;
import java.io.StringWriter;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

class JSONBodyTest {

    @Test
    void copiesTheBody() throws Exception {
        assertTrue(write(new CloseTrackingRequest(APPLICATION_JSON, "{\"a\":1}")).contains("<a type=\"i\">1</a>"));
    }

    @Test
    void closesTheDecodedStreamWhenStoppingAtTheEndOfTheRootObject() throws Exception {
        // The reader leaves before the end of the input, so only an explicit close releases the decoder
        final var request = new CloseTrackingRequest(APPLICATION_JSON, "{\"a\":1} trailing");
        write(request);
        assertTrue(request.allStreamsClosed());
    }

    private static String write(CloseTrackingRequest request) throws Exception {
        final var out = new StringWriter();
        final var writer = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(out);
        writer.writeStartElement("body");
        new JSONBody(request).write(writer);
        writer.writeEndElement();
        writer.flush();
        return out.toString();
    }
}
