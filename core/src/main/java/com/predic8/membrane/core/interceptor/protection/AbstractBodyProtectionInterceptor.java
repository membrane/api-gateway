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

package com.predic8.membrane.core.interceptor.protection;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.multipart.PartScanner.PartAction;
import jakarta.mail.internet.ParseException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.protection.AbstractBodyProtectionInterceptor.OtherContentTypes.SKIP;

/**
 * Applies a document-level check to every document a message carries: the whole body, or - when the
 * body is multipart - each part in turn. Subclasses supply the check; everything around it, from
 * deciding what is worth inspecting to putting a rewritten document back into the message, lives
 * here.
 *
 * <p>A multipart body is traversed with {@link PartScanner}, so a part the subclass does not want is
 * decided from its header and never buffered. When the check rewrites a document - as
 * {@code xmlProtection} does when it strips a DTD - the body is reassembled from the parts, and only
 * then: a message nothing was taken out of reaches the backend exactly as the client sent it.</p>
 *
 * @see com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionInterceptor
 * @see com.predic8.membrane.core.interceptor.json.JsonProtectionInterceptor
 */
public abstract class AbstractBodyProtectionInterceptor extends AbstractInterceptor {

    /**
     * What to do with content the plugin does not inspect: the whole body of a message of another
     * type, or such a part of a multipart body.
     */
    public enum OtherContentTypes {
        REJECT, SKIP
    }

    /**
     * The outcome of checking one document, and the document to forward in its place when the check
     * rewrote it.
     */
    public record Inspection(Outcome outcome, byte @Nullable [] rewritten) {

        /** The document is within the policy and may be forwarded as it arrived. */
        public static Inspection passed() {
            return new Inspection(CONTINUE, null);
        }

        /** The document may be forwarded, but only as {@code document}. */
        public static Inspection rewritten(byte[] document) {
            return new Inspection(CONTINUE, document);
        }

        /** The document violates the policy; the error response has already been set. */
        public static Inspection failed(Outcome outcome) {
            return new Inspection(outcome, null);
        }
    }

    private OtherContentTypes otherContentTypes = OtherContentTypes.REJECT;

    /**
     * Checks every document of the message, and forwards what the check made of them.
     *
     * @return {@link Outcome#CONTINUE}, or whatever the subclass returned from the first violation
     */
    protected final Outcome protect(Exchange exc, Message message) throws ParseException {
        if (message.isBodyEmpty())
            return CONTINUE;

        // A plain body is inspected straight off the body stream, without buffering it into a byte[].
        if (!MultipartUtil.isMultipart(message))
            return inspectBody(exc, message);

        return inspectParts(exc, message);
    }

    private Outcome inspectBody(Exchange exc, Message message) {
        Origin origin = Origin.body(message.getHeader());
        if (!holdsInspectableContent(origin))
            return skipOrReject(exc, origin);

        Inspection inspection = inspectDocument(exc, message::getBodyAsStreamDecoded, origin);
        if (inspection.rewritten() != null)
            message.setBodyContent(inspection.rewritten());
        return inspection.outcome();
    }

    /**
     * Inspects the parts one by one and stops at the first that is not accepted, so nothing after it
     * is read. The body is only reassembled if the check actually rewrote a part.
     */
    private Outcome inspectParts(Exchange exc, Message message) {
        DocumentPartHandler handler = new DocumentPartHandler(exc);
        try {
            PartScanner.forEachPart(message, maxDocumentSize(), handler);
        } catch (PartTooLargeException e) {
            // Reported like any other part-level violation, naming the attachment that was too big.
            return rejectUnprocessableBody(exc, Origin.part(e.getPartHeader(), handler.index), e.getMessage());
        } catch (IOException | ParseException e) {
            return rejectUnprocessableBody(exc, Origin.body(message.getHeader()), e.getMessage());
        }
        if (handler.outcome != CONTINUE)
            return handler.outcome;

        if (handler.rewrites.isEmpty())
            return CONTINUE;

        try {
            message.setBodyContent(PartRewriter.rebuild(message, handler.rewrites));
        } catch (IOException | ParseException e) {
            return rejectUnprocessableBody(exc, Origin.body(message.getHeader()), e.getMessage());
        }
        return CONTINUE;
    }

