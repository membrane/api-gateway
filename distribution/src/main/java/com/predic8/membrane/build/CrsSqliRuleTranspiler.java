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

package com.predic8.membrane.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Build-time tool (not shipped in any Membrane jar) that transpiles the OWASP CRS
 * {@code REQUEST-942-APPLICATION-ATTACK-SQLI.conf} ModSecurity rule file into the Membrane-native
 * {@code crs-sqli-rules.json} resource consumed by {@code SqlInjectionProtectionInterceptor} in the core module.
 * <p>
 * Only the {@code @rx} (plain regex) rules are extracted; the two {@code @detectSQLi} (libinjection) rules and
 * the CRS anomaly-scoring machinery are intentionally dropped. Every CRS regex compiles unchanged under
 * {@link java.util.regex.Pattern}. Chained rules whose second condition cannot be represented — i.e. anything
 * other than a positive {@code @rx} against the same request content (CRS chains on {@code TX:n} /
 * {@code MATCHED_VARS} with {@code @streq} / {@code !@streq} / {@code !@rx}) — are dropped whole rather than
 * emitted without their constraint.
 * <p>
 * Two modes, wired into the distribution build:
 * <ul>
 *   <li>{@code generate <conf> <out.json>} — (re)generate the resource (run manually after a CRS update).</li>
 *   <li>{@code check <conf> <out.json>} — fail the build if the committed resource is out of sync with the
 *       committed CRS source, so the shipped rules can never silently drift.</li>
 * </ul>
 * Source: <a href="https://github.com/coreruleset/coreruleset">https://github.com/coreruleset/coreruleset</a>.
 * The OWASP CRS is Copyright (c) 2006-2020 Trustwave and contributors and Copyright (c) 2021-2026 the CRS
 * project, licensed Apache-2.0; the generated rules retain their CRS rule id and message for attribution.
 */
public class CrsSqliRuleTranspiler {

    /** CRS transformations that appear in REQUEST-942 and are reproduced by core's Transformation enum. */
    private static final Set<String> SUPPORTED_TRANSFORMS = Set.of(
            "urlDecodeUni", "replaceComments", "removeCommentsChar", "removeWhitespace", "utf8toUnicode");

