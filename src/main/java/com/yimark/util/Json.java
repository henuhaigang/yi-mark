package com.yimark.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-free JSON support for the flat string-keyed documents SecureDoc writes
 * (manifests and the trust store). Nested structures are skipped by the parser.
 */
public final class Json {

    private Json() {}

    public static String writeObject(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append("  ").append(quote(e.getKey())).append(": ").append(quote(e.getValue()));
            if (++i < fields.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        return sb.append("}\n").toString();
    }

    public static String quote(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        parser.expect('{');
        parser.skipWhitespace();
        if (parser.peek() == '}') {
            parser.next();
            return out;
        }
        while (true) {
            parser.skipWhitespace();
            String key = parser.readString();
            parser.skipWhitespace();
            parser.expect(':');
            parser.skipWhitespace();
            if (parser.peek() == '"') {
                out.put(key, parser.readString());
            } else {
                parser.skipValue();
            }
            parser.skipWhitespace();
            char c = parser.next();
            if (c == '}') {
                return out;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected , or } at offset " + parser.pos);
            }
        }
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        char peek() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        char next() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            return src.charAt(pos++);
        }

        void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("expected " + c + " at offset " + (pos - 1));
            }
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char esc = next();
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + esc);
                }
            }
        }

        void skipValue() {
            skipWhitespace();
            char c = peek();
            if (c == '"') {
                readString();
            } else if (c == '{' || c == '[') {
                char open = next();
                char close = open == '{' ? '}' : ']';
                int depth = 1;
                while (depth > 0) {
                    char d = next();
                    if (d == '"') {
                        pos--;
                        readString();
                    } else if (d == open) {
                        depth++;
                    } else if (d == close) {
                        depth--;
                    }
                }
            } else {
                while (pos < src.length() && ",}] \t\r\n".indexOf(src.charAt(pos)) < 0) {
                    pos++;
                }
            }
        }
    }

    public static List<String> parseStringArray(String json) {
        List<String> out = new ArrayList<>();
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        parser.expect('[');
        parser.skipWhitespace();
        if (parser.peek() == ']') {
            parser.next();
            return out;
        }
        while (true) {
            parser.skipWhitespace();
            if (parser.peek() == '"') {
                out.add(parser.readString());
            } else {
                parser.skipValue();
            }
            parser.skipWhitespace();
            char c = parser.next();
            if (c == ']') {
                return out;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected , or ] at offset " + parser.pos);
            }
        }
    }

    public static String writeStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(quote(values.get(i)));
        }
        return sb.append(']').toString();
    }
}
