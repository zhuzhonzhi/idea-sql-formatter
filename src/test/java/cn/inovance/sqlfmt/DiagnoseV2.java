package cn.inovance.sqlfmt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.SqlLexer;
import cn.inovance.sqlfmt.core.Token;
import cn.inovance.sqlfmt.core.rules.BlockStructureRule;
import cn.inovance.sqlfmt.core.rules.ClauseLayoutRule;
import cn.inovance.sqlfmt.core.rules.KeywordUppercaseRule;
import cn.inovance.sqlfmt.core.rules.LeadingCommaRule;
import cn.inovance.sqlfmt.core.rules.ListAlignRule;
import cn.inovance.sqlfmt.core.rules.ParenCaseRule;

/** 定位 V2「字符串/注释内容变化」的首个差异点。 */
public final class DiagnoseV2 {

    private static List<String> protectedTokens(String src) {
        List<String> r = new ArrayList<>();
        for (Token t : SqlLexer.tokenize(src)) {
            if (t.isProtected()) r.add(t.type + ":" + esc(t.text));
        }
        return r;
    }

    private static String esc(String s) {
        return s.replace("\r", "<CR>").replace("\n", "<LF>").replace("\t", "<TAB>");
    }

    public static void main(String[] args) throws Exception {
        String src = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        FormatOptions opt = args.length > 1 && "full".equals(args[1])
                ? FormatOptions.full() : FormatOptions.defaults();

        SqlDocument doc = SqlDocument.parse(src);
        List<Rule> rules = List.of(
                new KeywordUppercaseRule(), new LeadingCommaRule(),
                new ClauseLayoutRule(), new BlockStructureRule(),
                new ListAlignRule(), new ParenCaseRule());
        for (Rule r : rules) r.apply(doc, opt);
        String out = doc.render();

        List<String> a = protectedTokens(src);
        List<String> b = protectedTokens(out);
        System.out.println("原文 protected token 数: " + a.size()
                + "   格式化后: " + b.size());
        System.out.println("CRLF 行数: " + (src.split("\r\n", -1).length - 1)
                + "   纯 LF 行数: " + (src.split("\n", -1).length - 1));
        System.out.println("原文中 \\r 总数: " + src.chars().filter(c -> c == '\r').count()
                + "   格式化后: " + out.chars().filter(c -> c == '\r').count());

        int n = Math.min(a.size(), b.size());
        int first = -1;
        for (int i = 0; i < n; i++) {
            if (!a.get(i).equals(b.get(i))) { first = i; break; }
        }
        if (first < 0 && a.size() != b.size()) first = n;
        if (first < 0) {
            System.out.println("protected token 序列完全一致");
        } else {
            System.out.println("\n首个差异 index=" + first);
            for (int i = Math.max(0, first - 2); i <= Math.min(Math.max(a.size(), b.size()) - 1, first + 2); i++) {
                String x = i < a.size() ? a.get(i) : "<无>";
                String y = i < b.size() ? b.get(i) : "<无>";
                System.out.printf("  [%d] 原文: %s%n        结果: %s%n", i,
                        cut(x), cut(y));
            }
        }

        System.out.println("\n=== 逐规则独立应用后的 protected token 数 ===");
        for (Rule r : rules) {
            SqlDocument d = SqlDocument.parse(src);
            r.apply(d, opt);
            String o = d.render();
            System.out.printf("  %-26s protected=%d  CR=%d%n",
                    r.id(), protectedTokens(o).size(),
                    o.chars().filter(c -> c == '\r').count());
            if (r.id().startsWith("R10")) {
                System.out.println("    -- R10 标记删除的行 --");
                for (int k = 0; k < d.size(); k++) {
                    SqlDocument.Line ln = d.get(k);
                    if (ln.removed) {
                        System.out.printf("       L%d: %s%n", k + 1, cut(ln.render()));
                    }
                }
            }
        }
    }

    private static String cut(String s) {
        return s.length() > 180 ? s.substring(0, 180) + "..." : s;
    }
}
