package cn.inovance.sqlfmt.core.rules;

import java.util.List;
import java.util.Locale;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Keywords;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.Token;

/** R1 / R2：白名单关键字大写；用户标识符、命名参数 x =>、字符串与注释一律不动。 */
public final class KeywordUppercaseRule implements Rule {

    @Override
    public String id() {
        return "R1/R2 关键字大写";
    }

    @Override
    public int apply(SqlDocument doc, FormatOptions options) {
        if (!options.uppercaseKeywords) return 0;
        int changed = 0;
        for (SqlDocument.Line line : doc.lines()) {
            if (line.removed || line.guarded) continue;
            List<Token> codes = line.codeTokens();
            for (int k = 0; k < codes.size(); k++) {
                Token t = codes.get(k);
                if (t.type != Token.Type.WORD) continue;
                // G2：命名参数左侧的参数名不大写（例如 interval => ...）
                if (k + 1 < codes.size() && codes.get(k + 1).isPunct("=>")) continue;
                if (!Keywords.isKeyword(t.text)) continue;
                String upper = t.text.toUpperCase(Locale.ROOT);
                if (!t.text.equals(upper)) {
                    t.text = upper;
                    changed++;
                }
            }
        }
        return changed;
    }
}