    private Outcome skipOrReject(Exchange exc, Origin origin) {
        if (otherContentTypes == SKIP)
            return CONTINUE;
        return rejectOtherContentType(exc, origin);
    }

    /**
     * Runs the check over the inspectable parts, remembering the first part that was not accepted and
     * every part the check rewrote.
     */
    private class DocumentPartHandler implements PartScanner.PartHandler {

        private final Exchange exc;
        private final Map<Integer, byte[]> rewrites = new LinkedHashMap<>();
        private Outcome outcome = CONTINUE;

        /** Position of the part the scanner is at; counted in decide(), which sees every part. */
        private int index = -1;

        private DocumentPartHandler(Exchange exc) {
            this.exc = exc;
        }

        @Override
        public PartAction decide(Header partHeader) {
            if (outcome != CONTINUE)
                return PartAction.STOP;

            index++;
            Origin origin = Origin.part(partHeader, index);
            if (holdsInspectableContent(origin))
                return PartAction.INSPECT;
            if (otherContentTypes == SKIP)
                return PartAction.SKIP;

            // Rejecting needs the header only, so the offending body is never buffered.
            outcome = rejectOtherContentType(exc, origin);
            return PartAction.STOP;
        }

        @Override
        public void handle(Part part) {
            Inspection inspection = inspectDocument(exc, part::getInputStream, Origin.part(part.getHeader(), index));
            outcome = inspection.outcome();
            if (inspection.rewritten() != null)
                rewrites.put(index, inspection.rewritten());
        }
    }

    /**
     * Whether the document at this origin is one to inspect. A whole body without a Content-Type is
     * inspected anyway - a client that posts a document without declaring it must keep being checked
     * - while a MIME part without one defaults to {@code text/plain} per RFC 2045 and is therefore
     * not of this plugin's type. Override to decide an absent Content-Type differently.
     */
    protected boolean holdsInspectableContent(Origin origin) {
        String contentType = origin.contentType();
        if (contentType == null)
            return !origin.isPart();
        return inspects(contentType);
    }

    /**
     * @return whether this plugin inspects documents of that content type
     */
    protected abstract boolean inspects(String contentType);

    /**
     * Checks one document: the whole body of a message, or one part of a multipart body. An
     * implementation that rejects the document sets the error response itself and reports it through
     * {@link Inspection#failed}.
     *
     * @param document opens the document, and opens it again for an implementation that has to read
     *                 it more than once - to probe an encoding declaration before parsing, say
     */
    protected abstract Inspection inspectDocument(Exchange exc, Supplier<InputStream> document, Origin origin);

    /**
     * Responds to a document this plugin does not inspect, while {@code otherContentTypes} is
     * {@code REJECT}.
     */
    protected abstract Outcome rejectOtherContentType(Exchange exc, Origin origin);

    /**
     * Responds to a multipart body that could not be traversed or reassembled: a part above
     * {@link #maxDocumentSize()}, a nested multipart, an unsupported Content-Transfer-Encoding, or a
     * rewritten part that cannot be put back safely.
     */
    protected abstract Outcome rejectUnprocessableBody(Exchange exc, Origin origin, String reason);

    /**
     * @return the largest single document this plugin buffers out of a multipart body
     */
    protected abstract int maxDocumentSize();

    public OtherContentTypes getOtherContentTypes() {
        return otherContentTypes;
    }

    /**
     * @description What to do with content this plugin does not inspect. This applies both to the
     * body of a request of another type and to the individual parts of a multipart body, so
     * <code>skip</code> allows e.g. an image to be uploaded alongside the document that is checked.
     * <p>Values: REJECT, SKIP</p>
     * @default REJECT
     * @example SKIP
     */
    @MCAttribute
    public void setOtherContentTypes(OtherContentTypes otherContentTypes) {
        this.otherContentTypes = otherContentTypes;
    }
}
