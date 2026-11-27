package com.ollamapocket.app;

import java.util.HashSet;
import java.util.Set;

/** Strict, bounded syntax admission for independently reviewed tool arguments. */
final class ToolJsonPolicy {
    static final int MAX_CHARS = 16000, MAX_DEPTH = 8, MAX_NODES = 1024;
    static final int MAX_KEY_CHARS = 128, MAX_KEYS = 128;
    static final int MAX_NUMBER_CHARS = 128, MAX_EXPONENT = 256;
    private ToolJsonPolicy() { }

    static void requireObject(String raw) {
        if (raw == null || raw.length() > MAX_CHARS) throw new IllegalArgumentException("Tool JSON size");
        Parser p = new Parser(raw);
        p.space();
        if (!p.at('{')) throw p.invalid();
        p.value(0);
        p.space();
        if (p.pos != raw.length()) throw p.invalid();
    }

    private static final class Parser {
        final String text;
        int pos, nodes;
        Parser(String text) { this.text = text; }
        IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid tool JSON at " + pos); }
        boolean at(char ch) { return pos < text.length() && text.charAt(pos) == ch; }
        boolean take(char ch) { if (!at(ch)) return false; pos++; return true; }
        void need(char ch) { if (!take(ch)) throw invalid(); }
        void space() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
                pos++;
            }
        }
        void value(int depth) {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES) throw invalid();
            space();
            if (at('{')) object(depth);
            else if (at('[')) array(depth);
            else if (at('"')) string(false);
            else if (at('t')) literal("true");
            else if (at('f')) literal("false");
            else if (at('n')) literal("null");
            else number();
        }
        void object(int depth) {
            need('{'); space();
            if (take('}')) return;
            Set<String> keys = new HashSet<>();
            do {
                space();
                String key = string(true);
                if (keys.size() >= MAX_KEYS || !keys.add(key)) throw invalid();
                space(); need(':'); value(depth + 1); space();
                if (take('}')) return;
            } while (take(','));
            throw invalid();
        }
        void array(int depth) {
            need('['); space();
            if (take(']')) return;
            do {
                value(depth + 1); space();
                if (take(']')) return;
            } while (take(','));
            throw invalid();
        }
        void literal(String word) {
            if (!text.startsWith(word, pos)) throw invalid();
            pos += word.length();
        }
        char unit() {
            if (pos >= text.length()) throw invalid();
            char c = text.charAt(pos++);
            if (c < 0x20) throw invalid();
            if (c != '\\') return c;
            if (pos >= text.length()) throw invalid();
            switch (text.charAt(pos++)) {
                case '"': return '"'; case '\\': return '\\'; case '/': return '/';
                case 'b': return '\b'; case 'f': return '\f'; case 'n': return '\n';
                case 'r': return '\r'; case 't': return '\t';
                case 'u':
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        if (pos >= text.length()) throw invalid();
                        char h = text.charAt(pos++);
                        int d = h >= '0' && h <= '9' ? h-'0' : h >= 'a' && h <= 'f' ? h-'a'+10 : h >= 'A' && h <= 'F' ? h-'A'+10 : -1;
                        if (d < 0) throw invalid();
                        code = code*16+d;
                    }
                    return (char) code;
                default: throw invalid();
            }
        }
        String string(boolean key) {
            need('"');
            StringBuilder out = key ? new StringBuilder() : null;
            while (!at('"')) {
                char c = unit();
                if (Character.isLowSurrogate(c)) throw invalid();
                if (out != null) out.append(c);
                if (Character.isHighSurrogate(c)) {
                    if (at('"')) throw invalid();
                    char low = unit();
                    if (!Character.isLowSurrogate(low)) throw invalid();
                    if (out != null) out.append(low);
                }
                if (out != null && out.length() > MAX_KEY_CHARS) throw invalid();
            }
            need('"');
            return out == null ? null : out.toString();
        }
        boolean digit() { return pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9'; }
        void digits() {
            if (!digit()) throw invalid();
            while (digit()) pos++;
        }
        void number() {
            int start = pos;
            take('-');
            if (!take('0')) digits();
            if (take('.')) digits();
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                if (!digit()) throw invalid();
                int exponent = 0;
                while (digit()) {
                    exponent = exponent*10 + text.charAt(pos++)-'0';
                    if (exponent > MAX_EXPONENT) throw invalid();
                }
            }
            if (pos-start > MAX_NUMBER_CHARS) throw invalid();
        }
    }
}
