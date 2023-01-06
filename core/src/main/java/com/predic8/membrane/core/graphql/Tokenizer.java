package com.predic8.membrane.core.graphql;

import org.apache.commons.io.input.BOMInputStream;

import java.io.*;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;

public class Tokenizer {

    public enum Type {
        PUNCTUATOR,
        NAME,
        INT_VALUE,
        FLOAT_VALUE,
        STRING_VALUE
    }

    private final CountingReader reader;
    private Type type;
    private int intValue;
    private double doubleValue;
    private String stringValue;
    private boolean reverted;
    private boolean eof;

    public Tokenizer(InputStream reader) {
        this.reader = new CountingReader(new BufferedReader(new InputStreamReader(new BOMInputStream(reader))));
    }

    /**
     * Undo the last {@link #advance()}.
     *
     * Note that this can only be used to go *one* token back.
     *
     * Also note that when this method has been called, the second-to-last token cannot actually be inspected. This
     * method only serves the purpose to let the next parsing routine call {@link #advance()} again itself. This can
     * therefore be used to check - again - whether EOF has been reached.
     */
    public void revert() {
        if (reverted)
            throw new IllegalStateException("Cannot double-revert().");
        reverted = true;
    }

    /**
     * Same as {@link #advance()}, but throw EOFException if EOF is reached instead of returning a boolean.
     */
    public void mustAdvance() throws IOException, ParsingException {
        if (!advance())
            throw new ParsingException("Early EOF.", position());
    }

    /**
     * Advances to next token.
     * @return whether another token was found. ('false' signals that EOF has been reached.)
     * @throws EOFException parsing error (EOF while parsing a token, e.g. an unclosed string)
     * @throws ParsingException parsing error (illegal text according to the grammar)
     */
    public boolean advance() throws IOException, ParsingException {
        if (reverted) {
            reverted = false;
            return !eof;
        }
        while(true) {
            int c = reader.read();
            if (c == -1) {
                type = null;
                eof = true;
                return false;
            }
            // Punctuator
            if (c == '!' || c == '$' || c == '&' || c == '(' || c == ')' || c == ':' || c == '=' || c == '@' || c == '[' || c == ']' || c == '{' || c == '|' || c == '}') {
                type = PUNCTUATOR;
                intValue = c;
                return true;
            }
            if (c == '.') {
                reader.mark(2);
                if (reader.read() != '.' || reader.read() != '.')
                    throw new ParsingException("Expected punctuator '...'", reader.position());
                type = PUNCTUATOR;
                intValue = c;
                return true;
            }
            // Name
            if (isNameStart(c)) {
                parseName(c);
                return true;
            }

            if (c == '-' || ('0' <= c && c <= '9')) {
                parseIntOrFloat(c);
                return true;
            }

            if (c == '"') {
                type = STRING_VALUE;
                c = reader.read();
                if (c == -1)
                    throw new EOFException();
                if (c == '"') {
                    reader.mark(2);
                    c = reader.read();
                    if (c != '"') {
                        reader.reset();
                        stringValue = "";
                        return true;
                    }
                    parseBlockString();
                    return true;
                }
                parseSimpleString(c);
                return true;
            }

            // whitespace
            if (c == '\t' || c == ' ')
                continue;

            // line terminator
            if (c == '\n') // 0x0A
                continue;
            if (c == '\r') { // 0x0D
                reader.mark(1);
                c = reader.read();
                if (c == '\n')
                    continue;
                reader.reset();
                continue;
            }

            // comment
            if (c == '#') {
                parseComment();
                continue;
            }

            // comma
            if (c == ',')
                continue;

            throw new ParsingException("Illegal char.", reader.position());
        }
    }

    private void parseComment() throws IOException, ParsingException {
        while(true) {
            reader.mark(1);
            int c = reader.read();
            if (c == -1)
                return;
            if (c == '\r' || c == '\n') {
                reader.reset();
                return;
            }
            if (!isSourceChar(c))
                throw new ParsingException("Invalid char.", reader.position());
        }
    }

