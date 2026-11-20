package cn.inovance.sqlfmt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.rules.BlockStructureRule;
import cn.inovance.sqlfmt.core.rules.ClauseLayoutRule;
import cn.inovance.sqlfmt.core.rules.KeywordUppercaseRule;
import cn.inovance.sqlfmt.core.rules.LeadingCommaRule;
import cn.inovance.sqlfmt.core.rules.ListAlignRule;
import cn.inovance.sqlfmt.core.rules.ParenCaseRule;

/** 诊断工具：定位非幂等的规则，打印首次差异行。 */
public final class DiagnoseIdempotency {

    private static final String FILE = "/Users/zzz/Develop/汇川/cux_bi_cst_mar.pck";

    public static void main(String[] args) throws Exception {
        String src = Files.readString(Paths.get(FILE), StandardCharsets.UTF_8);

        String[] names = {"uppercase", "leadingComma", "whereAnd", "blankLines",
                "blockStructure", "followCommentsOnly", "listAlign", "parenCaseAlign",
                "strictBlock", "defaults", "full"};
        for (String name : names) {
            FormatOptions o = off();
            switch (name) {
                case "uppercase": o.uppercaseKeywords = true; break;
                case "leadingComma": o.leadingComma = true; break;
                case "whereAnd": o.whereAnd = true; break;
                case "blankLines": o.blankLines = true; break;
                case "blockStructure": o.blockStructure = true; o.followComments = false; break;
                case "followCommentsOnly": o.followComments = true; break;
                case "listAlign": o.listAlign = true; break;
                case "parenCaseAlign": o.parenCaseAlign = true; break;
                case "strictBlock": o.blockStructure = true; o.strictBlockIndent = true;
                    o.followComments = false; break;
                case "defaults": o = FormatOptions.defaults(); break;
                case "full": o = FormatOptions.full(); break;
                default: break;
            }
            String a = run(src, o);
            String b = run(a, o);
            System.out.printf("%-22s %s%n", name, a.equals(b) ? "IDEMPOTENT" : "NOT idempotent");
            if (!a.equals(b)) printFirstDiff(a, b, name);
        }
    }

    private static void printFirstDiff(String a, String b, String name) {
        String[] x = a.split("\n", -1), y = b.split("\n", -1);
        int shown = 0;
        for (int i = 0; i < Math.min(x.length, y.length) && shown < 3; i++) {
            if (!x[i].equals(y[i])) {
                System.out.println("    L" + (i + 1) + " [1] " + x[i]);
                System.out.println("    L" + (i + 1) + " [2] " + y[i]);
                shown++;
            }
        }
        if (x.length != y.length) System.out.println("    lines " + x.length + " -> " + y.length);
    }

    private static String run(String src, FormatOptions o) {
        SqlDocument doc = SqlDocument.parse(src);
        for (Rule r : rules()) r.apply(doc, o);
        return doc.render();
    }

    private static List<Rule> rules() {
        return new ArrayList<>(Arrays.asList(
                new KeywordUppercaseRule(), new LeadingCommaRule(), new ClauseLayoutRule(),
                new BlockStructureRule(), new ListAlignRule(), new ParenCaseRule()));
    }

    private static FormatOptions off() {
        FormatOptions o = new FormatOptions();
        o.uppercaseKeywords = false;
        o.leadingComma = false;
        o.blockStructure = false;
        o.whereAnd = false;
        o.blankLines = false;
        o.followComments = false;
        o.listAlign = false;
        o.parenCaseAlign = false;
        o.verify = false;
        return o;
    }
}
