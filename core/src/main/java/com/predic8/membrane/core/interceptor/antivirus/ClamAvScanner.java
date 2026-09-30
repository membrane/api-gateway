/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.antivirus;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.multipart.MultipartUtil;
import com.predic8.membrane.core.util.MessageUtil;
import fi.solita.clamav.ClamAVClient;
import jakarta.mail.internet.ParseException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.core.interceptor.antivirus.ScanResult.CLEAN;
import static com.predic8.membrane.core.interceptor.antivirus.ScanResult.INFECTED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * {@link ContentScanner} that streams message content to a ClamAV daemon (clamd) via INSTREAM.
 * <p>
 * The headers are always scanned. A non-empty body is scanned after removing any
 * {@code Content-Encoding}. A multipart body is scanned twice: once as a whole, so preambles,
 * epilogues and part headers are covered, and once per leaf part after decoding its
 * {@code Content-Transfer-Encoding}. Nested multiparts are descended into.
 * <p>
 * Multipart content is validated before ClamAV is contacted, so malformed content is rejected
 * with {@link InvalidScanContentException} even when the scanner is unavailable.
 * <p>
 * Thread safe: the only state is the {@link ClamAVClient}, which opens a new connection per scan.
 */
public final class ClamAvScanner implements ContentScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvScanner.class);
    /** Maximum size of a single encoded multipart part, in bytes. */
    private static final int MAX_PART_SIZE = 100 * 1024 * 1024;

    private final ClamAVClient client;

    /**
     * @param client connection to the ClamAV daemon
     */
    public ClamAvScanner(ClamAVClient client) {
        this.client = requireNonNull(client);
    }

    /**
     * Scans headers first, then the body; stops at the first detection.
     *
     * @return {@link ScanResult#INFECTED} if ClamAV reports a signature, otherwise {@link ScanResult#CLEAN}
     * @throws InvalidScanContentException if the Content-Type or multipart structure is invalid,
     *         an encoded part exceeds 100 MiB or uses an unsupported transfer encoding
     * @throws IOException if ClamAV cannot be reached or returns an error reply
     */
    @Override
    public ScanResult scan(Message message) throws IOException {
        boolean bodyEmpty = message.isBodyEmpty();
        boolean multipart = bodyEmpty ? false : validateContent(message);
        log.debug("Scanning message headers with ClamAV");
        if (!isNotMalicious(IOUtils.toInputStream(message.getHeader().toString(), UTF_8))) {
            log.info("Harmful content detected in message headers; skipping body scan");
            return INFECTED;
        }

        if (bodyEmpty) {
            log.debug("Body is empty; skipping body scan");
            return CLEAN;
        }

        try {
            boolean clean;
            if (multipart) {
                // Scan the complete MIME representation so preambles, epilogues, and part headers
                // are covered too. Then scan decoded leaf parts to catch transfer-encoded content.
                log.debug("Scanning complete multipart body with ClamAV");
                clean = isNotMalicious(MessageUtil.getContentAsStream(message));
                if (clean) {
                    log.debug("Scanning decoded multipart leaf parts with ClamAV; maximum encoded part size is {} bytes", MAX_PART_SIZE);
                    clean = MultipartUtil.allDecodedPartsMatch(message, MAX_PART_SIZE, part -> {
                        log.debug("Scanning decoded multipart leaf part: {} bytes", part.getBody().length);
                        return isNotMalicious(part.getInputStream());
                    });
                }
            } else {
                log.debug("Scanning content-decoded message body with ClamAV");
                clean = isNotMalicious(MessageUtil.getContentAsStream(message));
            }
            ScanResult result = clean ? CLEAN : INFECTED;
            log.debug("ClamAV message scan completed: {}", result);
            return result;
        } catch (ParseException e) {
            throw new InvalidScanContentException("Invalid Content-Type for virus scan", e);
        }
    }

    /**
     * Fully decodes every multipart part without scanning it.
     *
     * @return whether the body is multipart
     * @throws InvalidScanContentException if the content cannot be parsed or decoded
     */
    private boolean validateContent(Message message) throws InvalidScanContentException {
        try {
            boolean multipart = MultipartUtil.isMultipart(message);
            if (multipart) {
                // Validate every part before contacting ClamAV: an unavailable scanner must not
                // allow malformed content through when onScanFailure is pass.
                log.debug("Validating multipart structure and transfer encodings before scanning");
                MultipartUtil.allDecodedPartsMatch(message, MAX_PART_SIZE, part -> true);
            }
            return multipart;
        } catch (IOException | ParseException | RuntimeException e) {
            throw new InvalidScanContentException("Invalid content for virus scan", e);
        }
    }

    /**
     * Sends the stream to ClamAV and closes it.
     *
     * @return {@code true} for an {@code OK} reply, {@code false} for a {@code FOUND} reply
     * @throws IOException for any other reply, e.g. an error or size-limit message
     */
    private boolean isNotMalicious(InputStream input) throws IOException {
        try (input) {
            byte[] reply = client.scan(input);
            if (ClamAVClient.isCleanReply(reply)) {
                log.debug("ClamAV stream scan completed: clean");
                return true;
            }
            String result = new String(reply, UTF_8).trim();
            if (!result.endsWith(" FOUND")) {
                log.warn("ClamAV returned an error or unexpected reply; scan could not complete");
                throw new IOException("ClamAV scan failed: " + result);
            }
            log.warn("ClamAV detected malicious content: {}", result);
            return false;
        }
    }
}