    private static final Pattern CONTINUATION = Pattern.compile("\\\\\\n\\s*");
    // Chained second conditions are written as indented SecRule lines, so allow leading whitespace.
    private static final Pattern DIRECTIVE = Pattern.compile("^[ \\t]*SecRule.*$", Pattern.MULTILINE);
    private static final Pattern OPERATOR = Pattern.compile("\"@rx (.*)\" \"(.*)\"\\s*$");
    // Variable list a SecRule targets (the token right after SecRule), used to spot capture-based chains.
    private static final Pattern TARGET = Pattern.compile("SecRule\\s+(\\S+)");
    private static final Pattern ID = Pattern.compile("id:(\\d+)");
    private static final Pattern CHAIN = Pattern.compile("(?:^|,)chain(?:,|$)");
    private static final Pattern TRANSFORM = Pattern.compile("t:(\\w+)");
    private static final Pattern MSG = Pattern.compile("msg:'([^']*)'");
    private static final Pattern PARANOIA = Pattern.compile("paranoia-level/(\\d)");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** CRS SQLi rule files are named e.g. REQUEST-942-APPLICATION-ATTACK-SQLI.conf; match by pattern. */
    private static final String CONF_GLOB = "REQUEST-942*.conf";

    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !Set.of("generate", "check").contains(args[0])) {
            System.err.println("Usage: CrsSqliRuleTranspiler <generate|check> <confDirOrFile> <out.json>");
            System.exit(2);
        }
        String mode = args[0];
        Path conf = resolveConf(Paths.get(args[1]));
        Path json = Paths.get(args[2]);

        ArrayNode generated = transpile(Files.readString(conf, UTF_8));

        if (mode.equals("generate")) {
            Files.writeString(json, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(generated) + "\n", UTF_8);
            System.out.println("Wrote " + generated.size() + " rules to " + json);
            return;
        }

        // check
        if (!Files.exists(json)) {
            System.err.println("Rule resource missing: " + json + " — run CrsSqliRuleTranspiler generate");
            System.exit(1);
        }
        JsonNode committed = MAPPER.readTree(Files.readString(json, UTF_8));
        if (!committed.equals(generated)) {
            System.err.println("SQL injection rules in " + json + " are out of sync with " + conf + ".");
            System.err.println("Run: mvn -pl distribution exec:java -Dexec.mainClass=com.predic8.membrane.build.CrsSqliRuleTranspiler "
                    + "-Dexec.args=\"generate <conf> <out.json>\"  (or the generate-sqli-rules profile) and commit the result.");
            System.exit(1);
        }
        System.out.println("SQL injection rules are up to date (" + generated.size() + " rules).");
    }

    /**
     * Resolve the CRS source file. Accepts the file itself, or a directory in which the single file matching
     * {@value #CONF_GLOB} is located — so a CRS rename does not require touching the build configuration.
     */
    private static Path resolveConf(Path path) throws IOException {
        if (Files.isRegularFile(path))
            return path;
        if (!Files.isDirectory(path))
            throw new NoSuchFileException("CRS source not found: " + path);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, CONF_GLOB)) {
            List<Path> matches = new ArrayList<>();
            stream.forEach(matches::add);
            if (matches.isEmpty())
                throw new NoSuchFileException("No file matching '" + CONF_GLOB + "' in " + path);
            if (matches.size() > 1)
                throw new IOException("Multiple files matching '" + CONF_GLOB + "' in " + path + ": " + matches);
            return matches.get(0);
        }
    }

    /** Transpile the CRS REQUEST-942 file content into the Membrane rule array. */
    public static ArrayNode transpile(String conf) {
        List<ObjectNode> rules = new ArrayList<>();
        ObjectNode pendingChain = null; // a rule awaiting its chained second condition

        Matcher directives = DIRECTIVE.matcher(CONTINUATION.matcher(conf).replaceAll(""));
        while (directives.find()) {
            String directive = directives.group();
            Matcher op = OPERATOR.matcher(directive);
            String regex = op.find() ? op.group(1) : null;
            String actions = regex != null ? op.group(2) : null;

            if (pendingChain != null) {
                // This directive is the chained second condition of the previous rule. Membrane can only
                // represent a chained condition that is another positive @rx applied to the *same request
                // content* (stored as "requires"). CRS REQUEST-942 instead chains on captured variables
                // (TX:n / MATCHED_VARS) with @streq / !@streq / !@rx, which have no Membrane equivalent.
                // Emitting the broad first predicate without its constraint would cause false positives,
                // so drop the whole rule instead of letting it through weakened.
                if (regex != null && !chainsOnCapturedVariable(directive)) {
                    pendingChain.put("requires", regex);
                    rules.add(pendingChain);
                } else {
                    System.err.println("WARNING: dropping chained rule " + pendingChain.get("id").asText()
                            + " — unsupported chained condition (only a plain @rx on request content can be represented)");
                }
                pendingChain = null;
                continue;
            }

            if (regex == null)
                continue;
            Matcher id = ID.matcher(actions);
            if (!id.find())
                continue;

            warnOnUnsupportedTransforms(id.group(1), actions);

            ObjectNode rule = MAPPER.createObjectNode();
            rule.put("id", id.group(1));
            rule.put("paranoiaLevel", paranoiaLevel(actions));
            rule.set("transforms", transforms(actions));
            rule.put("message", message(actions));
            rule.put("regex", regex);

            if (CHAIN.matcher(actions).find())
                pendingChain = rule;
            else
                rules.add(rule);
        }

        rules.sort(Comparator.comparing(r -> r.get("id").asText()));
        ArrayNode array = MAPPER.createArrayNode();
        rules.forEach(array::add);
        return array;
    }

    /**
     * @return true if the chained condition targets a captured/internal ModSecurity variable (TX:n,
     * MATCHED_VARS, ...). Such conditions compare captures rather than scanning the request content and
     * therefore cannot be expressed as a Membrane {@code requires} pattern.
     */
    private static boolean chainsOnCapturedVariable(String directive) {
        Matcher m = TARGET.matcher(directive);
        if (!m.find())
            return true; // unknown shape -> treat as unsupported
        String vars = m.group(1);
        return vars.contains("TX:") || vars.contains("MATCHED_VAR");
    }

    private static ArrayNode transforms(String actions) {
        ArrayNode array = MAPPER.createArrayNode();
        Matcher m = TRANSFORM.matcher(actions);
        while (m.find())
            if (!m.group(1).equals("none"))
                array.add(m.group(1));
        return array;
    }

    private static int paranoiaLevel(String actions) {
        Matcher m = PARANOIA.matcher(actions);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private static String message(String actions) {
        Matcher m = MSG.matcher(actions);
        return m.find() ? m.group(1) : "SQL Injection Attack Detected";
    }

    private static void warnOnUnsupportedTransforms(String ruleId, String actions) {
        Matcher m = TRANSFORM.matcher(actions);
        while (m.find()) {
            String t = m.group(1);
            if (!t.equals("none") && !SUPPORTED_TRANSFORMS.contains(t))
                System.err.println("WARNING: rule " + ruleId + " uses unsupported transformation '" + t + "'");
        }
    }
}
