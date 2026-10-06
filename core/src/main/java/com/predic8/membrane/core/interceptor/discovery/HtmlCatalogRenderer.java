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
package com.predic8.membrane.core.interceptor.discovery;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.text.StringEscapeUtils.*;

/**
 * Renders a {@link Catalog} as an HTML page for a human in a browser. It is the human readable
 * view of the <code>membrane</code> format and shows what that format carries, operations
 * included. An endpoint with that format serves it when the <code>Accept</code> header asks for
 * HTML.
 * <p>
 * Titles, descriptions and contact details come from OpenAPI documents the gateway operator did
 * not necessarily write, so every value is escaped, and only <code>http</code> and
 * <code>https</code> URLs become links. Anything else, such as a <code>javascript:</code> URL, is
 * shown as text.
 */
public class HtmlCatalogRenderer implements CatalogRenderer {

    private static final Set<String> COLOURED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final String STYLE = """
            :root { color-scheme: light dark; --bg: #eeeeee; --box: #ffffff; --text: #1f2328; --muted: #59636e;
                    --line: #dddddd; --link: #0b5cad; --stripe: #f6f6f6; }
            @media (prefers-color-scheme: dark) {
                :root { --bg: #16181c; --box: #22252a; --text: #e6e6e6; --muted: #9aa1a9;
                        --line: #363a40; --link: #7ab7ff; --stripe: #272a30; }
            }
            * { box-sizing: border-box; }
            body { margin: 0; padding: 1em; background: var(--bg); color: var(--text); font-family: sans-serif; line-height: 1.4; }
            .box { max-width: 70em; margin: 0 auto 1em; padding: 1em 1.5em; background: var(--box);
                   border-radius: 10px; box-shadow: 0 0 20px rgba(0, 0, 0, 0.15); }
            h1, h2 { margin: 0 0 0.4em; overflow-wrap: anywhere; }
            a { color: var(--link); }
            dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.2em 1em; margin: 0.8em 0; }
            dt { color: var(--muted); }
            dd { margin: 0; overflow-wrap: anywhere; }
            .description { white-space: pre-wrap; }
            .version, .kind { font-size: 0.6em; font-weight: normal; padding: 0.15em 0.6em; border-radius: 1em;
                              border: 1px solid var(--line); vertical-align: middle; }
            .kind, .empty { color: var(--muted); }
            .endpoint { overflow-wrap: anywhere; }
            .links a { margin-right: 1em; }
            .tags { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 0.4em; }
            .tags li { background: var(--stripe); border: 1px solid var(--line); border-radius: 1em; padding: 0.1em 0.7em; font-size: 0.85em; }
            .operations { margin-top: 0.8em; }
            .operations > summary { cursor: pointer; font-weight: bold; color: var(--muted); padding: 0.2em 0; }
            .operations > summary:hover { color: var(--text); }
            .operations[open] > summary { margin-bottom: 0.2em; }
            .count { font-size: 0.85em; font-weight: normal; padding: 0.1em 0.6em; margin-left: 0.4em;
                     border: 1px solid var(--line); border-radius: 1em; background: var(--stripe); }
            .scroll { overflow-x: auto; }
            table { width: 100%; border-collapse: collapse; margin-top: 0.4em; }
            th, td { text-align: left; padding: 0.45em 0.6em; border-bottom: 1px solid var(--line); vertical-align: top; }
            tbody tr:nth-of-type(even) { background: var(--stripe); }
            .deprecated code, .deprecated td:last-child { text-decoration: line-through; color: var(--muted); }
            .method { display: inline-block; min-width: 4.5em; text-align: center; font: bold 0.8em monospace;
                      padding: 0.2em 0.4em; border-radius: 4px; color: #ffffff; background: #6e7781; }
            .get { background: #1f7a4d; }
            .post { background: #0b5cad; }
            .put { background: #a35a00; }
            .patch { background: #6f42c1; }
            .delete { background: #b42318; }
            """;

    @Override
    public String contentType() {
        return TEXT_HTML_UTF8;
    }

    @Override
    public boolean rendersOperations() {
        return true;
    }

