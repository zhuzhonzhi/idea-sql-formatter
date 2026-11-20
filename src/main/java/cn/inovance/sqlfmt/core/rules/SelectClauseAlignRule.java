package cn.inovance.sqlfmt.core.rules;

import java.util.HashMap;
import java.util.HashSet;
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
 *    内联 "(SELECT ...)" 内层深度更深，不按外层列重排。
 *  - AND 不在此规则内，仍由 R17 按「WHERE + 2」对齐。
 *  - INSERT INTO ... SELECT 由 INSERT 行建立本层基准，SELECT/FROM/WHERE 与 INSERT 同列；
 *  - 行首 SELECT 建立本层基准；行内出现 "(SELECT ..." 即以内联 SELECT 的实际列为
 *    内层基准（", (" / "FROM (" / "IN (" / 裸 "(" 等均适用），FROM/WHERE 与它同列；
 *    内联层内的 GROUP/ORDER/HAVING 按条件列（基准 + 2）对齐。
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
        // depth -> 该层最近一个 SELECT 的列（行首 SELECT 或内联括号里的 SELECT）
        Map<Integer, Integer> baseAtDepth = new HashMap<>();
        // 由内联 "(SELECT ...)" 建立基准的深度：GROUP/ORDER 按条件列而非基准列对齐
        Set<Integer> inlineDepth = new HashSet<>();
        // INSERT 行的列：紧随其后的 SELECT 以此为基准，而不是 SELECT 自身原缩进
        Map<Integer, Integer> insertBaseAtDepth = new HashMap<>();

        for (SqlDocument.Line line : doc.lines()) {
            if (line.removed || line.guarded) continue;
            int d = Math.max(0, depth);
            List<Token> codes = line.codeTokens();
            String first = line.firstWord();

            // 新语句开始：清掉本层及更深层的旧基准
            if (first != null && STATEMENT_START.contains(first)) {
                baseAtDepth.entrySet().removeIf(e -> e.getKey() >= d);
                if (!"SELECT".equals(first)) {
                    insertBaseAtDepth.entrySet().removeIf(e -> e.getKey() >= d);
                }
            }
            if ("INSERT".equals(first)) {
                insertBaseAtDepth.put(d, line.indentLen());
            } else if ("SELECT".equals(first)) {
                Integer ib = insertBaseAtDepth.get(d);
                if (ib != null) {
                    if (line.indentLen() != ib) {
                        line.setIndent(ib);
                        changed++;
                    }
                    baseAtDepth.put(d, ib);
                    insertBaseAtDepth.remove(d);
                } else {
                    baseAtDepth.put(d, line.indentLen());
                }
            } else if (first != null && CLAUSE.contains(first)) {
                Integer base = baseAtDepth.get(d);
                if (base != null) {
                    int target = base;
                    if (inlineDepth.contains(d)
                            && ("GROUP".equals(first) || "ORDER".equals(first) || "HAVING".equals(first))) {
                        target = base + options.indentSize;   // 与 WHERE 后第一个条件同列
                    }
                    if (line.indentLen() != target) {
                        line.setIndent(target);
                        changed++;
                    }
                }
            }

            // 语句结束：分号或 PL/SQL 块关键字
            if ((first != null && BLOCK.contains(first)) || containsPunct(codes, ";")) {
                baseAtDepth.entrySet().removeIf(e -> e.getKey() >= d);
                insertBaseAtDepth.entrySet().removeIf(e -> e.getKey() >= d);
            }

            // R19：扫完整行累计括号深度；深度回落时清掉更深层基准
            for (Token t : codes) {
                if (t.isPunct("(")) depth++;
                else if (t.isPunct(")")) depth = Math.max(0, depth - 1);
            }
            final int curDepth = depth;
            baseAtDepth.entrySet().removeIf(e -> e.getKey() > curDepth);
            inlineDepth.removeIf(e -> e > curDepth);
            insertBaseAtDepth.entrySet().removeIf(e -> e.getKey() > curDepth);

            // 内联 ", (SELECT ...)" / "FROM (SELECT ...)" 的 '(' 打开新一层：
            // 内层 FROM/WHERE 以内联 SELECT 的实际列为基准，与「SELECT 同列」一致。
            if (curDepth > d) {
                int sel = inlineSelectCol(line, codes);
                if (sel >= 0) {
                    baseAtDepth.put(curDepth, sel);
                    inlineDepth.add(curDepth);
                }
            }
        }
        return changed;
    }

    /** @return 行内 "(SELECT" 中 SELECT token 的列；非内联子查询返回 -1。 */
    private static int inlineSelectCol(SqlDocument.Line line, List<Token> codes) {
        Token select = null;
        for (int k = 0; k + 1 < codes.size(); k++) {
            if (codes.get(k).isPunct("(") && codes.get(k + 1).isWord("SELECT")) {
                select = codes.get(k + 1);                  // ", (" / "FROM (" / "IN (" / 裸 "("
                break;
            }
        }
        if (select == null) return -1;
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
