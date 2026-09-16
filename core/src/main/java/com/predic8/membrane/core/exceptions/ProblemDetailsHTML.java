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

    private static final String GITHUB_ICON = """
            <svg viewBox="0 0 16 16" width="18" height="18" fill="currentColor" aria-hidden="true">\
            <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>\
            </svg>""";

    private static final String STYLE = """
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                   color: #222222; background: #FAFAFA; margin: 0; padding: 3em 1em; text-align: left; }
            main { max-width: 40em; margin: 0 auto; background: #FFFFFF; border: 1px solid #E5E5E5;
                   border-radius: 8px; padding: 2.5em; }
            .status { margin: 0; display: flex; align-items: center; gap: 0.8em; }
            .status .code { font-size: 2.6em; font-weight: 700; color: #2E8CE0; line-height: 1; }
            .status .reason { font-size: 2.6em; font-weight: 700; color: #2E8CE0; line-height: 1; }
            .title { font-size: 1.3em; font-weight: 600; color: #222222; margin: 0.6em 0 0 0; }
            .detail { font-size: 1.05em; font-weight: 400; font-style: italic; color: #555555; margin: 0.5em 0 0 0; }
            .fields { margin: 1.4em 0 0 0; }
            .field { margin-top: 0.4em; word-break: break-word; }
            .field .key { font-weight: 600; }
            footer { display: flex; align-items: center; justify-content: flex-start; gap: 0.6em;
                     margin-top: 2em; font-size: 0.9em; color: #999999; }
            footer a { color: #2E8CE0; font-weight: 600; text-decoration: none;
                       display: inline-flex; align-items: center; gap: 0.35em; }
            footer a:hover { text-decoration: underline; }
            footer .dot { color: #CCCCCC; }
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
        sb.append("<p class=\"status\"><span class=\"code\">").append(status).append("</span>")
                .append("<span class=\"reason\">").append(escapeHtml4(reason)).append("</span></p>\n");
        appendTitle(sb, root, reason);
        appendDetail(sb, root.get(DETAIL));
        appendFields(sb, root);
        sb.append("<footer>\n");
        sb.append("<a href=\"https://www.membrane-api.io/\">Membrane API Gateway</a>\n");
        sb.append("<span class=\"dot\">&middot;</span>\n");
        sb.append("<a href=\"https://github.com/membrane/api-gateway\" target=\"_blank\" rel=\"noopener\">")
                .append(GITHUB_ICON).append("GitHub</a>\n");
        sb.append("</footer>\n");
        sb.append("</main>\n</body>\n</html>");
        return sb.toString();
    }

    private static String reasonFor(int status) {
        String reason = getMessageForStatusCode(status);
        return reason.isEmpty() ? "Error" : reason;
    }

    private static void appendTitle(StringBuilder sb, Map<String, Object> root, String reason) {
        Object title = root.get(TITLE);
        if (title == null || title.toString().isBlank() || title.toString().equals(reason))
            return;
        sb.append("<h1 class=\"title\">").append(escapeHtml4(title.toString())).append("</h1>\n");
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
            appendField(fields, key, value, 0);
        });
        if (fields.isEmpty())
            return;
        sb.append("<div class=\"fields\">\n").append(fields).append("</div>\n");
    }

    private static void appendField(StringBuilder sb, String key, Object value, int depth) {
        String indent = depth == 0 ? "" : " style=\"margin-left: " + (depth * 1.2) + "em;\"";
        if (value instanceof Map<?, ?> map) {
            sb.append("<div class=\"field\"").append(indent).append("><span class=\"key\">")
                    .append(escapeHtml4(key)).append(":</span></div>\n");
            map.forEach((k, v) -> {
                if (v != null)
                    appendField(sb, String.valueOf(k), v, depth + 1);
            });
            return;
        }
        sb.append("<div class=\"field\"").append(indent).append("><span class=\"key\">")
                .append(escapeHtml4(key)).append(":</span> ").append(renderValue(value)).append("</div>\n");
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
        StringBuilder nested = new StringBuilder();
        map.forEach((key, value) -> {
            if (value == null)
                return;
            nested.append("<span class=\"key\">").append(escapeHtml4(String.valueOf(key))).append(":</span> ")
                    .append(renderValue(value)).append("<br/>");
        });
        return nested.toString();
    }
}
