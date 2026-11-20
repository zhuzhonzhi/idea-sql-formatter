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
 * R10：统一前导逗号。
 *  - 行尾逗号移到下一元素行的行首；
 *  - "a, -- 注释" 这类逗号后紧跟行尾注释的，逗号移到下一非注释行行首，
 *    注释留在原行（G2：注释内容不变）；
 *  - 独占一行的逗号合并到下一行并删除该空占位行；
 *  - 逗号列 = 首元素列 - 2（下一行缩进减 2，使元素列保持不变）；
 *  - 后接 SQL 子句 / 右括号 / 分号的逗号保留原样（手册 R10 例外）；
 *  - 块注释、字符串、注释内的逗号一律不碰（G2）。
 */
public final class LeadingCommaRule implements Rule {

    private static final Set<String> CLAUSE = Set.of(
            "FROM", "WHERE", "GROUP", "ORDER", "HAVING", "UNION", "MINUS", "INTERSECT",
            "SELECT", "INSERT", "VALUES", "SET", "INTO", "CONNECT", "START", "ON",
            "USING", "JOIN", "LEFT", "RIGHT", "INNER", "FULL", "CROSS", "NATURAL",
            "RETURNING", "END", "THEN", "ELSE", "ELSIF", "LOOP", "WHEN", "CASE");

    @Override
    public String id() {
        return "R10 前导逗号";
    }

    @Override
    public int apply(SqlDocument doc, FormatOptions options) {
        if (!options.leadingComma) return 0;
        int changed = 0;
        List<SqlDocument.Line> lines = doc.lines();
        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line cur = lines.get(i);
            if (cur.removed || cur.guarded) continue;
            // 用「整行非空白 token」判断，不能用 codeTokens()：
            // STR / 注释是 protected，不计入 code，会把 "'N'," 这类真实字段行
            // 误判成「逗号独占的占位行」而整行删掉（曾导致 V2 字面量丢失）。
            // 以「最后一个代码 token 是逗号，其后只允许注释」为准：
            // "a, -- 注释" 的逗号也算行尾逗号（注释留在原行不动）；
            // "a, 'N'" 这类逗号后还有真实内容的行跳过。
            List<Token> meaningful = new ArrayList<>();
            for (Token t : cur.tokens) {
                if (t.type != Token.Type.WS) meaningful.add(t);
            }
            if (meaningful.isEmpty()) continue;

            int commaIdx = -1;
            for (int k = meaningful.size() - 1; k >= 0; k--) {
                Token t = meaningful.get(k);
                if (t.isCode()) {
                    if (t.isPunct(",")) commaIdx = k;
                    break;
                }
            }
            if (commaIdx < 0) continue;
            boolean onlyCommentsAfter = true;
            for (int k = commaIdx + 1; k < meaningful.size(); k++) {
                Token.Type type = meaningful.get(k).type;
                if (type != Token.Type.LINE_COMMENT && type != Token.Type.BLOCK_COMMENT) {
                    onlyCommentsAfter = false;
                    break;
                }
            }
            if (!onlyCommentsAfter) continue;
            Token comma = meaningful.get(commaIdx);

            int j = i + 1;
            while (j < lines.size()
                    && (lines.get(j).removed || lines.get(j).guarded || lines.get(j).isBlank())) j++;
            if (j >= lines.size()) continue;
            SqlDocument.Line next = lines.get(j);

            Token nf = next.firstCode();
            if (nf == null || next.startsWithComma()) continue;
            if (nf.isPunct(")") || nf.isPunct(";")) continue;
            if (nf.type == Token.Type.WORD && CLAUSE.contains(nf.text.toUpperCase(Locale.ROOT))) continue;

            // 去掉当前行的尾逗号（注释 token 保留在原行）
            cur.tokens.remove(comma);
            trimTrailingWhitespace(cur);

            // 下一行加前导逗号：缩进 -2，元素保持原列
            next.setIndent(Math.max(0, next.indentLen() - 2));
            next.tokens.add(0, new Token(Token.Type.WS, " ", 0, 0));
            next.tokens.add(0, new Token(Token.Type.PUNCT, ",", 0, 0));
            changed++;

            // 原本「逗号独占一行」→ 删除该占位行（R10 / E3 恢复后统一）
            if (meaningful.size() == 1) {
                cur.removed = true;
                changed++;
            }
        }
        return changed;
    }

    private static void trimTrailingWhitespace(SqlDocument.Line line) {
        while (!line.tokens.isEmpty()
                && line.tokens.get(line.tokens.size() - 1).type == Token.Type.WS) {
            line.tokens.remove(line.tokens.size() - 1);
        }
    }
}
