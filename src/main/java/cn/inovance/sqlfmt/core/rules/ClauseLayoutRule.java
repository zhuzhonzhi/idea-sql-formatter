package cn.inovance.sqlfmt.core.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.Token;

/**
 * R17 / R18 / R19：WHERE 单独换行、AND 按 WHERE 首条件对齐、括号深度扫完整行。
 * R20 / R21：删除字段清单内部的纯空行，保留语句之间的分隔空行。
 */
public final class ClauseLayoutRule implements Rule {

    private static final Set<String> NEW_STATEMENT = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "UNION", "MINUS",
            "INTERSECT", "FROM");

    @Override
    public String id() {
        return "R17/R18/R19/R20 WHERE-AND 与空行";
    }

    @Override
    public int apply(SqlDocument doc, FormatOptions options) {
        int changed = 0;
        if (options.whereAnd) {
            changed += splitInlineWhere(doc);
            changed += alignAnd(doc, options);
        }
        if (options.blankLines) changed += removeInnerBlankLines(doc);
        return changed;
    }

    /** R18：把 "FROM t WHERE c" 拆成 FROM 行 + WHERE 行，两者同列。 */
    private static int splitInlineWhere(SqlDocument doc) {
        int changed = 0;
        List<SqlDocument.Line> lines = doc.lines();
        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line line = lines.get(i);
            if (line.removed || line.guarded) continue;
            List<Token> codes = line.codeTokens();
            if (codes.isEmpty() || !codes.get(0).isWord("FROM")) continue;

            Token where = null;
            for (int k = 1; k < codes.size(); k++) {
                if (codes.get(k).isWord("WHERE")) { where = codes.get(k); break; }
            }
            if (where == null) continue;

            int idx = line.tokens.indexOf(where);
            if (idx <= 0) continue;
            List<Token> tail = new ArrayList<>(line.tokens.subList(idx, line.tokens.size()));
            line.tokens.subList(idx, line.tokens.size()).clear();
            trimTrailingWhitespace(line);

            SqlDocument.Line whereLine = new SqlDocument.Line();
            whereLine.indent = line.indent;
            whereLine.tokens.addAll(tail);
            lines.add(i + 1, whereLine);
            i++;                       // 跳过刚插入的行
            changed++;
        }
        return changed;
    }

    /**
     * R17 / R19：AND 缩进 = WHERE 缩进 + 2，且 AND 后仅保留 1 个空格，
     * 使 AND 后条件列与 WHERE 后首条件列一致（WHERE 关键字 5 字符 + 空格）。
     * 括号深度通过扫描完整行的 '(' ')' 累计，遇关键字不提前中断。
     */
    private static int alignAnd(SqlDocument doc, FormatOptions options) {
        int changed = 0;
        int depth = 0;
        Map<Integer, Integer> whereAtDepth = new HashMap<>();
        for (SqlDocument.Line line : doc.lines()) {
            if (line.removed) continue;
            int lineDepth = Math.max(0, depth);
            List<Token> codes = line.codeTokens();
            String first = line.firstWord();

            if (first != null && NEW_STATEMENT.contains(first)) {
                whereAtDepth.entrySet().removeIf(e -> e.getKey() >= lineDepth);
            }
            if (containsWord(codes, "WHERE")) {
                whereAtDepth.put(lineDepth, line.indentLen());
            }
            if ("AND".equals(first)) {
                Integer w = whereAtDepth.get(lineDepth);
                if (w != null) {
                    int target = w + options.indentSize;
                    if (line.indentLen() != target) {
                        line.setIndent(target);
                        changed++;
                    }
                    if (normalizeSpaceAfterFirstWord(line)) changed++;
                }
            }
            // R19：扫完整行更新括号深度
            for (Token t : codes) {
                if (t.isPunct("(")) depth++;
                else if (t.isPunct(")")) depth = Math.max(0, depth - 1);
            }
        }
        return changed;
    }

    /** R20：仅删除「前后至少一侧是列表元素」的纯空行，语句间空行保留（R21）。 */
    private static int removeInnerBlankLines(SqlDocument doc) {
        int changed = 0;
        List<SqlDocument.Line> lines = doc.lines();
        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line line = lines.get(i);
            if (!line.isBlank()) continue;
            SqlDocument.Line prev = previousNonBlank(lines, i);
            SqlDocument.Line next = nextNonBlank(lines, i);
            if (prev == null || next == null) continue;
            if (next.startsWithComma() || endsWithComma(prev)) {
                line.removed = true;
                changed++;
            }
        }
        return changed;
    }

    private static boolean endsWithComma(SqlDocument.Line line) {
        List<Token> codes = line.codeTokens();
        return !codes.isEmpty() && codes.get(codes.size() - 1).isPunct(",");
    }

    private static SqlDocument.Line previousNonBlank(List<SqlDocument.Line> lines, int i) {
        for (int k = i - 1; k >= 0; k--) {
            SqlDocument.Line p = lines.get(k);
            if (p.removed || p.isBlank()) continue;
            return p;
        }
        return null;
    }

    private static SqlDocument.Line nextNonBlank(List<SqlDocument.Line> lines, int i) {
        for (int k = i + 1; k < lines.size(); k++) {
            SqlDocument.Line p = lines.get(k);
            if (p.removed || p.isBlank()) continue;
            return p;
        }
        return null;
    }

    /** 把行首关键字之后的空白规范为恰好 1 个空格，保证 AND 后条件列固定。 */
    private static boolean normalizeSpaceAfterFirstWord(SqlDocument.Line line) {
        Token first = line.firstCode();
        if (first == null) return false;
        int idx = line.tokens.indexOf(first);
        int k = idx + 1;
        int removed = 0;
        while (k < line.tokens.size() && line.tokens.get(k).type == Token.Type.WS) {
            line.tokens.remove(k);
            removed++;
        }
        line.tokens.add(k, new Token(Token.Type.WS, " ", 0, 0));
        return removed != 1 || !" ".equals(line.tokens.get(k).text);
    }

    private static boolean containsWord(List<Token> codes, String upper) {
        for (Token t : codes) if (t.isWord(upper)) return true;
        return false;
    }

    private static void trimTrailingWhitespace(SqlDocument.Line line) {
        while (!line.tokens.isEmpty()
                && line.tokens.get(line.tokens.size() - 1).type == Token.Type.WS) {
            line.tokens.remove(line.tokens.size() - 1);
        }
    }
}
