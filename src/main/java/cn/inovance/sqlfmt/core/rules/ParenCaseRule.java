package cn.inovance.sqlfmt.core.rules;

import java.util.List;
import java.util.Locale;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;

/**
 * R5 / R11：', (' 标量子查询块以 '(' 列为基准；
 * R22 / R23：SQL CASE 的 CASE/END 同列，WHEN/THEN/ELSE +2，值行 +4。
 *
 * G3：SQL CASE 只做列对齐，绝不参与 PL/SQL 块栈；本规则默认关闭。
 */
public final class ParenCaseRule implements Rule {

    @Override
    public String id() {
        return "R5/R11/R22/R23 括号与 CASE";
    }

    @Override
    public int apply(SqlDocument doc, FormatOptions options) {
        if (!options.parenCaseAlign) return 0;
        int changed = alignSubqueries(doc, options);
        changed += alignCase(doc, options);
        return changed;
    }

    /** R5 / R11：', (' 内部主子句 = '(' 列 + 2，AND = +4，闭合 ')' = '(' 列。 */
    private static int alignSubqueries(SqlDocument doc, FormatOptions options) {
        int changed = 0;
        List<SqlDocument.Line> lines = doc.lines();
        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line line = lines.get(i);
            if (line.removed || line.guarded) continue;
            String s = line.render();
            String trimmed = s.trim();
            // 严格限定为 ', (' 起始的标量子查询块；不能只判断「以逗号开头且行内含 '('」，
            // 否则 ', NVL(func(...))' 这类行会把 '(' 列算到很靠后，与其他对齐规则互相放大。
            if (!trimmed.startsWith(", (") && !trimmed.startsWith(",(")) continue;
            int comma = s.indexOf(',');
            int paren = s.indexOf('(', comma);
            if (paren < 0) continue;

            for (int j = i + 1; j < lines.size(); j++) {
                SqlDocument.Line m = lines.get(j);
                if (m.removed || m.guarded) continue;
                String ms = m.render();
                String kw = m.firstWord();
                int target;
                if (ms.trim().startsWith(")")) {
                    target = paren;
                    changed += setIndent(m, target);
                    break;
                } else if ("AND".equals(kw) || "OR".equals(kw)) {
                    target = paren + options.indentSize * 2;
                } else if ("SELECT".equals(kw) || "FROM".equals(kw) || "WHERE".equals(kw)) {
                    target = paren + options.indentSize;
                } else {
                    continue;
                }
                changed += setIndent(m, target);
            }
        }
        return changed;
    }

    /** R22 / R23：以 CASE 列为基准，按模板重排内部行（只改行首空白）。 */
    private static int alignCase(SqlDocument doc, FormatOptions options) {
        int changed = 0;
        List<SqlDocument.Line> lines = doc.lines();
        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line line = lines.get(i);
            if (line.removed || line.guarded) continue;
            String s = line.render();
            int caseCol = s.toUpperCase(Locale.ROOT).indexOf("CASE");
            if (caseCol < 0) continue;
            // 只处理「SELECT CASE」或「, CASE」起始的字段行
            String trimmed = s.trim().toUpperCase(Locale.ROOT);
            if (!trimmed.startsWith("SELECT CASE") && !trimmed.startsWith(", CASE")) continue;

            for (int j = i + 1; j < lines.size(); j++) {
                SqlDocument.Line m = lines.get(j);
                if (m.removed) continue;
                String kw = m.firstWord();
                if ("END".equals(kw)) {
                    changed += setIndent(m, caseCol);
                    break;
                }
                if (m.guarded) continue;
                if ("WHEN".equals(kw) || "THEN".equals(kw) || "ELSE".equals(kw)) {
                    changed += setIndent(m, caseCol + options.indentSize);
                } else {
                    changed += setIndent(m, caseCol + options.indentSize * 2);
                }
            }
        }
        return changed;
    }

    private static int setIndent(SqlDocument.Line line, int target) {
        if (line.indentLen() == target) return 0;
        line.setIndent(target);
        return 1;
    }
}
