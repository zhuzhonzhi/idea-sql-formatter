package cn.inovance.sqlfmt.core.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.Token;

/**
 * R9 / R12 / R13 / R14 / R15 / R16：列表按「首元素列」对齐。
 *
 * 这是启发式规则（默认关闭）：泛化的列对齐可能误伤刻意对齐的 SQL，
 * 手册第 12 章明确记录过 92 行回归。启用前建议先用「检查/预览」动作看差异。
 */
public final class ListAlignRule implements Rule {

    private static final Set<String> TERMINATORS = Set.of(
            "FROM", "WHERE", "GROUP", "ORDER", "HAVING", "UNION", "MINUS", "INTERSECT",
            "CONNECT", "START", "SET", "VALUES", "INTO", "SELECT", "INSERT", "UPDATE",
            "DELETE", "BEGIN", "END", "IF", "LOOP", "EXCEPTION", "ELSIF", "ELSE",
            "WHEN", "THEN");

    @Override
    public String id() {
        return "R9/R12/R13/R14/R15/R16 列表对齐";
    }

    @Override
    public int apply(SqlDocument doc, FormatOptions options) {
        if (!options.listAlign) return 0;
        int changed = 0;
        List<SqlDocument.Line> lines = doc.lines();
        int lastSelectColumn = -1;

        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line line = lines.get(i);
            if (line.removed || line.guarded || line.startsWithComma()) continue;
            String kw = line.firstWord();
            Integer target = null;

            if ("SELECT".equals(kw)) {
                target = columnAfter(line, "SELECT");
                if (target >= 0) lastSelectColumn = target;
            } else if ("FROM".equals(kw)) {
                target = columnAfter(line, "FROM");
            } else if ("SET".equals(kw)) {
                target = columnAfter(line, "SET");
            } else if ("INTO".equals(kw)) {
                // R12：INTO 后字段与 SELECT 首字段同列
                target = lastSelectColumn >= 0 ? lastSelectColumn : columnAfter(line, "INTO");
            } else if ("INSERT".equals(kw)) {
                target = columnAfterParen(line);
            } else if (containsInParen(line)) {
                // R14：IN (...) 元素按 '(' 内首元素列对齐
                target = columnAfterInParen(line);
            }

            if (target == null || target < 2) continue;
            int end = findEnd(lines, i + 1);
            for (int j = i + 1; j < end; j++) {
                SqlDocument.Line item = lines.get(j);
                if (item.removed || item.guarded || !item.startsWithComma()) continue;
                int desired = Math.max(0, target - 2);   // 逗号列 = 首元素列 - 2
                if (item.indentLen() != desired) {
                    item.setIndent(desired);
                    changed++;
                }
            }
            if ("SET".equals(kw)) changed += alignEquals(lines, i, end);
        }
        return changed;
    }

    private static int findEnd(List<SqlDocument.Line> lines, int from) {
        for (int j = from; j < lines.size(); j++) {
            SqlDocument.Line l = lines.get(j);
            if (l.removed) continue;
            String kw = l.firstWord();
            if (kw != null && TERMINATORS.contains(kw)) return j;
        }
        return lines.size();
    }

    /** R16：SET 各行的等号列取最大值后补齐。 */
    private static int alignEquals(List<SqlDocument.Line> lines, int start, int end) {
        List<Integer> columns = new ArrayList<>();
        for (int j = start; j < end; j++) {
            SqlDocument.Line l = lines.get(j);
            if (l.removed || l.guarded) continue;
            int c = l.render().indexOf('=');
            if (c >= 0) columns.add(c);
        }
        if (columns.isEmpty()) return 0;
        int target = 0;
        for (int c : columns) target = Math.max(target, c);
        int changed = 0;
        for (int j = start; j < end; j++) {
            SqlDocument.Line l = lines.get(j);
            if (l.removed || l.guarded) continue;
            int c = l.render().indexOf('=');
            if (c < 0 || c >= target) continue;
            int idx = indexOfEquals(l);
            if (idx < 0) continue;
            int delta = target - c;
            if (idx > 0 && l.tokens.get(idx - 1).type == Token.Type.WS) {
                l.tokens.get(idx - 1).text += SqlDocument.Line.spaces(delta);
            } else {
                l.tokens.add(idx, new Token(Token.Type.WS, SqlDocument.Line.spaces(delta), 0, 0));
            }
            changed++;
        }
        return changed;
    }

    private static int indexOfEquals(SqlDocument.Line line) {
        for (int k = 0; k < line.tokens.size(); k++) {
            if (line.tokens.get(k).isPunct("=")) return k;
        }
        return -1;
    }

    /**
     * 只匹配代码区 WORD token 并累加列号。
     * 不能用 render().indexOf(keyword)：会命中字符串/注释里的同名文字，
     * 且当该行自身被对齐后会形成「列号自反馈」，导致 V5 非幂等。
     */
    private static int columnAfter(SqlDocument.Line line, String keyword) {
        int col = line.indentLen();
        for (int k = 0; k < line.tokens.size(); k++) {
            Token t = line.tokens.get(k);
            if (t.type == Token.Type.WORD && t.text.equalsIgnoreCase(keyword)) {
                return skipWhitespaceTokens(line, k + 1, col + t.text.length());
            }
            col += t.text.length();
        }
        return -1;
    }

    private static int columnAfterParen(SqlDocument.Line line) {
        int col = line.indentLen();
        for (int k = 0; k < line.tokens.size(); k++) {
            Token t = line.tokens.get(k);
            if (t.isPunct("(")) return skipWhitespaceTokens(line, k + 1, col + 1);
            col += t.text.length();
        }
        return -1;
    }

    private static boolean containsInParen(SqlDocument.Line line) {
        List<Token> codes = line.codeTokens();
        for (int k = 0; k + 1 < codes.size(); k++) {
            if (codes.get(k).isWord("IN") && codes.get(k + 1).isPunct("(")) return true;
        }
        return false;
    }

    private static int columnAfterInParen(SqlDocument.Line line) {
        int col = line.indentLen();
        boolean seenIn = false;
        for (int k = 0; k < line.tokens.size(); k++) {
            Token t = line.tokens.get(k);
            if (t.type == Token.Type.WORD && t.text.equalsIgnoreCase("IN")) {
                seenIn = true;
                col += t.text.length();
                continue;
            }
            if (seenIn && t.isPunct("(")) {
                return skipWhitespaceTokens(line, k + 1, col + 1);
            }
            col += t.text.length();
        }
        return -1;
    }

    private static int skipWhitespaceTokens(SqlDocument.Line line, int from, int col) {
        int q = col;
        for (int k = from; k < line.tokens.size() && line.tokens.get(k).type == Token.Type.WS; k++) {
            q += line.tokens.get(k).text.length();
        }
        return q;
    }
}
