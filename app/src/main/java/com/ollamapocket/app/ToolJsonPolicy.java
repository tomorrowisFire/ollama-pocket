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
        requireObject(raw,MAX_CHARS,MAX_DEPTH,MAX_NODES);
    }

    // Reply limits are separate; never widen the admission of tool arguments.
    static final int MAX_REPLY_CHARS=524288;
    static void requireReplyObject(String raw) {
        requireObject(raw,MAX_REPLY_CHARS,16,8192);
    }
    /** Engine records have their own wire bounds; tool arguments and tool replies remain unchanged. */
    static void requireEngineObject(String raw,int maxChars) {
        if(maxChars<1||maxChars>1_000_000)throw new IllegalArgumentException("Engine JSON limit");
        requireObject(raw,maxChars,24,65_536);
    }
    /** Provider JSON includes large catalogs; do not widen tool or local engine admission. */
    static void requireCloudObject(String raw,int maxChars) {
        if(maxChars<1||maxChars>2_000_000)throw new IllegalArgumentException("Cloud JSON limit");
        requireObject(raw,maxChars,32,250_000);
    }
    private static void requireObject(String raw,int maxChars,int maxDepth,int maxNodes) {
        if (raw == null || raw.length() > maxChars) throw new IllegalArgumentException("Tool JSON size");
        Parser p = new Parser(raw,maxDepth,maxNodes);
        p.space();
        if (!p.at('{')) throw p.invalid();
        p.value(0);
        p.space();
        if (p.pos != raw.length()) throw p.invalid();
    }

    private static final class Parser {
        final String text;
        final int maxDepth,maxNodes;
        int pos, nodes;
        Parser(String text,int maxDepth,int maxNodes) { this.text=text;this.maxDepth=maxDepth;this.maxNodes=maxNodes; }
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
            if (depth > maxDepth || ++nodes > maxNodes) throw invalid();
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
