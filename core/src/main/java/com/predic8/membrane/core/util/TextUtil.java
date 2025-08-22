/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import com.predic8.xml.beautifier.*;
import org.apache.commons.text.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

import static java.lang.Integer.*;
import static java.nio.charset.StandardCharsets.*;


public class TextUtil {
    private static final Logger log = LoggerFactory.getLogger(TextUtil.class.getName());

    // Guess for a very short XML
    private static final int STRING_BUFFER_INITIAL_CAPACITY_FOR_XML = 250;

    private static final char[] source;
    private static final String[] replace;

    static {
        source = new char[]{'*', '?', '.', '\\', '(', ')', '+', '|', '^', '$', '%', '@'};
        replace = new String[]{".*", ".", "\\.", "\\\\", "\\(", "\\)", "\\+", "\\|", "\\^", "\\$", "\\%", "\\@"};
    }

    /**
     * Returns a {@link Charset} for a given encoding name.
     * <p>
     * Behavior:
     * <ul>
     *   <li>If {@code encoding} is {@code null}, blank, or cannot be resolved, {@code defaultCharset} is returned.</li>
     * </ul>
     *
     * <h4>Examples</h4>
     * <pre>
     * getCharset(null, UTF_8)                  → UTF_8
     * getCharset("latin1", UTF_8)              → ISO_8859-1
     * getCharset("unknown-xyz", UTF_8)         → UTF_8
     * </pre>
     *
     * @param encoding       the charset name as it appears; may be {@code null}
     * @param defaultCharset the charset to fall back to; must be non-null
     * @return the resolved {@link Charset}, or {@code defaultCharset} if resolution fails
     */
    public static Charset getCharset(String encoding, Charset defaultCharset) {
        if (encoding == null || encoding.isEmpty())
            return defaultCharset;
        try {
            return Optional.of(encoding).map(Charset::forName).orElse(defaultCharset);
        } catch (Exception e) {
            log.info("Encoding '{}' caused error: {}, Use default: {}", encoding, e.getMessage(), defaultCharset);
            return defaultCharset;
        }
    }

    public static Charset getCharset(String encoding) {
        return getCharset(encoding, UTF_8);
    }

    public static Charset getCharsetOrDefault(Charset charset) {
        return charset == null ? UTF_8 : charset;
    }

    public static String camelToKebab(String string) {
        // lower/digit to uppercase
        String kebab = string.replaceAll("([a-z0-9])([A-Z])", "$1-$2");
        // uppercase followed by uppercase to lowercase
        kebab = kebab.replaceAll("([A-Z])([A-Z][a-z])", "$1-$2");
        return kebab.toLowerCase();
    }

    /**
     * @param reader
     * @return
     * @throws Exception
     */
    public static String formatXML(Reader reader) throws Exception {
        return formatXML(reader, false);
    }

    /**
     * As HTML is needed for the AdminConsole
     *
     * @param reader XML
     * @param asHTML Should output formatted as XML
     * @return Formatted string
     * @throws Exception
     */
    public static String formatXML(Reader reader, boolean asHTML) throws Exception {
        try {
            StringWriter out = new StringWriter(STRING_BUFFER_INITIAL_CAPACITY_FOR_XML);
            new XMLBeautifier(getXmlBeautifierFormatter(asHTML, out)).parse(reader);
            return out.toString();
        } catch (XMLStreamException e) {
            log.info("Error parsing XML: {}", e.getMessage());
            throw e;
        }
    }

    public static String formatXML(InputStream inputStream, boolean asHTML) throws Exception {
        try {
            StringWriter out = new StringWriter(STRING_BUFFER_INITIAL_CAPACITY_FOR_XML);
            new XMLBeautifier(getXmlBeautifierFormatter(asHTML, out)).parse(inputStream);
            return out.toString();
        } catch (IOException e) {
            log.info("Error parsing XML: {}", e.getMessage());
            throw e;
        }
    }

    private static @NotNull XMLBeautifierFormatter getXmlBeautifierFormatter(boolean asHTML, StringWriter out) {
        return asHTML ? new HtmlBeautifierFormatter(out, 0) : new StandardXMLBeautifierFormatter(out, 4);
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static String globToRegExp(String glob) {
        StringBuilder buf = new StringBuilder();
        buf.append("^");
        for (int i = 0; i < glob.length(); i++) {
            appendReplacement(glob.charAt(i), buf);
        }
        buf.append("$");
        return buf.toString();
    }

    private static void appendReplacement(char c, StringBuilder buf) {
        for (int j = 0; j < source.length; j++) {
            if (c == source[j]) {
                buf.append(replace[j]);
                return;
            }
        }
        buf.append(c);
    }

    public static String toEnglishList(String conjunction, String... args) {
        ArrayList<String> l = new ArrayList<>();
        for (String arg : args)
            if (arg != null && !arg.isEmpty())
                l.add(arg);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            sb.append(l.get(i));
            if (i == l.size() - 2) {
                sb.append(" ");
                sb.append(conjunction);
                sb.append(" ");
            }
            if (i < l.size() - 2)
                sb.append(", ");
        }
        return sb.toString();
    }

