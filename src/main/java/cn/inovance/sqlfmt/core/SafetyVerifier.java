package cn.inovance.sqlfmt.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

/**
 * V1-V4 安全闸门。任何格式化结果在写回前必须通过，
 * 只要有一项不守恒就拒绝写回（对应规则手册 G5 / 第 9 章）。
 *
 * V1 token 签名忽略逗号（逗号位置变化是语义等价变换），且 WORD 统一大写
 * （R1 关键字大写不改变序列）；V3 单独校验逗号总数守恒。
 */
public final class SafetyVerifier {

    private static final Set<String> STRUCT_WORDS = new HashSet<>(java.util.Arrays.asList(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "MERGE",
            "BEGIN", "END", "IF", "ELSE", "ELSIF", "LOOP", "CASE", "WHEN", "THEN",
            "EXCEPTION", "PROCEDURE", "FUNCTION", "INTO", "VALUES", "SET",
            "AND", "OR", "UNION", "PACKAGE", "DECLARE", "FOR", "WHILE"));

    public static final class Snapshot {
        public final String tokenSig;
        public final String literalSig;
        public final String structSig;
        public final int commaCount;

        Snapshot(String tokenSig, String literalSig, String structSig, int commaCount) {
            this.tokenSig = tokenSig;
            this.literalSig = literalSig;
            this.structSig = structSig;
            this.commaCount = commaCount;
        }
    }

    private SafetyVerifier() {}

    public static Snapshot snapshot(String src) {
        List<Token> toks = SqlLexer.tokenize(src);
        StringBuilder tokens = new StringBuilder();
        StringBuilder literals = new StringBuilder();
        Map<String, Integer> struct = new TreeMap<>();
        int commas = 0;
        for (Token t : toks) {
            if (t.isProtected()) literals.append(t.text).append('\u0000');
            if (!t.isCode()) continue;
            if (t.isPunct(",")) {
                commas++;
                continue;
            }
            if (t.type == Token.Type.WORD) {
                String upper = t.text.toUpperCase(Locale.ROOT);
                tokens.append(upper).append('\u0000');
                if (STRUCT_WORDS.contains(upper)) struct.merge(upper, 1, Integer::sum);
            } else {
                tokens.append(t.text).append('\u0000');
            }
        }
        return new Snapshot(tokens.toString(), literals.toString(), struct.toString(), commas);
    }

    /** @return null 表示通过；否则返回可直接展示给用户的失败原因。 */
    public static String verify(String before, String after) {
        Snapshot a = snapshot(before);
        Snapshot b = snapshot(after);
        if (!a.tokenSig.equals(b.tokenSig)) {
            return "V1 失败：非逗号 token 序列发生变化（可能改动了标识符或表达式）";
        }
        if (!a.literalSig.equals(b.literalSig)) {
            return "V2 失败：字符串/注释内容发生变化";
        }
        if (a.commaCount != b.commaCount) {
            return "V3 失败：逗号总数不守恒（" + a.commaCount + " -> " + b.commaCount + "）";
        }
        if (!a.structSig.equals(b.structSig)) {
            return "V4 失败：SQL/PL-SQL 结构计数发生变化";
        }
        return null;
    }
}
