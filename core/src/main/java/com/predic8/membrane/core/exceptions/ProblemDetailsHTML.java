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

package com.predic8.membrane.core.exceptions;

import com.predic8.membrane.core.http.Response;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static com.predic8.membrane.core.exceptions.ProblemDetails.DETAIL;
import static com.predic8.membrane.core.exceptions.ProblemDetails.STATUS;
import static com.predic8.membrane.core.exceptions.ProblemDetails.TITLE;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML_UTF8;
import static com.predic8.membrane.core.util.HttpUtil.getMessageForStatusCode;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

public class ProblemDetailsHTML {

    private static final String STYLE = """
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                   color: #222222; background: #FAFAFA; margin: 0; padding: 3em 1em; }
            main { max-width: 44em; margin: 0 auto; background: #FFFFFF; border: 1px solid #E5E5E5;
                   border-radius: 6px; padding: 2em 2.5em; }
            .status { font-size: 3.5em; font-weight: 600; color: #1F7A8C; margin: 0; line-height: 1; }
            h1 { font-size: 1.4em; font-weight: 600; margin: 0.3em 0 0 0; }
            .detail { font-size: 1.05em; }
            dl { margin: 1.5em 0 0 0; border-top: 1px solid #E5E5E5; padding-top: 1em; }
            dt { font-weight: 600; margin-top: 0.8em; }
            dd { margin: 0.2em 0 0 0; word-break: break-word; }
            dd dl { margin-top: 0.2em; border-top: none; padding-top: 0; }
            .docs { margin-top: 2em; border-top: 1px solid #E5E5E5; padding-top: 1.2em; }
            .docs a { color: #1F7A8C; font-weight: 600; text-decoration: none; }
            .docs a:hover { text-decoration: underline; }
            """;

    static void createHTMLContent(Map<String, Object> root, Response.ResponseBuilder builder) {
        builder.body(convertMapToHtml(root));
        builder.contentType(TEXT_HTML_UTF8);
    }

    private static String convertMapToHtml(Map<String, Object> root) {
        int status = statusOf(root);
        String reason = reasonFor(status);
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\"/>\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n");
        sb.append("<title>").append(status).append(" - ").append(escapeHtml4(reason)).append("</title>\n");
        sb.append("<style>").append(STYLE).append("</style>\n");
        sb.append("</head>\n<body>\n<main>\n");
        sb.append("<p class=\"status\">").append(status).append("</p>\n");
        sb.append("<h1>").append(escapeHtml4(headline(root, reason))).append("</h1>\n");
        appendDetail(sb, root.get(DETAIL));
        appendFields(sb, root);
        sb.append("<p class=\"docs\"><a href=\"https://www.membrane-api.io\">")
                .append("Membrane API Gateway documentation</a></p>\n");
        sb.append("</main>\n</body>\n</html>");
        return sb.toString();
    }

    private static String reasonFor(int status) {
        String reason = getMessageForStatusCode(status);
        return reason.isEmpty() ? "Error" : reason;
    }

    private static String headline(Map<String, Object> root, String reason) {
        Object title = root.get(TITLE);
        if (title == null || title.toString().isBlank())
            return reason;
        return title.toString();
    }

    private static int statusOf(Map<String, Object> root) {
        if (root.get(STATUS) instanceof Integer status)
            return status;
        return 500;
    }

    private static void appendDetail(StringBuilder sb, Object detail) {
        if (detail == null)
            return;
        sb.append("<p class=\"detail\">").append(escapeHtml4(detail.toString())).append("</p>\n");
    }

    private static void appendFields(StringBuilder sb, Map<String, Object> root) {
        StringBuilder fields = new StringBuilder();
        root.forEach((key, value) -> {
            if (TITLE.equals(key) || STATUS.equals(key) || DETAIL.equals(key) || value == null)
                return;
            appendEntry(fields, key, value);
        });
        if (fields.isEmpty())
            return;
        sb.append("<dl>\n").append(fields).append("</dl>\n");
    }

    private static void appendEntry(StringBuilder sb, String key, Object value) {
        sb.append("<dt>").append(escapeHtml4(key)).append("</dt>\n");
        sb.append("<dd>").append(renderValue(value)).append("</dd>\n");
    }

    private static String renderValue(Object value) {
        if (value instanceof Map<?, ?> map)
            return renderNested(map);
        if (value instanceof Collection<?> collection)
            return collection.stream().filter(Objects::nonNull).map(ProblemDetailsHTML::renderValue)
                    .reduce((a, b) -> a + "<br/>" + b).orElse("");
        return escapeHtml4(String.valueOf(value));
    }

    private static String renderNested(Map<?, ?> map) {
        StringBuilder nested = new StringBuilder("<dl>\n");
        map.forEach((key, value) -> {
            if (value == null)
                return;
            appendEntry(nested, String.valueOf(key), value);
        });
        return nested.append("</dl>").toString();
    }
}
