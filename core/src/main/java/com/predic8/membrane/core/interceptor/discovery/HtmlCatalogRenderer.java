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

import java.net.URLEncoder;
import java.security.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.discovery.ApiDiscoveryInterceptor.*;
import static com.predic8.membrane.core.openapi.util.UriUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static org.apache.commons.text.StringEscapeUtils.*;

/**
 * Renders a {@link Catalog} as an HTML page for a human in a browser. It is the human readable
 * view of Membrane's own format and shows what that format carries, operations included.
 * <code>apiDiscovery</code> serves it when the <code>Accept</code> header asks for HTML.
 * <p>
 * Titles, descriptions and contact details come from OpenAPI documents the gateway operator did
 * not necessarily write, so every value is escaped, and only <code>http</code> and
 * <code>https</code> URLs become links. Anything else, such as a <code>javascript:</code> URL, is
 * shown as text. The page's own script, which filters the APIs and expands or collapses them, is
 * the only one {@link #CONTENT_SECURITY_POLICY} lets run.
 */
public class HtmlCatalogRenderer implements CatalogRenderer {

    private static final Set<String> COLOURED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final String UNTAGGED_GROUP = "Other";

    // Swagger UI lists operations without a tag under this one.
    private static final String SWAGGER_UI_UNTAGGED = "default";

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

    private final CollectionInfo collection;