    private boolean isNameStart(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private void parseBlockString() throws IOException, ParsingException {
        // parse block string
        StringBuilder value2 = new StringBuilder();
        while(true) {
            int c = reader.read();
            if (c == -1)
                throw new EOFException();
            if (!isSourceChar(c))
                throw new ParsingException("Invalid character in block string.", reader.position());
            if (c == '"') {
                reader.mark(2);
                if (reader.read() == '"' && reader.read() == '"')
                    break;
                reader.reset();
            }
            if (c == '\\') {
                reader.mark(3);
                if (reader.read() == '"' && reader.read() == '"' && reader.read() == '"')
                    value2.append("\"\"\"");
                else {
                    reader.reset();
                    value2.append("\\");
                }
            } else {
                value2.append((char)c);
            }
        }
        stringValue = value2.toString();
    }

    private void parseSimpleString(int c) throws IOException, ParsingException {
        StringBuilder value = new StringBuilder();
        do {
            // c == '"' does not occur here
            if (!isSourceChar(c))
                throw new ParsingException("Found illegal character.", reader.position());
            if (c == '\r' || c == '\n') {
                throw new ParsingException("Found newline while parsing string.", reader.position());
            } else if (c == '\\') {
                c = reader.read();
                if (c == -1)
                    throw new EOFException();
                if (c == '"' || c == '\\' || c == '/')
                    value.append((char)c);
                else if (c == 'n')
                    value.append('\n');
                else if (c == 'r')
                    value.append('\r');
                else if (c == 't')
                    value.append('\t');
                else if (c == 'b')
                    value.append('\b');
                else if (c == 'f')
                    value.append('\f');
                else if (c == 'u') {
                    value.append((char) Integer.parseInt("" + assertHex(reader.read()) + assertHex(reader.read()) + assertHex(reader.read()) + assertHex(reader.read()), 16));
                } else
                    throw new ParsingException("Invalid escaped character '" + c + "'", reader.position());

            } else {
                value.append((char)c);
            }
            c = reader.read();
            if (c == -1)
                throw new EOFException();
        } while (c != '"');
        stringValue = value.toString();
    }

    /**
     * @param c '-' or '0'..'9'
     */
    private void parseIntOrFloat(int c) throws IOException {
        // integerPart
        StringBuilder sb = new StringBuilder();
        boolean hasDot = false;
        boolean hasExp = false;
        while(true) {
            sb.append((char) c);
            reader.mark(2);
            c = reader.read();
            if (!hasDot && c == '.') {
                c = reader.read();
                if (isNameStart(c)) {
                    reader.reset();
                    break;
                }
                // FractionalPart
                hasDot = true;
                sb.append('.');
            }
            if (!hasExp && (c == 'e' || c == 'E')) {
                hasExp = true;
                sb.append((char)c);
                c = reader.read();
                if (c == '+' || c == '-') {
                    sb.append((char)c);
                    c = reader.read();
                }
            }
            if ('0' <= c && c <= '9')
                continue;
            reader.reset();
            break;
        }
        type = hasDot || hasExp ? FLOAT_VALUE : INT_VALUE;
        if (type == INT_VALUE)
            intValue = Integer.parseInt(sb.toString());
        else
            doubleValue = Double.parseDouble(sb.toString());
    }

    private void parseName(int c) throws IOException {
        type = NAME;
        StringBuilder value3 = new StringBuilder();
        while (true) {
            value3.append((char)c);
            reader.mark(1);
            c = reader.read();
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || ('0' <= c && c <= '9'))
                continue;
            reader.reset();
            break;
        }
        stringValue = value3.toString();
    }

    private char assertHex(int c) throws EOFException, ParsingException {
        if (c == -1)
            throw new EOFException();
        if (('0' <= c && c <= '9') || ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F'))
            return (char)c;
        throw new ParsingException("Illegal hex character in escaped string char.", reader.position());
    }

    private boolean isSourceChar(int c) {
        return c == 9 || c == 10 || c == 13 || c >= 32;
    }

    public Type type() {
        if (reverted)
            throw new IllegalStateException();
        if (eof)
            throw new IllegalStateException();
        return type;
    }

    /**
     * If {@link #type()} returned {@code PUNCTUATOR}, this will return the value.
     */
    public int punctuator() {
        if (type != PUNCTUATOR && type != INT_VALUE)
            throw new IllegalStateException();
        if (reverted)
            throw new IllegalStateException();
        if (eof)
            throw new IllegalStateException();
        return intValue;
    }

    /**
     * If {@link #type()} returned {@code NAME} or {@code STRING_VALUE}, this will return the value.
     */
    public String string() {
        if (type != NAME && type != STRING_VALUE)
            throw new IllegalStateException();
        if (reverted)
            throw new IllegalStateException();
        if (eof)
            throw new IllegalStateException();
        return stringValue;
    }

    public int integer() {
        if (type != PUNCTUATOR && type != INT_VALUE)
            throw new IllegalStateException();
        if (reverted)
            throw new IllegalStateException();
        if (eof)
            throw new IllegalStateException();
        return intValue;
    }

    public double float_() {
        if (type != FLOAT_VALUE)
            throw new IllegalStateException();
        if (reverted)
            throw new IllegalStateException();
        if (eof)
            throw new IllegalStateException();
        return doubleValue;
    }

    public String tokenString() {
        return switch(type()) {
            case PUNCTUATOR -> "PUNCTUATOR " + (char)punctuator() + " " + punctuator();
            case INT_VALUE -> "INT_VALUE " + integer();
            case FLOAT_VALUE -> "FLOAT_VALUE " + float_();
            case NAME -> "NAME " + string();
            case STRING_VALUE -> "STRING_VALUE " + string();
        };
    }

    public long position() {
        return reader.position();
    }

}
