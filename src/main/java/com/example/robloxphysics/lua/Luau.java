package com.example.robloxphysics.lua;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Source-to-source translator from the common subset of Luau syntax to Lua 5.2 (which LuaJ runs).
 *
 * Supported Luau extensions:
 *  - compound assignment: {@code += -= *= /= %= ^= ..=}
 *  - {@code continue} inside while / for / repeat loops
 *  - type annotations on locals, parameters, return types, for-loop variables, generics
 *  - {@code type X = ...} / {@code export type X = ...} declarations
 *  - type casts {@code expr :: Type}
 *  - interpolated strings {@code `Hello {name}!`}
 *  - floor division {@code a // b} (as {@code math.floor(a / b)} for simple operands)
 *  - {@code !=} (common typo from other languages; Luau itself rejects it, we are lenient)
 */
public final class Luau {
    private Luau() {}

    enum K { NAME, KEYWORD, NUMBER, STRING, INTERP, SYMBOL, COMMENT, WS, EOF }

    static final class Tok {
        K k;
        String s;
        Tok(K k, String s) { this.k = k; this.s = s; }
        boolean is(String v) { return (k == K.SYMBOL || k == K.KEYWORD) && s.equals(v); }
        boolean isName(String v) { return k == K.NAME && s.equals(v); }
        public String toString() { return k + ":" + s; }
    }

    private static final Set<String> KEYWORDS = Set.of(
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if", "in",
            "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while");

    private static final String[] SYMBOLS = {
            "...", "..=", "//=", "::", "==", "~=", "!=", "<=", ">=", "..", "+=", "-=", "*=", "/=", "%=", "^=", "//", "->",
            "+", "-", "*", "/", "%", "^", "#", "<", ">", "=", "(", ")", "{", "}", "[", "]", ";", ":", ",", ".", "?", "|", "&", "@"
    };

    public static String translate(String src) {
        List<Tok> toks = lex(src);
        toks = stripTypes(toks);
        toks = compoundAssign(toks);
        toks = continues(toks);
        toks = misc(toks);
        StringBuilder sb = new StringBuilder(src.length() + 64);
        for (Tok t : toks) if (t.k != K.EOF) sb.append(t.s);
        return sb.toString();
    }

    // ------------------------------------------------------------------ lexer

    static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            int st = i;
            if (Character.isWhitespace(c)) {
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                out.add(new Tok(K.WS, s.substring(st, i)));
            } else if (c == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
                i += 2;
                int lvl = longBracketLevel(s, i);
                if (lvl >= 0) {
                    i = skipLongBracket(s, i, lvl);
                } else {
                    while (i < n && s.charAt(i) != '\n') i++;
                }
                out.add(new Tok(K.COMMENT, s.substring(st, i)));
            } else if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                String w = s.substring(st, i);
                out.add(new Tok(KEYWORDS.contains(w) ? K.KEYWORD : K.NAME, w));
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                if (c == '0' && i + 1 < n && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
                    i += 2;
                    while (i < n && (Character.digit(s.charAt(i), 16) >= 0 || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
                } else if (c == '0' && i + 1 < n && (s.charAt(i + 1) == 'b' || s.charAt(i + 1) == 'B')) {
                    i += 2;
                    int bs = i;
                    while (i < n && (s.charAt(i) == '0' || s.charAt(i) == '1' || s.charAt(i) == '_')) i++;
                    String bin = s.substring(bs, i).replace("_", "");
                    out.add(new Tok(K.NUMBER, bin.isEmpty() ? "0" : Long.toString(Long.parseLong(bin, 2))));
                    continue;
                } else {
                    while (i < n) {
                        char d = s.charAt(i);
                        if (Character.isDigit(d) || d == '.' || d == '_') i++;
                        else if ((d == 'e' || d == 'E')) {
                            i++;
                            if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                        } else break;
                    }
                }
                out.add(new Tok(K.NUMBER, s.substring(st, i).replace("_", "")));
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < n && s.charAt(i) != c) {
                    if (s.charAt(i) == '\\') i++;
                    else if (s.charAt(i) == '\n') break;
                    i++;
                }
                i = Math.min(n, i + 1);
                out.add(new Tok(K.STRING, s.substring(st, i)));
            } else if (c == '[' && longBracketLevel(s, i) >= 0) {
                i = skipLongBracket(s, i, longBracketLevel(s, i));
                out.add(new Tok(K.STRING, s.substring(st, i)));
            } else if (c == '`') {
                i++;
                int depth = 0;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\\') { i += 2; continue; }
                    if (depth == 0 && d == '`') break;
                    if (d == '{') depth++;
                    else if (d == '}' && depth > 0) depth--;
                    i++;
                }
                i = Math.min(n, i + 1);
                out.add(new Tok(K.INTERP, s.substring(st, i)));
            } else {
                String sym = null;
                for (String sy : SYMBOLS) {
                    if (s.startsWith(sy, i)) { sym = sy; break; }
                }
                if (sym == null) sym = String.valueOf(c);
                i += sym.length();
                out.add(new Tok(K.SYMBOL, sym));
            }
        }
        out.add(new Tok(K.EOF, ""));
        return out;
    }

    /** At s[i] == '[' : returns the level of a long bracket ([[ = 0, [=[ = 1), or -1. */
    private static int longBracketLevel(String s, int i) {
        if (i >= s.length() || s.charAt(i) != '[') return -1;
        int j = i + 1, lvl = 0;
        while (j < s.length() && s.charAt(j) == '=') { j++; lvl++; }
        return j < s.length() && s.charAt(j) == '[' ? lvl : -1;
    }

    private static int skipLongBracket(String s, int i, int lvl) {
        String close = "]" + "=".repeat(lvl) + "]";
        int e = s.indexOf(close, i + lvl + 2);
        return e < 0 ? s.length() : e + close.length();
    }

    // ------------------------------------------------------------------ helpers

    /** index of the next significant (non ws/comment) token at or after i */
    static int next(List<Tok> t, int i) {
        while (i < t.size() && (t.get(i).k == K.WS || t.get(i).k == K.COMMENT)) i++;
        return Math.min(i, t.size() - 1);
    }

    static int prev(List<Tok> t, int i) {
        while (i >= 0 && (t.get(i).k == K.WS || t.get(i).k == K.COMMENT)) i--;
        return i;
    }

    /** Given index of an opening bracket, returns index of matching close. */
    static int match(List<Tok> t, int i) {
        String open = t.get(i).s;
        String close = open.equals("(") ? ")" : open.equals("[") ? "]" : open.equals("{") ? "}" : ">";
        int depth = 0;
        for (int j = i; j < t.size(); j++) {
            Tok x = t.get(j);
            if (x.k != K.SYMBOL) continue;
            if (x.s.equals(open)) depth++;
            else if (x.s.equals(close)) {
                if (--depth == 0) return j;
            }
        }
        return t.size() - 1;
    }

    /**
     * Skip a Luau type expression starting at significant token index i. Returns the index just past the
     * type (which may be whitespace).
     */
    static int skipType(List<Tok> t, int i) {
        i = next(t, i);
        i = skipTypePrimary(t, i);
        while (true) {
            int j = next(t, i);
            Tok x = t.get(j);
            if (x.is("?")) { i = j + 1; continue; }
            if (x.is("|") || x.is("&")) { i = skipTypePrimary(t, next(t, j + 1)); continue; }
            if (x.is("->")) { i = skipType(t, j + 1); continue; }
            return i;
        }
    }

    private static int skipTypePrimary(List<Tok> t, int i) {
        Tok x = t.get(i);
        if (x.is("?") || x.is("|") || x.is("&")) {
            // leading | in unions: type T = | A | B
            return skipTypePrimary(t, next(t, i + 1));
        }
        if (x.is("(") || x.is("{") || x.is("[")) {
            int e = match(t, i) + 1;
            int j = next(t, e);
            if (t.get(j).is("->")) return skipType(t, j + 1);
            return e;
        }
        if (x.is("<")) { // generic function type <T>(...) -> ...
            return skipTypePrimary(t, next(t, match(t, i) + 1));
        }
        if (x.k == K.STRING || x.k == K.NUMBER || x.is("nil") || x.is("true") || x.is("false") || x.is("...")) {
            if (x.is("...")) {
                int j = next(t, i + 1);
                if (t.get(j).k == K.NAME) return skipTypePrimary(t, j);
            }
            return i + 1;
        }
        if (x.k == K.NAME) {
            int e = i + 1;
            if (x.s.equals("typeof")) {
                int j = next(t, e);
                if (t.get(j).is("(")) return match(t, j) + 1;
            }
            while (true) {
                int j = next(t, e);
                if (t.get(j).is(".") && t.get(next(t, j + 1)).k == K.NAME) { e = next(t, j + 1) + 1; continue; }
                if (t.get(j).is("<")) { e = matchAngle(t, j) + 1; continue; }
                if (t.get(j).is("...")) { e = j + 1; continue; }
                return e;
            }
        }
        if (x.k == K.KEYWORD && x.s.equals("function")) return i + 1;
        return i;
    }

    private static int matchAngle(List<Tok> t, int i) {
        int depth = 0;
        for (int j = i; j < t.size(); j++) {
            Tok x = t.get(j);
            if (x.k != K.SYMBOL) continue;
            if (x.s.equals("<")) depth++;
            else if (x.s.equals(">")) { if (--depth == 0) return j; }
            else if (x.s.equals("(") || x.s.equals("{") || x.s.equals("[")) j = match(t, j);
        }
        return t.size() - 1;
    }

    private static void blank(List<Tok> t, int from, int to) {
        for (int j = from; j < to && j < t.size(); j++) {
            Tok x = t.get(j);
            if (x.k == K.EOF) break;
            // keep newlines so line numbers in errors stay correct
            long nl = x.s.chars().filter(ch -> ch == '\n').count();
            x.k = K.WS;
            x.s = nl > 0 ? "\n".repeat((int) nl) : " ";
        }
    }

    // ------------------------------------------------------------------ passes

    static List<Tok> stripTypes(List<Tok> t) {
        for (int i = 0; i < t.size(); i++) {
            Tok x = t.get(i);
            int p = prev(t, i - 1);
            Tok pt = p >= 0 ? t.get(p) : null;
            boolean stmtStart = pt == null || pt.k == K.KEYWORD && !pt.is("nil") && !pt.is("true") && !pt.is("false")
                    || pt.is(")") || pt.is("]") || pt.is("}") || pt.is(";") || pt.k == K.NAME || pt.k == K.STRING || pt.k == K.NUMBER;

            // type / export type declarations
            if (x.k == K.NAME && (x.s.equals("type") || x.s.equals("export")) && stmtStart) {
                int j = next(t, i + 1);
                int nameIdx = j;
                if (x.s.equals("export")) {
                    if (!t.get(j).isName("type")) continue;
                    nameIdx = next(t, j + 1);
                }
                if (t.get(nameIdx).k != K.NAME) continue;
                int after = next(t, nameIdx + 1);
                if (t.get(after).is("<")) after = next(t, matchAngle(t, after) + 1);
                if (!t.get(after).is("=")) continue;
                int end = skipType(t, after + 1);
                blank(t, i, end);
                i = end - 1;
                continue;
            }

            if (x.is("local")) {
                int j = next(t, i + 1);
                if (t.get(j).is("function")) continue;
                // local a: T, b: U = ...
                while (t.get(j).k == K.NAME) {
                    int k = next(t, j + 1);
                    if (t.get(k).is("<")) { // attribs like <const> (Lua 5.4) - not supported by 5.2, strip
                        int e = matchAngle(t, k);
                        blank(t, k, e + 1);
                        k = next(t, e + 1);
                    }
                    if (t.get(k).is(":")) {
                        int e = skipType(t, k + 1);
                        blank(t, k, e);
                        k = next(t, e);
                    }
                    if (t.get(k).is(",")) { j = next(t, k + 1); continue; }
                    break;
                }
                continue;
            }

            if (x.is("for")) {
                int j = next(t, i + 1);
                while (t.get(j).k == K.NAME) {
                    int k = next(t, j + 1);
                    if (t.get(k).is(":")) {
                        int e = skipType(t, k + 1);
                        blank(t, k, e);
                        k = next(t, e);
                    }
                    if (t.get(k).is(",")) { j = next(t, k + 1); continue; }
                    break;
                }
                continue;
            }

            if (x.is("function")) {
                int j = next(t, i + 1);
                // function name path a.b:c
                while (t.get(j).k == K.NAME || t.get(j).is(".") || t.get(j).is(":")) j = next(t, j + 1);
                if (t.get(j).is("<")) { // generics
                    int e = matchAngle(t, j);
                    blank(t, j, e + 1);
                    j = next(t, e + 1);
                }
                if (!t.get(j).is("(")) continue;
                int close = match(t, j);
                // params
                int k = next(t, j + 1);
                while (k < close) {
                    Tok pk = t.get(k);
                    if (pk.k == K.NAME || pk.is("...")) {
                        int c = next(t, k + 1);
                        if (t.get(c).is(":")) {
                            int e = skipType(t, c + 1);
                            blank(t, c, e);
                            k = next(t, e);
                            continue;
                        }
                    }
                    k = next(t, k + 1);
                }
                close = match(t, j);
                int r = next(t, close + 1);
                if (t.get(r).is(":")) {
                    int e = skipType(t, r + 1);
                    blank(t, r, e);
                }
                continue;
            }

            // casts
            if (x.is("::")) {
                int e = skipType(t, i + 1);
                blank(t, i, e);
            }
        }
        return t;
    }

    private static final Set<String> COMPOUND = Set.of("+=", "-=", "*=", "/=", "%=", "^=", "..=", "//=");
    private static final Set<String> STMT_BREAK_KW = Set.of("do", "then", "else", "end", "repeat", "break", "return", "until", "elseif");

    /** a.b[c] += expr  ->  a.b[c] = a.b[c] + (expr) */
    static List<Tok> compoundAssign(List<Tok> t) {
        List<Tok> out = new ArrayList<>(t);
        for (int i = 0; i < out.size(); i++) {
            Tok x = out.get(i);
            if (x.k != K.SYMBOL || !COMPOUND.contains(x.s)) continue;
            // find lhs start: walk back over a suffixed expression at bracket depth 0
            int depth = 0;
            int j = i - 1;
            int start = i;
            for (; j >= 0; j--) {
                Tok y = out.get(j);
                if (y.k == K.WS || y.k == K.COMMENT) {
                    if (depth == 0 && y.s.contains("\n")) {
                        // a newline only ends the lhs if the thing before it could end a statement
                        start = j + 1;
                        int pj = prev(out, j - 1);
                        if (pj < 0) break;
                        Tok pt = out.get(pj);
                        if (pt.is(".") || pt.is(":") || pt.is("[")) continue;
                        break;
                    }
                    continue;
                }
                if (y.is(")") || y.is("]")) { depth++; continue; }
                if (y.is("(") || y.is("[")) { depth--; if (depth < 0) { start = j + 1; break; } continue; }
                if (depth > 0) continue;
                if (y.k == K.NAME || y.is(".") || y.is(":")) { start = j; continue; }
                start = j + 1;
                break;
            }
            if (j < 0) start = 0;
            start = next(out, start);
            // find rhs end: until newline at depth 0 that is followed by a statement start, or ; or end keyword
            int k = i + 1;
            depth = 0;
            int end = out.size() - 1;
            for (; k < out.size(); k++) {
                Tok y = out.get(k);
                if (y.k == K.EOF) { end = k; break; }
                if (y.is("(") || y.is("[") || y.is("{")) depth++;
                else if (y.is(")") || y.is("]") || y.is("}")) {
                    if (depth == 0) { end = k; break; }
                    depth--;
                } else if (depth == 0) {
                    if (y.is(";") || (y.k == K.KEYWORD && STMT_BREAK_KW.contains(y.s))) { end = k; break; }
                    if (y.k == K.WS && y.s.contains("\n")) {
                        int nx = next(out, k);
                        Tok nt = out.get(nx);
                        int pv = prev(out, k - 1);
                        Tok pt = out.get(pv);
                        boolean cont = isBinaryOp(pt) || isBinaryOp(nt) && !nt.is("-") || pv <= i;
                        if (!cont) { end = k; break; }
                    }
                    if (y.is("local") || y.is("function") || y.is("if") || y.is("while") || y.is("for")) {
                        if (k > next(out, i + 1)) { end = k; break; }
                    }
                }
            }
            StringBuilder lhs = new StringBuilder();
            for (int a = start; a < i; a++) {
                Tok y = out.get(a);
                if (y.k != K.COMMENT) lhs.append(y.s);
            }
            String l = lhs.toString().trim();
            String op = x.s.substring(0, x.s.length() - 1);
            // replace op with "= lhs op ("
            if (op.equals("//")) {
                x.s = "= math.floor(" + l + " / (";
                out.add(end, new Tok(K.SYMBOL, "))"));
            } else {
                x.s = "= " + l + " " + op + " (";
                out.add(end, new Tok(K.SYMBOL, ")"));
            }
            x.k = K.WS; // treat as opaque text from here on
            // move trailing whitespace before inserted paren? (cosmetic only)
        }
        return out;
    }

    private static boolean isBinaryOp(Tok t) {
        if (t.k == K.SYMBOL) {
            switch (t.s) {
                case "+": case "-": case "*": case "/": case "%": case "^": case "..": case "==": case "~=": case "<":
                case ">": case "<=": case ">=": case ",": case "=": case "//": case "!=":
                    return true;
                default:
                    return false;
            }
        }
        return t.is("and") || t.is("or") || t.is("not");
    }

    /** continue -> goto __continueN ; inserts ::__continueN:: before the loop's end/until. */
    static List<Tok> continues(List<Tok> t) {
        final class Block {
            final String kind; // FUNC, LOOP, LOOPDO (while/for awaiting do), DO, IF, REPEAT
            String label;
            Block(String k) { kind = k; }
        }
        Deque<Block> stack = new ArrayDeque<>();
        List<Tok> out = new ArrayList<>(t.size() + 8);
        int counter = 0;
        for (int i = 0; i < t.size(); i++) {
            Tok x = t.get(i);
            if (x.k == K.KEYWORD) {
                switch (x.s) {
                    case "function": stack.push(new Block("FUNC")); break;
                    case "if": stack.push(new Block("IF")); break;
                    case "while": case "for": stack.push(new Block("LOOPDO")); break;
                    case "repeat": stack.push(new Block("REPEAT")); break;
                    case "do":
                        if (!stack.isEmpty() && stack.peek().kind.equals("LOOPDO")) {
                            Block b = stack.pop();
                            Block nb = new Block("LOOP");
                            nb.label = b.label;
                            stack.push(nb);
                        } else stack.push(new Block("DO"));
                        break;
                    case "end":
                        if (!stack.isEmpty()) {
                            Block b = stack.pop();
                            if (b.label != null && b.kind.equals("LOOP")) {
                                out.add(new Tok(K.SYMBOL, "::" + b.label + ":: "));
                            }
                        }
                        break;
                    case "until":
                        if (!stack.isEmpty()) {
                            Block b = stack.pop();
                            if (b.label != null) out.add(new Tok(K.SYMBOL, "::" + b.label + ":: "));
                        }
                        break;
                    default:
                }
            } else if (x.isName("continue")) {
                int nx = next(t, i + 1);
                Tok nt = t.get(nx);
                boolean asName = nt.is("=") || nt.is(".") || nt.is("[") || nt.is(":") || nt.is("(") || nt.is(",")
                        || nt.is("{") || nt.k == K.STRING || COMPOUND.contains(nt.s);
                int pv = prev(t, i - 1);
                if (pv >= 0 && (t.get(pv).is(".") || t.get(pv).is(":") || t.get(pv).is("local"))) asName = true;
                if (!asName) {
                    Block loop = null;
                    for (Block b : stack) {
                        if (b.kind.equals("FUNC")) break;
                        if (b.kind.equals("LOOP") || b.kind.equals("REPEAT")) { loop = b; break; }
                    }
                    if (loop != null) {
                        if (loop.label == null) loop.label = "__continue" + (counter++);
                        out.add(new Tok(K.SYMBOL, "goto " + loop.label));
                        continue;
                    }
                }
            }
            out.add(x);
        }
        return out;
    }

    /** Interpolated strings, != , and // */
    static List<Tok> misc(List<Tok> t) {
        List<Tok> out = new ArrayList<>(t.size());
        for (int i = 0; i < t.size(); i++) {
            Tok x = t.get(i);
            if (x.k == K.INTERP) {
                x = new Tok(K.STRING, interp(x.s));
            } else if (x.is("!=")) {
                x = new Tok(K.SYMBOL, "~=");
            } else if (x.is("//")) {
                // a // b  ->  math.floor(a / b) for simple operands (name, number, call-free member chain)
                int a = prev(out, out.size() - 1);
                int aStart = a;
                while (aStart > 0) {
                    int p = prev(out, aStart - 1);
                    if (p >= 0 && (out.get(p).is(".") || out.get(p).k == K.NAME && out.get(aStart).is("."))) aStart = p;
                    else break;
                }
                int b = next(t, i + 1);
                int bEnd = b;
                while (bEnd + 2 < t.size() && t.get(bEnd + 1).is(".") && t.get(bEnd + 2).k == K.NAME) bEnd += 2;
                if (a >= 0 && (out.get(a).k == K.NAME || out.get(a).k == K.NUMBER || out.get(a).is(")"))
                        && (t.get(b).k == K.NAME || t.get(b).k == K.NUMBER)) {
                    if (out.get(a).is(")")) {
                        // find matching (
                        int depth = 0;
                        for (int j = a; j >= 0; j--) {
                            if (out.get(j).is(")")) depth++;
                            else if (out.get(j).is("(")) { if (--depth == 0) { aStart = j; break; } }
                        }
                        int p = prev(out, aStart - 1);
                        if (p >= 0 && out.get(p).k == K.NAME) aStart = p;
                    }
                    out.add(aStart, new Tok(K.SYMBOL, "math.floor("));
                    out.add(new Tok(K.SYMBOL, "/"));
                    for (int j = i + 1; j <= bEnd; j++) out.add(t.get(j));
                    out.add(new Tok(K.SYMBOL, ")"));
                    i = bEnd;
                    continue;
                }
                x = new Tok(K.SYMBOL, "/"); // fallback: plain division
            }
            out.add(x);
        }
        return out;
    }

    /** `a {b} c` -> ("a " .. tostring(b) .. " c") */
    static String interp(String s) {
        String body = s.substring(1, s.endsWith("`") && s.length() > 1 ? s.length() - 1 : s.length());
        List<String> parts = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char d = body.charAt(i + 1);
                if (d == '`' || d == '{' || d == '}') lit.append(d);
                else lit.append(c).append(d);
                i += 2;
            } else if (c == '{') {
                int depth = 1, j = i + 1;
                while (j < body.length() && depth > 0) {
                    char e = body.charAt(j);
                    if (e == '{') depth++;
                    else if (e == '}') depth--;
                    if (depth > 0) j++;
                }
                if (lit.length() > 0) { parts.add(quote(lit.toString())); lit.setLength(0); }
                parts.add("tostring(" + translate(body.substring(i + 1, Math.min(j, body.length()))) + ")");
                i = j + 1;
            } else if (c == '"') {
                lit.append("\\\"");
                i++;
            } else if (c == '\n') {
                lit.append("\\n");
                i++;
            } else {
                lit.append(c);
                i++;
            }
        }
        if (lit.length() > 0 || parts.isEmpty()) parts.add(quote(lit.toString()));
        return "(" + String.join(" .. ", parts) + ")";
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }
}