    public HtmlCatalogRenderer(CollectionInfo collection) {
        this.collection = collection;
    }

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
            .more > summary { cursor: pointer; color: var(--muted); }
            .more > summary:hover { color: var(--text); }
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
            .scroll { max-height: 70vh; overflow: auto; }
            table { width: 100%; border-collapse: collapse; margin-top: 0.4em; }
            th, td { text-align: left; padding: 0.45em 0.6em; border-bottom: 1px solid var(--line); vertical-align: top; }
            thead th { position: sticky; top: 0; background: var(--box); }
            tbody tr:nth-of-type(even) { background: var(--stripe); }
            .group th { background: var(--stripe); color: var(--muted); font-size: 0.9em; }
            .opid { color: var(--muted); font-family: monospace; font-size: 0.85em; }
            .badge { color: var(--muted); border: 1px solid var(--line); border-radius: 1em;
                     padding: 0.05em 0.5em; font-size: 0.75em; white-space: nowrap; }
            [hidden] { display: none !important; }
            .api { margin-bottom: 0.6em; padding: 0.8em 1.5em; }
            .api > summary { display: flex; align-items: center; gap: 0.8em; }
            :is(.api, .more, .operations) > summary { cursor: pointer; list-style: none; }
            :is(.api, .more, .operations) > summary::-webkit-details-marker { display: none; }
            :is(.api, .more, .operations) > summary::before { content: ""; display: inline-block; flex: none;
                    width: 0.45em; height: 0.45em; margin: 0 0.6em 0.15em 0.1em; vertical-align: middle;
                    border-right: 2px solid var(--muted); border-bottom: 2px solid var(--muted);
                    transform: rotate(-45deg); transition: transform 0.15s; }
            .api > summary::before { margin: 0 0.1em; }
            :is(.api, .more, .operations)[open] > summary::before { transform: rotate(45deg); }
            .api > summary > h2 { margin: 0; font-size: 1.25em; }
            .api > summary:hover > h2 { color: var(--link); }
            .api[open] > summary { margin-bottom: 0.6em; padding-bottom: 0.6em; border-bottom: 1px solid var(--line); }
            .toolbar { position: sticky; top: 0; z-index: 1; display: flex; flex-wrap: wrap; align-items: center;
                       gap: 0.6em 1em; padding: 0.7em 1.5em; }
            .toolbar input { flex: 1 1 16em; min-width: 0; font: inherit; color: inherit; padding: 0.45em 0.7em;
                             background: var(--bg); border: 1px solid var(--line); border-radius: 6px; }
            .toolbar button { font: inherit; color: inherit; padding: 0.45em 0.9em; cursor: pointer;
                              background: var(--stripe); border: 1px solid var(--line); border-radius: 6px; }
            .toolbar button:hover:enabled { border-color: var(--muted); }
            .toolbar button:disabled { cursor: default; color: var(--muted); }
            .toolbar :focus-visible { outline: 2px solid var(--link); outline-offset: 1px; }
            .matches:empty { display: none; }
            .matches { color: var(--muted); font-size: 0.9em; white-space: nowrap; font-variant-numeric: tabular-nums; }
            .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden;
                       clip-path: inset(50%); white-space: nowrap; }
            .deprecated code, .deprecated td:last-child { text-decoration: line-through; color: var(--muted); }
            .method { display: inline-block; min-width: 4.5em; text-align: center; font: bold 0.8em monospace;
                      padding: 0.2em 0.4em; border-radius: 4px; color: #ffffff; background: #6e7781; }
            .get { background: #1f7a4d; }
            .post { background: #0b5cad; }
            .put { background: #a35a00; }
            .patch { background: #6f42c1; }
            .delete { background: #b42318; }
            """;

    /**
     * Filters the APIs as the reader types and expands or collapses all of them. An API matches when
     * every search term occurs in it; terms that only some operations match narrow it down to those
     * operations, and the filter opens what it reveals that way and closes it again when it changes.
     */
    private static final String SCRIPT = """
            (() => {
              const filter = document.getElementById("filter");
              const toggle = document.getElementById("toggle");
              const matches = document.getElementById("matches");
              const noMatch = document.getElementById("no-match");
              const apis = Array.from(document.querySelectorAll("details.api"));
              const openedByFilter = new Set();
              const shown = () => apis.filter(api => !api.hidden);

              function updateToggle() {
                const visible = shown();
                toggle.disabled = visible.length === 0;
                toggle.textContent = visible.length > 0 && visible.every(api => api.open) ? "Collapse all" : "Expand all";
              }

              function reveal(details) {
                if (details && !details.open) {
                  details.open = true;
                  openedByFilter.add(details);
                }
              }

              function apply() {
                openedByFilter.forEach(details => details.open = false);
                openedByFilter.clear();
                const terms = filter.value.toLowerCase().split(/\\s+/).filter(Boolean);
                let count = 0;
                for (const api of apis) {
                  const text = api.dataset.search;
                  const apiMatches = terms.every(term => text.includes(term));
                  let rowMatches = 0;
                  for (const row of api.querySelectorAll("tr[data-search]")) {
                    row.hidden = !apiMatches && !terms.every(term => text.includes(term) || row.dataset.search.includes(term));
                    if (!row.hidden) rowMatches++;
                  }
                  for (const group of api.querySelectorAll("tbody"))
                    group.hidden = !group.querySelector("tr[data-search]:not([hidden])");
                  api.hidden = !apiMatches && rowMatches === 0;
                  if (!api.hidden) count++;
                  if (!apiMatches && rowMatches > 0) {
                    reveal(api);
                    reveal(api.querySelector("details.operations"));
                  }
                }
                matches.textContent = terms.length === 0 ? "" : count + " of " + apis.length + " APIs";
                noMatch.hidden = count > 0;
                updateToggle();
              }

              toggle.addEventListener("click", () => {
                const expand = toggle.textContent === "Expand all";
                shown().forEach(api => api.open = expand);
                openedByFilter.clear();
                updateToggle();
              });
              filter.addEventListener("input", apply);
              filter.addEventListener("keydown", event => {
                if (event.key === "Escape") {
                  filter.value = "";
                  apply();
                }
              });
              document.addEventListener("keydown", event => {
                if (event.key === "/" && document.activeElement !== filter && !event.ctrlKey && !event.metaKey && !event.altKey) {
                  event.preventDefault();
                  filter.focus();
                }
              });
              // toggle does not bubble, so it is caught on its way down.
              document.addEventListener("toggle", updateToggle, true);
              document.getElementById("toolbar").hidden = false;
              apply();
            })();
            """;

    /**
     * Allows the page's own script by its hash and nothing else, so markup that slipped past the
     * escaping still cannot run a script.
     */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-%s'".formatted(sha256(SCRIPT));

    private static String sha256(String text) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform supports SHA-256.", e);
        }
    }

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
                """.formatted(escape(collection.name()), STYLE));
        renderHeader(html, catalog);
        if (catalog.apis().isEmpty())
            html.append("<p class=\"box empty\">No APIs are published here.</p>\n");
        else
            renderToolbar(html);
        catalog.apis().forEach(entry -> renderApi(html, entry));
        if (!catalog.apis().isEmpty())
            html.append("<script>").append(SCRIPT).append("</script>\n");
        html.append("</body>\n</html>\n");
        return html.toString().getBytes(UTF_8);
    }

    private void renderHeader(StringBuilder html, Catalog catalog) {
        html.append("<header class=\"box\">\n<h1>%s</h1>\n".formatted(escape(collection.name())));
        if (collection.description() != null)
            html.append("<p class=\"description\">%s</p>\n".formatted(escape(collection.description())));
        html.append("<dl>\n");
        definition(html, "APIs", apiCount(catalog));
        html.append("</dl>\n");
        // A browser always prefers HTML, so the JSON is only reachable through the query parameter.
        html.append("<p class=\"links\"><a href=\"?%s=%s\">View as JSON</a></p>\n".formatted(FORMAT_PARAMETER, JSON_FORMAT));
        html.append("</header>\n");
    }

    private static void renderToolbar(StringBuilder html) {
        html.append("""
                <div class="box toolbar" id="toolbar" hidden>
                <input type="search" id="filter" placeholder="Filter by name, path, tag or operation (press /)" aria-label="Filter APIs" autocomplete="off">
                <span class="matches" id="matches" aria-live="polite"></span>
                <button type="button" id="toggle">Expand all</button>
                </div>
                <p class="box empty" id="no-match" hidden>No API matches the filter.</p>
                """);
    }

    private static String apiCount(Catalog catalog) {
        final var byKind = catalog.apis().stream()
                .collect(groupingBy(CatalogEntry::kind, TreeMap::new, counting()));
        if (byKind.size() < 2)
            return String.valueOf(catalog.apis().size());
        return "%d (%s)".formatted(catalog.apis().size(), byKind.entrySet().stream()
                .map(kind -> "%d %s".formatted(kind.getValue(), kind.getKey().getLabel()))
                .collect(joining(", ")));
    }

    private static String sectionId(CatalogEntry entry) {
        return CatalogCollector.slug(entry.id());
    }

    // Collapsed to its title, so that the page opens as an overview of every API.
    private static void renderApi(StringBuilder html, CatalogEntry entry) {
        html.append("<details class=\"box api\" id=\"%s\" data-search=\"%s\">\n<summary><h2>%s"
                .formatted(sectionId(entry), escape(searchText(entry)), escape(entry.name())));
        if (entry.version() != null)
            html.append(" <span class=\"version\">%s</span>".formatted(escape(entry.version())));
        html.append(" <span class=\"kind\">%s</span></h2></summary>\n".formatted(entry.kind().getLabel()));
        // A proxy without a description of its own repeats its name there, which says nothing new.
        final var paragraphs = paragraphs(entry.description());
        if (!paragraphs.isEmpty() && !paragraphs.equals(List.of(entry.name())))
            renderDescription(html, paragraphs);
        renderEndpoint(html, entry.endpoint());
        renderLinks(html, entry);
        renderTags(html, entry.tags());
        renderDetails(html, entry);
        renderOperations(html, entry);
        html.append("</details>\n");
    }

    private static String searchText(CatalogEntry entry) {
        return searchText(Stream.concat(
                Stream.of(entry.name(), entry.version(), entry.kind().getLabel(), entry.description(), entry.endpoint().url()),
                entry.tags().stream()));
    }

    private static String searchText(CatalogEntry entry, CatalogOperation operation) {
        return searchText(Stream.concat(
                Stream.of(operation.method(), fullPath(entry, operation), operation.summary(), operation.operationId()),
                operation.tags().stream()));
    }

    private static String searchText(Stream<String> values) {
        return values.filter(Objects::nonNull).collect(joining(" ")).toLowerCase(Locale.ROOT);
    }

    private static List<String> paragraphs(String description) {
        final var withoutImages = MARKDOWN_IMAGE.matcher(description).replaceAll("");
        final var withoutLinks = MARKDOWN_LINK.matcher(withoutImages).replaceAll("$1");
        return Arrays.stream(BLANK_LINE.split(withoutLinks.replace("\r\n", "\n")))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .toList();
    }

    /**
     * The first paragraph is shown, the rest is collapsed: a catalog page lists many APIs, and a
     * long OpenAPI description would push the next one far down.
     */
    private static void renderDescription(StringBuilder html, List<String> paragraphs) {
        html.append(descriptionParagraph(paragraphs.getFirst()));
        if (paragraphs.size() == 1)
            return;
        html.append("<details class=\"more\">\n<summary>More</summary>\n");
        paragraphs.subList(1, paragraphs.size()).forEach(paragraph -> html.append(descriptionParagraph(paragraph)));
        html.append("</details>\n");
    }

    private static String descriptionParagraph(String paragraph) {
        return "<p class=\"description\">%s</p>\n".formatted(escape(paragraph));
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

    private static void renderOperations(StringBuilder html, CatalogEntry entry) {
        final var operations = entry.operations();
        if (operations.isEmpty())
            return;
        html.append("""
                <details class="operations">
                <summary>Operations <span class="count">%d</span></summary>
                <div class="scroll">
                <table>
                <caption class="sr-only">Operations of %s</caption>
                <thead><tr><th scope="col">Method</th><th scope="col">Path</th><th scope="col">Summary</th></tr></thead>
                """.formatted(operations.size(), escape(entry.name())));
        if (operations.stream().allMatch(operation -> operation.tags().isEmpty()))
            renderOperationGroup(html, entry, null, operations);
        else
            groupByTag(entry.documentTags(), operations).forEach((tag, group) -> renderOperationGroup(html, entry, tag, group));
        html.append("</table>\n</div>\n</details>\n");
    }

    private static Map<String, List<CatalogOperation>> groupByTag(List<String> declaredTags, List<CatalogOperation> operations) {
        final var byTag = new LinkedHashMap<String, List<CatalogOperation>>();
        declaredTags.forEach(tag -> byTag.put(tag, new ArrayList<>()));
        final var untagged = new ArrayList<CatalogOperation>();
        operations.forEach(operation -> {
            if (operation.tags().isEmpty())
                untagged.add(operation);
            else
                byTag.computeIfAbsent(operation.tags().getFirst(), tag -> new ArrayList<>()).add(operation);
        });
        byTag.values().removeIf(List::isEmpty);
        if (!untagged.isEmpty())
            byTag.computeIfAbsent(UNTAGGED_GROUP, tag -> new ArrayList<>()).addAll(untagged);
        return byTag;
    }

    private static void renderOperationGroup(StringBuilder html, CatalogEntry entry, String tag, List<CatalogOperation> operations) {
        html.append("<tbody>\n");
        if (tag != null)
            html.append("<tr class=\"group\"><th colspan=\"3\" scope=\"colgroup\">%s</th></tr>\n".formatted(escape(tag)));
        operations.forEach(operation -> html.append(operationRow(entry, operation)));
        html.append("</tbody>\n");
    }

    private static String operationRow(CatalogEntry entry, CatalogOperation operation) {
        return "<tr data-search=\"%s\"%s><td>%s%s</td><td>%s</td><td>%s</td></tr>\n".formatted(
                escape(searchText(entry, operation)),
                operation.deprecated() ? " class=\"deprecated\"" : "",
                method(operation.method()),
                // Struck-through text alone carries nothing to a screen reader, so it is said too.
                operation.deprecated() ? " <span class=\"badge\">deprecated</span>" : "",
                operationPath(entry, operation),
                summary(operation));
    }

    /**
     * The path the client calls, the path of the api in front of the one from the OpenAPI
     * document, linked to the operation in Swagger UI where possible.
     */
    private static String operationPath(CatalogEntry entry, CatalogOperation operation) {
        final var path = "<code>%s</code>".formatted(escape(fullPath(entry, operation)));
        final var swaggerUi = swaggerUiLink(entry, operation);
        if (!isHttpUrl(swaggerUi))
            return path;
        return "<a href=\"%s\">%s</a>".formatted(escape(swaggerUi), path);
    }

    private static String fullPath(CatalogEntry entry, CatalogOperation operation) {
        return trimTrailingSlash(entry.endpoint().path()) + operation.path();
    }

    // Swagger UI decodes the fragment once and then splits it at '/', so a tag or id containing one cannot be linked.
    private static String swaggerUiLink(CatalogEntry entry, CatalogOperation operation) {
        if (entry.links().humanUrl() == null || operation.operationId() == null)
            return null;
        final var tag = operation.tags().isEmpty() ? SWAGGER_UI_UNTAGGED : operation.tags().getFirst();
        if (tag.contains("/") || operation.operationId().contains("/"))
            return null;
        return "%s#/%s/%s".formatted(entry.links().humanUrl(), fragmentSegment(tag), fragmentSegment(operation.operationId()));
    }

    private static String fragmentSegment(String value) {
        return URLEncoder.encode(value, UTF_8).replace("+", "%20");
    }

    private static String summary(CatalogOperation operation) {
        if (operation.summary() == null || operation.operationId() == null)
            return escape(firstNonNull(operation.summary(), operation.operationId()));
        return "%s <span class=\"opid\">%s</span>"
                .formatted(escape(operation.summary()), escape(operation.operationId()));
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
