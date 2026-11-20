package cn.inovance.sqlfmt.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 行模型。规则只修改 Line.indent（行首空白）、WORD 的 text、逗号位置，
 * 或标记 removed 删除纯空行。render() 保证可完整还原为源码文本。
 */
public final class SqlDocument {

    public static final class Line {
        public String indent = "";
        public final List<Token> tokens = new ArrayList<>();
        /** G2：整行处于字符串/注释内（无代码 token），所有规则跳过。 */
        public boolean guarded = false;
        /** R20 删除标记。 */
        public boolean removed = false;

        public boolean isBlank() {
            return !removed && indent.isEmpty() && tokens.isEmpty();
        }

        public List<Token> codeTokens() {
            List<Token> result = new ArrayList<>();
            for (Token t : tokens) if (t.isCode()) result.add(t);
            return result;
        }

        public Token firstCode() {
            for (Token t : tokens) if (t.isCode()) return t;
            return null;
        }

        public String firstWord() {
            Token t = firstCode();
            return (t != null && t.type == Token.Type.WORD)
                    ? t.text.toUpperCase(Locale.ROOT) : null;
        }

        public boolean startsWithComma() {
            Token t = firstCode();
            return t != null && t.isPunct(",");
        }

        public boolean isPureComment() {
            for (Token t : tokens) if (t.isCode()) return false;
            return true;
        }

        public int indentLen() {
            return indent.length();
        }

        public void setIndent(int n) {
            indent = spaces(n);
        }

        public String render() {
            StringBuilder sb = new StringBuilder(indent);
            for (Token t : tokens) sb.append(t.text);
            return sb.toString();
        }

        public static String spaces(int n) {
            return " ".repeat(Math.max(0, n));
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private final boolean trailingNewline;
    /** 原始文件换行风格：\r\n / \n / \r。render() 时原样恢复，保证 CRLF 文件不被改成 LF。 */
    private final String newline;

    private SqlDocument(boolean trailingNewline, String newline) {
        this.trailingNewline = trailingNewline;
        this.newline = newline;
    }

    public static SqlDocument parse(String src) {
        String text = src == null ? "" : src;
        String nl = text.contains("\r\n") ? "\r\n"
                : (text.indexOf('\r') >= 0 ? "\r" : "\n");
        boolean endsWithNl = text.endsWith("\n") || text.endsWith("\r");
        SqlDocument doc = new SqlDocument(endsWithNl, nl);
        Line cur = new Line();
        for (Token t : SqlLexer.tokenize(text)) {
            if (t.type == Token.Type.NL) {
                doc.lines.add(cur);
                cur = new Line();
                continue;
            }
            if (t.type == Token.Type.WS && cur.indent.isEmpty() && cur.tokens.isEmpty()) {
                cur.indent = t.text;      // 行首空白独立保存
            } else {
                cur.tokens.add(t);
            }
        }
        doc.lines.add(cur);
        // 以换行结尾时，tokenize 会多产生一个虚拟空行，去掉它
        if (endsWithNl && !doc.lines.isEmpty()
                && doc.lines.get(doc.lines.size() - 1).isBlank()) {
            doc.lines.remove(doc.lines.size() - 1);
        }
        doc.markGuarded();
        return doc;
    }

    private void markGuarded() {
        for (Line l : lines) {
            boolean hasCode = false;
            boolean hasProtected = false;
            for (Token t : l.tokens) {
                if (t.isCode()) hasCode = true;
                if (t.isProtected()) hasProtected = true;
            }
            l.guarded = hasProtected && !hasCode;
        }
    }

    public List<Line> lines() {
        return lines;
    }

    public int size() {
        return lines.size();
    }

    public Line get(int i) {
        return lines.get(i);
    }

    /** R18 使用：在 idx 之后插入一个新行。 */
    public void insertAfter(int idx, Line line) {
        lines.add(idx + 1, line);
    }

    /** 供规则遍历：过滤掉已标记删除的行。 */
    public List<Line> liveLines() {
        List<Line> result = new ArrayList<>(lines.size());
        for (Line l : lines) if (!l.removed) result.add(l);
        return result;
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Line l : lines) {
            if (l.removed) continue;
            if (!first) sb.append(newline);
            sb.append(l.render());
            first = false;
        }
        if (trailingNewline) sb.append(newline);
        return sb.toString();
    }
}
