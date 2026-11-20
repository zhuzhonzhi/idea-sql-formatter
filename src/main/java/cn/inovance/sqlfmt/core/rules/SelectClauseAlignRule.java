package cn.inovance.sqlfmt.core.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.Token;

/**
 * R25：SELECT ... INTO ... FROM ... WHERE 这类语句，主子句以 SELECT 所在列为基准对齐。
 *
 * 处理范围：行首的 INTO / FROM / WHERE / GROUP / ORDER / HAVING /
 * CONNECT / START / UNION / MINUS / INTERSECT，缩进统一改为该语句 SELECT 的列。
 *
 * 边界（避免误伤）：
 *  - 以括号深度区分层级：只改与基准 SELECT 同深度的主子句；
 *    "FROM (SELECT ...)" 内层子查询的 FROM/WHERE 深度更深，不按外层列重排。
 *  - AND 不在此规则内，仍由 R17 按「WHERE + 2」对齐。
 *  - 行首 SELECT 建立本层基准；行内 ", (SELECT ...)" 标量子查询以其中 SELECT
 *    的实际列为内层基准，使括号内 FROM/WHERE 与 SELECT 同列；
 *    "FROM (SELECT" 仍不建立，避免把列号算到括号中间。
 *  - 只改行首空白（G1），字符串 / 注释 / guarded 行跳过，V1-V4 天然守恒。
 */
public final class SelectClauseAlignRule implements Rule {

    private static final Set<String> STATEMENT_START = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "WITH");

    private static final Set<String> CLAUSE = Set.of(
            "INTO", "FROM", "WHERE", "GROUP", "ORDER", "HAVING",
            "CONNECT", "START", "UNION", "MINUS", "INTERSECT");

    private static final Set<String> BLOCK = Set.of(
            "BEGIN", "END", "IF", "ELSE", "ELSIF", "LOOP", "EXCEPTION",
            "PROCEDURE", "FUNCTION", "CASE", "FOR", "WHILE", "DECLARE", "RETURN");

    @Override
    public String id() {
        return "R25 SELECT 子句基准对齐";
    }

    @Override
    public int apply(SqlDocument doc, FormatOptions options) {
        if (!options.selectClauseAlign) return 0;
        int changed = 0;
        int depth = 0;
        // depth -> 该层最近一个行首 SELECT 的列
        Map<Integer, Integer> baseAtDepth = new HashMap<>();

        for (SqlDocument.Line line : doc.lines()) {
            if (line.removed || line.guarded) continue;
            int d = Math.max(0, depth);
            List<Token> codes = line.codeTokens();
            String first = line.firstWord();

            // 新语句开始：清掉本层及更深层的旧基准
            if (first != null && STATEMENT_START.contains(first)) {
                baseAtDepth.entrySet().removeIf(e -> e.getKey() >= d);
            }
            if ("SELECT".equals(first)) {
                baseAtDepth.put(d, line.indentLen());
            } else if (first != null && CLAUSE.contains(first)) {
                Integer base = baseAtDepth.get(d);
                if (base != null && line.indentLen() != base) {
                    line.setIndent(base);
                    changed++;
                }
            }

            // 语句结束：分号或 PL/SQL 块关键字
            if ((first != null && BLOCK.contains(first)) || containsPunct(codes, ";")) {
                baseAtDepth.entrySet().removeIf(e -> e.getKey() >= d);
            }

            // R19：扫完整行累计括号深度；深度回落时清掉更深层基准
            for (Token t : codes) {
                if (t.isPunct("(")) depth++;
                else if (t.isPunct(")")) depth = Math.max(0, depth - 1);
            }
            final int curDepth = depth;
            baseAtDepth.entrySet().removeIf(e -> e.getKey() > curDepth);

            // 行内 ", (SELECT ...)" 的 '(' 打开新一层：内层主子句以内联 SELECT
            // 的实际列为基准，与外层「SELECT/FROM/WHERE 同列」的约定一致。
            if (curDepth > d) {
                int sel = scalarSubquerySelectCol(line, codes);
                if (sel >= 0) baseAtDepth.put(curDepth, sel);
            }
        }
        return changed;
    }

    /** @return ", (SELECT" 中 SELECT token 的列；非标量子查询返回 -1。 */
    private static int scalarSubquerySelectCol(SqlDocument.Line line, List<Token> codes) {
        if (codes.size() < 3) return -1;
        if (!codes.get(0).isPunct(",") || !codes.get(1).isPunct("(")
                || !codes.get(2).isWord("SELECT")) return -1;
        Token select = codes.get(2);
        int col = line.indentLen();
        for (Token t : line.tokens) {
            if (t == select) return col;
            col += t.text.length();
        }
        return -1;
    }

    private static boolean containsPunct(List<Token> codes, String p) {
        for (Token t : codes) if (t.isPunct(p)) return true;
        return false;
    }
}