    public static String capitalizeFirstCharacter(String english) {
        if (english.isEmpty())
            return "";
        return (english.charAt(0) + english.substring(1)).toUpperCase();
    }

    /**
     * Checks whether s is a valid (well-formed and balanced) XML snippet.
     */
    public static boolean isValidXMLSnippet(String s) {
        try {
            XMLEventReader parser = XMLInputFactoryFactory.inputFactory()
                    .createXMLEventReader(new StringReader("<a>" + s + "</a>"));
            XMLEvent event = null;
            try {
                while (parser.hasNext()) {
                    event = parser.nextEvent();
                }
                return event != null && event.isEndDocument();
            } finally {
                try {
                    parser.close();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            log.debug("Invalid XML snippet.", e);
            return false;
        }
    }

    public static String linkURL(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            url = StringEscapeUtils.escapeHtml4(url);
            return "<a href=\"" + url + "\">" + url + "</a>";
        }
        return StringEscapeUtils.escapeHtml4(url);
    }

    public static String removeFinalChar(String s) {
        if (s == null || s.isEmpty())
            return "";
        int end = s.offsetByCodePoints(s.length(), -1);
        return s.substring(0, end);
    }

    /**
     * Counts from 1 cause this is needed for getting lines from Javascript source code.
     *
     * @param s          Multiline string
     * @param lineNumber number of line to return. Counts from 1
     * @return line
     */
    public static String getLineFromMultilineString(String s, int lineNumber) {
        String[] lines = s.split("\\r?\\n");
        if (lineNumber <= 0 || lineNumber > lines.length) return "";
        return lines[lineNumber - 1];
    }

    public static String escapeQuotes(String s) {
        return s.replace("\"", "\\\"");
    }

    /**
     * Adjusts the indentation of each line in a multiline string to match the minimum indentation found.
     *
     * @param multilineString The input multiline string to process.
     * @return A string with adjusted indentation.
     */
    public static String unifyIndent(String multilineString) {
        String[] lines = multilineString.split("\r?\n");
        return trimLines(lines, getMinIndent(lines)).toString().replaceFirst("\\s*$", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * how many characters to remove from start to drop `columns` of indent (tab = 4).
     */
    private static int charsToRemoveForColumns(String s, int columns) {
        int cols = 0, i = 0;
        while (i < s.length() && cols < columns) {
            char c = s.charAt(i);
            if (c == ' ') {
                cols += 1;
                i++;
            } else if (c == '\t') {
                cols += 4;
                i++;
            } else break;
        }
        return i;
    }

    /**
     * Trims excess indentation from each line based on minIndent (columns),
     * drops leading/trailing blank lines, preserves internal blank lines,
     * and produces no trailing newline.
     */
    public static StringBuilder trimLines(String[] lines, int minIndent) {
        // find first/last non-blank lines
        int first = -1, last = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!isBlank(lines[i])) {
                first = i;
                break;
            }
        }
        if (first == -1) return new StringBuilder(); // all blank
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!isBlank(lines[i])) {
                last = i;
                break;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = first; i <= last; i++) {
            String line = lines[i];
            if (!isBlank(line)) {
                int curIndent = getCurrentIndent(line);               // tab = 4 per your method
                int cutCols = Math.min(minIndent, curIndent);
                int cutChars = charsToRemoveForColumns(line, cutCols);
                result.append(line.substring(cutChars));
            }
            if (i < last) result.append("\n"); // newline only between kept lines
        }
        return result;
    }

    /**
     * Calculates the current indentation level (number of leading spaces) of a given line.
     *
     * @param line The line to calculate the indentation for.
     * @return The number of leading spaces in the line.
     */
    public static int getCurrentIndent(String line) {
        return line.length() - line.replaceFirst("^\\s+", "").length();
    }

    /**
     * Determines the minimum indentation level (number of leading spaces) across all non-empty lines in an array of lines.
     *
     * @param lines The array of lines to analyze.
     * @return The minimum indent level found among the lines.
     */
    public static int getMinIndent(String[] lines) {
        if (lines == null || lines.length == 0)
            return 0;
        int minIndent = MAX_VALUE;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int leadingSpaces = getCurrentIndent(line);
                minIndent = Math.min(minIndent, leadingSpaces);
            }
        }
        return minIndent == MAX_VALUE ? 0 : minIndent;
    }

}
