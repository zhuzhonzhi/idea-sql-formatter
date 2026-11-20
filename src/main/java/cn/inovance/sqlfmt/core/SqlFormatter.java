package cn.inovance.sqlfmt.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.inovance.sqlfmt.core.rules.BlockStructureRule;
import cn.inovance.sqlfmt.core.rules.ClauseLayoutRule;
import cn.inovance.sqlfmt.core.rules.KeywordUppercaseRule;
import cn.inovance.sqlfmt.core.rules.LeadingCommaRule;
import cn.inovance.sqlfmt.core.rules.ListAlignRule;
import cn.inovance.sqlfmt.core.rules.ParenCaseRule;
import cn.inovance.sqlfmt.core.rules.SelectClauseAlignRule;

/**
 * 格式化编排器（零外部依赖）。
 *
 * 流程：parse -> 按序应用规则 -> render -> V1-V4 一致性校验 -> V5 幂等复验。
 * 任何一项不通过都返回 ok=false 并保留原文，由调用方决定是否写回（V6）。
 */
public final class SqlFormatter {

    public static final class Result {
        public final String text;                  // 校验失败时为原文
        public final boolean ok;
        public final String error;                 // null 表示通过
        public final Map<String, Integer> changes;
        public final int changedLines;

        Result(String text, boolean ok, String error,
               Map<String, Integer> changes, int changedLines) {
            this.text = text;
            this.ok = ok;
            this.error = error;
            this.changes = changes;
            this.changedLines = changedLines;
        }
    }

    private SqlFormatter() {}

    public static Result format(String src, FormatOptions options) {
        FormatOptions opt = options == null ? FormatOptions.defaults() : options;
        String before = src == null ? "" : src;

        Map<String, Integer> changes = new LinkedHashMap<>();
        SqlDocument doc = SqlDocument.parse(before);
        for (Rule r : rules(opt)) changes.put(r.id(), r.apply(doc, opt));
        String out = doc.render();

        if (opt.verify) {
            String err = SafetyVerifier.verify(before, out);          // V1-V4
            if (err != null) {
                return new Result(before, false, err, changes, 0);
            }
            // V5：对齐类规则之间存在列号互相反馈，单次运行未必稳定。
            // 迭代到不动点（fixed point）后再交付，保证「再次格式化结果不变」。
            String cur = out;
            boolean converged = false;
            for (int i = 0; i < MAX_PASSES; i++) {
                String next = formatRaw(cur, opt);
                if (next.equals(cur)) {
                    converged = true;
                    break;
                }
                cur = next;
            }
            if (!converged) {
                return new Result(before, false,
                        "V5 失败：" + MAX_PASSES + " 轮后仍未收敛，已拒绝写回（建议关闭对齐类规则）",
                        changes, 0);
            }
            out = cur;
            err = SafetyVerifier.verify(before, out);                 // 对最终结果再校验一次
            if (err != null) return new Result(before, false, err, changes, 0);
        }
        return new Result(out, true, null, changes, diffCount(before, out));
    }

    private static final int MAX_PASSES = 8;

    /** 只检查不写回：返回可直接展示给用户的文本报告。 */
    public static String check(String src, FormatOptions options) {
        Result r = format(src, options);
        if (!r.ok) return "校验未通过：" + r.error;
        return "校验通过（V1-V6），预计改动 " + r.changedLines + " 行\n" + report(r.changes);
    }

    public static String report(Map<String, Integer> changes) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : changes.entrySet()) {
            sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append(" 项\n");
        }
        return sb.toString();
    }

    private static String formatRaw(String src, FormatOptions opt) {
        SqlDocument doc = SqlDocument.parse(src);
        for (Rule r : rules(opt)) r.apply(doc, opt);
        return doc.render();
    }

    private static List<Rule> rules(FormatOptions opt) {
        List<Rule> list = new ArrayList<>();
        list.add(new KeywordUppercaseRule());   // R1/R2
        list.add(new LeadingCommaRule());       // R10
        list.add(new ClauseLayoutRule());       // R17/R18/R19/R20/R21
        list.add(new BlockStructureRule());     // R3/R4/R7/R8/R24
        list.add(new ListAlignRule());          // R9/R12-R16（默认关）
        list.add(new ParenCaseRule());          // R5/R11/R22/R23（默认关）
        list.add(new SelectClauseAlignRule());  // R25 SELECT 主子句基准对齐（默认开）
        return list;
    }

    private static int diffCount(String a, String b) {
        String[] x = a.split("\n", -1);
        String[] y = b.split("\n", -1);
        int n = Math.min(x.length, y.length);
        int c = 0;
        for (int i = 0; i < n; i++) if (!x[i].equals(y[i])) c++;
        return c + Math.abs(x.length - y.length);
    }
}
