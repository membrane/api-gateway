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
package com.predic8.membrane.core.util.xml;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.text.UnicodeUtil;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

import static com.predic8.membrane.core.util.text.TextUtil.getCharset;

public class XMLInputSourceUtil {

    private static final int BOM_PREFIX_LENGTH = 3;

    /**
     * For XML processing sometimes an InputSource is needed.
     * Passes the body as a byte stream so the parser can determine the encoding
     * itself (from the message's charset or, failing that, a byte order mark or the XML
     * declaration) instead of it being pre-decoded with the JVM default charset.
     * @param msg Message with body
     * @return InputSource of the message body
     */
    public static @NotNull InputSource getInputSource(Message msg) {
        return getInputSource(msg.getBodyAsStreamDecoded(), msg.getHeader().getCharset());
    }

    /**
     * Builds an {@link InputSource} from a byte stream, honoring a byte order mark ahead of a
     * declared charset. RFC 7303 makes the Content-Type charset authoritative over the document's
     * own XML declaration for XML media types — but not over a BOM, which identifies the
     * byte-stream encoding more directly than either. When {@code body} opens with a UTF-8 or
     * UTF-16 BOM, the source's encoding is left unset so the parser detects it from the BOM
     * itself; otherwise {@code charsetName} (if non-null and resolvable) is applied as before.
     *
     * @param body        body bytes to parse; the BOM peek does not consume them from the parser's
     *                    point of view — it still sees the full stream, BOM included
     * @param charsetName the declared charset (e.g. from Content-Type), or null if none
     */
    public static InputSource getInputSource(InputStream body, String charsetName) {
        PushbackInputStream pushbackBody = new PushbackInputStream(body, BOM_PREFIX_LENGTH);
        byte[] prefix = new byte[BOM_PREFIX_LENGTH];
        try {
            int prefixLength = Math.max(pushbackBody.read(prefix), 0);
            if (prefixLength > 0) {
                pushbackBody.unread(prefix, 0, prefixLength);
            }

            InputSource source = new InputSource(pushbackBody);
            if (!UnicodeUtil.startsWithByteOrderMark(prefix, prefixLength)) {
                Charset charset = getCharset(charsetName, null);
                if (charset != null) {
                    source.setEncoding(charset.name());
                }
            }
            return source;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