    @Override
    public byte[] render(Catalog catalog) {
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                %s</style>
                </head>
                <body>
                """.formatted(escape(catalog.name()), STYLE));
        renderHeader(html, catalog);
        if (catalog.apis().isEmpty())
            html.append("<p class=\"box empty\">No APIs are deployed on this gateway.</p>\n");
        catalog.apis().forEach(entry -> renderApi(html, entry));
        html.append("</body>\n</html>\n");
        return html.toString().getBytes(UTF_8);
    }

    private static void renderHeader(StringBuilder html, Catalog catalog) {
        html.append("<header class=\"box\">\n<h1>%s</h1>\n".formatted(escape(catalog.name())));
        if (catalog.description() != null)
            html.append("<p class=\"description\">%s</p>\n".formatted(escape(catalog.description())));
        html.append("<dl>\n");
        definition(html, "Identifier", escape(catalog.aid()));
        definition(html, "APIs", String.valueOf(catalog.apis().size()));
        definition(html, "Created", catalog.created().toString());
        definition(html, "Modified", catalog.modified().toString());
        html.append("</dl>\n</header>\n");
    }

    private static void renderApi(StringBuilder html, CatalogEntry entry) {
        html.append("<section class=\"box\">\n<h2>%s".formatted(escape(entry.name())));
        if (entry.version() != null)
            html.append(" <span class=\"version\">%s</span>".formatted(escape(entry.version())));
        html.append(" <span class=\"kind\">%s</span></h2>\n".formatted(entry.kind().getLabel()));
        // A proxy without a description of its own repeats its name there, which says nothing new.
        if (!entry.description().equals(entry.name()))
            html.append("<p class=\"description\">%s</p>\n".formatted(escape(entry.description())));
        renderEndpoint(html, entry.endpoint());
        renderLinks(html, entry);
        renderTags(html, entry.tags());
        renderDetails(html, entry);
        renderOperations(html, entry.operations());
        html.append("</section>\n");
    }

    /**
     * The method only appears when the proxy is restricted to one, <code>*</code> would be noise.
     */
    private static void renderEndpoint(StringBuilder html, CatalogEntry.Endpoint endpoint) {
        html.append("<p class=\"endpoint\">");
        if (endpoint.method() != null && !"*".equals(endpoint.method()))
            html.append(method(endpoint.method())).append(' ');
        html.append(link(endpoint.url(), endpoint.url())).append("</p>\n");
    }

    private static void renderLinks(StringBuilder html, CatalogEntry entry) {
        List<String> links = new ArrayList<>();
        if (entry.links().humanUrl() != null)
            links.add(link(entry.links().humanUrl(), "Swagger UI"));
        if (entry.links().specUrl() != null)
            links.add(link(entry.links().specUrl(), "OpenAPI"));
        if (entry.soap() != null && entry.soap().wsdl() != null)
            links.add(link(entry.soap().wsdl(), "WSDL"));
        if (links.isEmpty())
            return;
        html.append("<p class=\"links\">%s</p>\n".formatted(String.join("\n", links)));
    }

    private static void renderTags(StringBuilder html, List<String> tags) {
        if (tags.isEmpty())
            return;
        html.append("<ul class=\"tags\">");
        tags.forEach(tag -> html.append("<li>%s</li>".formatted(escape(tag))));
        html.append("</ul>\n");
    }

    private static void renderDetails(StringBuilder html, CatalogEntry entry) {
        StringBuilder details = new StringBuilder();
        String contact = entry.contact() == null ? "" : contact(entry.contact());
        if (!contact.isEmpty())
            definition(details, "Contact", contact);
        if (entry.license() != null && (entry.license().name() != null || entry.license().url() != null))
            definition(details, "License", link(entry.license().url(),
                    firstNonNull(entry.license().name(), entry.license().url())));
        if (entry.termsOfService() != null)
            definition(details, "Terms of service", link(entry.termsOfService(), entry.termsOfService()));
        if (entry.soap() != null && entry.soap().serviceName() != null)
            definition(details, "Service", escape(entry.soap().serviceName()));
        if (entry.soap() != null && entry.soap().portName() != null)
            definition(details, "Port", escape(entry.soap().portName()));
        if (details.isEmpty())
            return;
        html.append("<dl>\n").append(details).append("</dl>\n");
    }

    private static String contact(CatalogEntry.Contact contact) {
        List<String> parts = new ArrayList<>();
        if (contact.name() != null)
            parts.add(escape(contact.name()));
        if (contact.email() != null)
            parts.add("<a href=\"mailto:%s\">%s</a>".formatted(escape(contact.email()), escape(contact.email())));
        if (contact.url() != null)
            parts.add(link(contact.url(), contact.url()));
        return String.join(", ", parts);
    }

    private static void renderOperations(StringBuilder html, List<CatalogOperation> operations) {
        if (operations.isEmpty())
            return;
        html.append("""
                <details class="operations">
                <summary>Operations <span class="count">%d</span></summary>
                <div class="scroll">
                <table>
                <thead><tr><th>Method</th><th>Path</th><th>Summary</th></tr></thead>
                <tbody>
                """.formatted(operations.size()));
        operations.forEach(operation -> html.append("<tr%s><td>%s</td><td><code>%s</code></td><td>%s</td></tr>\n".formatted(
                operation.deprecated() ? " class=\"deprecated\"" : "",
                method(operation.method()),
                escape(operation.path()),
                escape(firstNonNull(operation.summary(), operation.operationId())))));
        html.append("</tbody>\n</table>\n</div>\n</details>\n");
    }

    private static String method(String method) {
        String upper = method.toUpperCase(Locale.ROOT);
        String css = COLOURED_METHODS.contains(upper) ? "method " + upper.toLowerCase(Locale.ROOT) : "method";
        return "<span class=\"%s\">%s</span>".formatted(css, escape(upper));
    }

    private static void definition(StringBuilder html, String term, String renderedValue) {
        html.append("<dt>%s</dt><dd>%s</dd>\n".formatted(term, renderedValue));
    }

    /**
     * A link when the URL is <code>http</code> or <code>https</code>, the escaped text otherwise.
     */
    private static String link(String url, String text) {
        if (!isHttpUrl(url))
            return escape(text);
        return "<a href=\"%s\">%s</a>".formatted(escape(url), escape(text));
    }

    private static boolean isHttpUrl(String url) {
        if (url == null)
            return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * Unlike {@link Objects#requireNonNullElse}, both may be null: the OpenAPI members this falls
     * back between are all optional.
     */
    private static String firstNonNull(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String escape(String text) {
        return text == null ? "" : escapeHtml4(text);
    }
}
