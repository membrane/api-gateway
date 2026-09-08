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

package com.predic8.membrane.core.multipart;

import com.google.common.primitives.Bytes;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.MessageUtil;
import jakarta.mail.internet.ParseException;
import org.apache.commons.fileupload.MultipartStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static com.predic8.membrane.core.multipart.MultipartUtil.boundaryOf;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Rebuilds a multipart body with the content of some parts replaced, for a caller that does not only
 * inspect a message but rewrites what it found - as {@code xmlProtection} does when it strips a DTD.
 * The inverse of {@link PartScanner#forEachPart}, which reads parts without being able to put one
 * back.
 *
 * <p>Parts that are not replaced are copied through byte for byte, headers included: nothing is
 * parsed on the way, so a header carrying raw UTF-8 (an RFC 7578 {@code filename}, say) or a folded
 * continuation line survives unchanged. The preamble and the epilogue are dropped, as no reader in
 * Membrane looks at either.</p>
 *
 * <p>Only call this when something actually has to be replaced. A message no part was rewritten in
 * should reach the backend as the client sent it, rather than as a reassembled copy.</p>
 */
public class PartRewriter {

    private static final byte[] CRLF = "\r\n".getBytes(ISO_8859_1);

    /**
     * Reassembles the message's multipart body, replacing the content of the parts named by their
     * position in the body.
     *
     * @param message      a message with a multipart body, positioned so that it can be read again
     * @param replacements the new content per part index, numbered as {@link PartScanner} numbers
     *                     the parts it hands out
     * @return the rebuilt body, for {@link Message#setBodyContent(byte[])}
     * @throws IOException    on I/O or parse errors, or if a replacement carries the message's own
     *                        MIME boundary, see {@link #rejectIfItCarriesTheBoundary}
     * @throws ParseException if the Content-Type header cannot be parsed
     */
    public static byte[] rebuild(Message message, Map<Integer, byte[]> replacements) throws IOException, ParseException {
        var boundary = boundaryOf(message);
        var rebuilt = new ByteArrayOutputStream();
        var startOfPart = startOfPart(boundary);
        var delimiter = delimiter(boundary);

        var ms = multipartStreamOver(message, boundary);
        var hasNext = ms.skipPreamble();
        var index = -1;
        while (hasNext) {
            var headers = ms.readHeaders(); // verbatim, including the blank line that ends the block
            index++;

            rebuilt.write(startOfPart);
            rebuilt.write(headers.getBytes(ISO_8859_1));
            writeBody(ms, rebuilt, replacements.get(index), delimiter, index);
            rebuilt.write(CRLF); // readBodyData stops before the CRLF that precedes the next delimiter

            hasNext = ms.readBoundary();
        }
        rebuilt.write(endOfBody(boundary));
        return rebuilt.toByteArray();
    }

    @SuppressWarnings("deprecation")
    private static @NotNull MultipartStream multipartStreamOver(Message message, String boundary) {
        var ms = new MultipartStream(MessageUtil.getContentAsStream(message), boundary.getBytes(UTF_8));
        // ISO-8859-1 maps every byte to exactly one char, so the header block written back out is
        // byte for byte the one that was read.
        ms.setHeaderEncoding(ISO_8859_1.name());
        return ms;
    }

    /** The delimiter line that opens a part, RFC 2046's {@code dash-boundary} followed by a CRLF. */
    private static byte @NotNull [] startOfPart(String boundary) {
        return ("--" + boundary + "\r\n").getBytes(ISO_8859_1);
    }

    /** The close-delimiter line that ends the last part, and with it the body. */
    private static byte @NotNull [] endOfBody(String boundary) {
        return ("--" + boundary + "--\r\n").getBytes(ISO_8859_1);
    }

    /**
     * RFC 2046's {@code delimiter}: what a parser scans for to find where a part ends. The leading
     * CRLF belongs to it, and is supplied by whatever precedes the delimiter rather than by the
     * delimiter line itself - which is why {@link #startOfPart} does not carry it.
     */
    private static byte @NotNull [] delimiter(String boundary) {
        return ("\r\n--" + boundary).getBytes(ISO_8859_1);
    }

    private static void writeBody(MultipartStream ms, ByteArrayOutputStream rebuilt, byte[] replacement,
                                  byte[] delimiter, int index) throws IOException {
        if (replacement == null) {
            ms.readBodyData(rebuilt);
            return;
        }
        ms.discardBodyData();
        rejectIfItCarriesTheBoundary(replacement, delimiter, index);
        rebuilt.write(replacement);
    }

    /**
     * A replacement that contains the message's own delimiter would split the rebuilt body somewhere
     * the sender never put a boundary, smuggling parts past the plugin that just inspected them. The
     * content it was made from cannot: the same bytes would have ended that part while it was being
     * read. So the check belongs on what a caller puts in, not on what came out of the message.
     *
     * <p>The delimiter is {@code CRLF--boundary}, and the CRLF that closes the part's header block
     * supplies those first two bytes - which is why a replacement <i>starting</i> with
     * {@code --boundary} is as dangerous as one carrying the full sequence.</p>
     */
    private static void rejectIfItCarriesTheBoundary(byte[] replacement, byte[] delimiter, int index) throws IOException {
        if (Bytes.indexOf(Bytes.concat(CRLF, replacement), delimiter) < 0)
            return;
        throw new IOException(("The rewritten content of part %d contains the MIME boundary of the "
                + "message and cannot be put back into it.").formatted(index));
    }
}
