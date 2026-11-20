package cn.inovance.sqlfmt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.SafetyVerifier;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.SqlFormatter;

/** 纯 Java 自测：不依赖 IDEA SDK，可直接 javac 编译运行。 */
public final class FormatterSelfTest {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) throws Exception {
        // 1. 词法往返
        String sample = "select a,\n b from t -- select here\n where x = 'select';\n";
        check("parse/render 往返无损", sample.equals(SqlDocument.parse(sample).render()),
                "render 与原文不一致");

        // 2. 关键字大写 + G2 词法保护
        FormatOptions upperOnly = onlyUppercase();
        String s2 = "select a, interval => 1 from t where x = 'select' -- select comment\n";
        SqlFormatter.Result r2 = SqlFormatter.format(s2, upperOnly);
        check("R1 关键字大写", r2.ok && r2.text.contains("SELECT")
                && r2.text.contains(" FROM ") && r2.text.contains(" WHERE "), r2.text);
        check("G2 字符串内容不改动", r2.text.contains("'select'"), r2.text);
        check("G2 行注释不改动", r2.text.contains("-- select comment"), r2.text);
        check("G2 命名参数不改动", r2.text.contains("interval => 1"), r2.text);

        // 3. 前导逗号（R10）
        FormatOptions commaOnly = onlyLeadingComma();
        String s3 = "    a,\n    b,\n    c\n";
        SqlFormatter.Result r3 = SqlFormatter.format(s3, commaOnly);
        check("R10 前导逗号通过校验", r3.ok, String.valueOf(r3.error));
        check("R10 逗号移到行首", r3.text.contains("  , b") && r3.text.contains("  , c"), r3.text);
        check("R10 行尾逗号消失", !r3.text.contains("a,") && !r3.text.contains("b,"), r3.text);

        // 3.5 行尾逗号后紧跟注释：逗号移到下一非注释行行首，注释留原行
        String s3c = "    a,  -- 中文注释\n"
                + "    -- 独立注释行\n"
                + "    b\n";
        SqlFormatter.Result r3c = SqlFormatter.format(s3c, commaOnly);
        check("R10 注释前行尾逗号移到下一非注释行",
                r3c.ok && r3c.text.contains("    a  -- 中文注释") && r3c.text.contains("\n  , b"),
                String.valueOf(r3c.error) + " :: " + r3c.text);
        check("R10 逗号移动不改动任何注释",
                r3c.text.contains("-- 中文注释") && r3c.text.contains("-- 独立注释行"), r3c.text);

        String s3d = "    a, 'N'\n    b\n";
        SqlFormatter.Result r3d = SqlFormatter.format(s3d, commaOnly);
        check("R10 逗号后仍有字面量时不移动",
                r3d.ok && r3d.text.contains("a, 'N'"), String.valueOf(r3d.error) + " :: " + r3d.text);

        // 4. WHERE 换行 + AND 对齐（R17/R18）
        FormatOptions whereOnly = onlyWhere();
        String s4 = "    SELECT a\n    FROM t WHERE x = 1\n    AND y = 2\n";
        SqlFormatter.Result r4 = SqlFormatter.format(s4, whereOnly);
        check("R18 WHERE 单独换行", r4.ok && r4.text.contains("\n    WHERE x = 1"), r4.text);
        check("R17 AND 缩进 +2", r4.text.contains("\n      AND y = 2"), r4.text);

        // 5. 空行清理（R20/R21）
        FormatOptions blankOnly = onlyBlankLines();
        String s5 = "INSERT INTO t (a,\n\n, b)\n";
        SqlFormatter.Result r5 = SqlFormatter.format(s5, blankOnly);
        check("R20 字段清单内空行删除", r5.ok && !r5.text.contains("a,\n\n"), r5.text);

        // 5.5 R25：SELECT 主子句以 SELECT 列为基准对齐
        FormatOptions r25 = off();
        r25.selectClauseAlign = true;
        r25.whereAnd = true;               // 同时启用 R17，验证 AND 仍是 WHERE + 2
        String s25 = "    SELECT a, b\n"
                + "      INTO x, y\n"
                + "      FROM t\n"
                + "     WHERE a = 1\n"
                + "       AND b = 2;\n";
        SqlFormatter.Result r25res = SqlFormatter.format(s25, r25);
        check("R25 INTO/FROM/WHERE 与 SELECT 同列",
                r25res.ok && r25res.text.contains("\n    INTO")
                        && r25res.text.contains("\n    FROM")
                        && r25res.text.contains("\n    WHERE"),
                String.valueOf(r25res.error) + " :: " + r25res.text);
        check("R25 不破坏 R17（AND = WHERE + 2）",
                r25res.text.contains("\n      AND"), r25res.text);

        String s25n = "    SELECT a\n"
                + "      FROM (SELECT b\n"
                + "              FROM t2\n"
                + "             WHERE b = 1) x\n"
                + "     WHERE a = 2;\n";
        SqlFormatter.Result r25n = SqlFormatter.format(s25n, r25);
        check("R25 嵌套子查询不被外层基准改动",
                r25n.ok && r25n.text.contains("              FROM t2")
                        && r25n.text.contains("             WHERE b = 1"),
                String.valueOf(r25n.error) + " :: " + r25n.text);

        // 5.6 R25：", (SELECT ...)" 标量子查询内部 FROM/WHERE 与 SELECT 同列
        String s25sub = "    SELECT a\n"
                + "        , (SELECT COUNT(1)\n"
                + "              FROM t2 x\n"
                + "            WHERE x.id = a.id\n"
                + "              AND x.ok = 'Y') cnt\n"
                + "      FROM t\n"
                + "     WHERE a.id = 1;\n";
        SqlFormatter.Result r25sub = SqlFormatter.format(s25sub, r25);
        check("R25 括号内 FROM/WHERE 与 SELECT 同列",
                r25sub.ok && r25sub.text.contains("        , (SELECT COUNT(1)\n"
                        + "           FROM t2 x\n"
                        + "           WHERE x.id = a.id\n"
                        + "             AND x.ok = 'Y') cnt"),
                String.valueOf(r25sub.error) + " :: " + r25sub.text);
        check("R25 子查询对齐不影响外层主子句",
                r25sub.text.contains("\n    FROM t\n    WHERE a.id = 1;"), r25sub.text);

        // 5.7 R22/R23：CASE 为基准，WHEN/THEN/ELSE +2，END 同列
        FormatOptions caseOnly = off();
        caseOnly.blockStructure = true;
        String sCase = "    CASE\n"
                + "      WHEN a = 1\n"
                + "        v1\n"
                + "      THEN\n"
                + "        x\n"
                + "  ELSE\n"
                + "        y\n"
                + "  END,\n";
        SqlFormatter.Result rCase = SqlFormatter.format(sCase, caseOnly);
        check("R22/R23 CASE 分支与 END 对齐",
                rCase.ok && rCase.text.equals("    CASE\n      WHEN a = 1\n        v1\n"
                        + "      THEN\n        x\n      ELSE\n        y\n    END,\n"),
                String.valueOf(rCase.error) + " :: " + rCase.text);

        String sCaseNested = "    SELECT CASE\n"
                + "        WHEN a = 1\n"
                + "            CASE\n"
                + "              WHEN b = 2 THEN 2\n"
                + "          ELSE 3\n"
                + "            END\n"
                + "    ELSE 4\n"
                + "    END,\n";
        SqlFormatter.Result rCaseNested = SqlFormatter.format(sCaseNested, caseOnly);
        check("R22/R23 嵌套 CASE 各用自身基准",
                rCaseNested.ok && rCaseNested.text.contains("    SELECT CASE\n"
                        + "             WHEN a = 1\n"
                        + "            CASE\n"
                        + "              WHEN b = 2 THEN 2\n"
                        + "              ELSE 3\n"
                        + "            END\n"
                        + "             ELSE 4\n"
                        + "           END,"),
                String.valueOf(rCaseNested.error) + " :: " + rCaseNested.text);

        // 6. CRLF 与 protected token 回归
        // 事故背景：cux_cst_margin_deduction_prt.pck 是 CRLF 文件，'\r' 曾被吞进
        // 行注释/字符串 token 导致 V2 失败；同时 "'N'," 行被 R10 误判为占位行整行删除。
        String crlf = "select a,\r\n"
                + "       'N',\r\n"
                + "       trunc(sysdate)\r\n"
                + "  from t -- 中文注释\r\n"
                + " where x = 'N';\r\n";
        check("CRLF parse/render 往返无损",
                crlf.equals(SqlDocument.parse(crlf).render()), "render 不一致");
        check("CRLF 输出保持 \\r\\n",
                SqlDocument.parse(crlf).render().contains("\r\n"), "换行风格丢失");
        SqlFormatter.Result rCrlf = SqlFormatter.format(crlf, FormatOptions.defaults());
        check("CRLF 文件 V1-V6 通过", rCrlf.ok, String.valueOf(rCrlf.error));
        check("CRLF 结果无裸 LF",
                rCrlf.text.contains("\r\n")
                        && !rCrlf.text.replace("\r\n", "").contains("\n"), rCrlf.text);

        FormatOptions commaOnly2 = onlyLeadingComma();
        String sLit = "    'N',\n    trunc(sysdate)\n";
        SqlFormatter.Result rLit = SqlFormatter.format(sLit, commaOnly2);
        check("R10 不删除含字面量的行",
                rLit.ok && rLit.text.contains("'N'"), String.valueOf(rLit.error) + " :: " + rLit.text);
        check("R10 字面量行转为前导逗号",
                rLit.text.contains(", trunc"), rLit.text);

        Path real2 = Paths.get("/Users/zzz/Develop/汇川/cux_cst_margin_deduction_prt.pck");
        if (Files.exists(real2)) {
            String src2 = Files.readString(real2, StandardCharsets.UTF_8);
            check("CRLF 真实文件 parse/render 往返",
                    src2.equals(SqlDocument.parse(src2).render()), "render 不一致");
            FormatOptions[] presets2 = {FormatOptions.defaults(), FormatOptions.full()};
            String[] names2 = {"defaults", "full"};
            for (int i = 0; i < presets2.length; i++) {
                SqlFormatter.Result rr = SqlFormatter.format(src2, presets2[i]);
                check("CRLF 真实文件 V1-V6 " + names2[i], rr.ok, String.valueOf(rr.error));
            }
        } else {
            System.out.println("SKIP CRLF 真实文件测试（文件不存在）");
        }

        // 7. 幂等性（V5）三套选项
        String complex = "select a,\n b,\n c\nfrom t where x = 1\nand y = 2;\n"
                + "begin\nif x = 1 then\nnull;\nelse\nnull;\nend if;\nend;\n";
        check("V5 幂等 defaults", idempotent(complex, FormatOptions.defaults()), "defaults");
        check("V5 幂等 conservative", idempotent(complex, FormatOptions.conservative()), "conservative");
        check("V5 幂等 full", idempotent(complex, FormatOptions.full()), "full");

        // 8. 真实 Oracle 包（只校验，不写回）
        Path real = Paths.get("/Users/zzz/Develop/汇川/cux_bi_cst_mar.pck");
        if (Files.exists(real)) {
            String src = Files.readString(real, StandardCharsets.UTF_8);
            check("真实文件 parse/render 往返",
                    src.equals(SqlDocument.parse(src).render()), "render 不一致");
            FormatOptions[] presets = {FormatOptions.conservative(), FormatOptions.defaults(),
                    FormatOptions.full()};
            String[] names = {"conservative", "defaults", "full"};
            for (int i = 0; i < presets.length; i++) {
                SqlFormatter.Result r = SqlFormatter.format(src, presets[i]);
                check("真实文件 V1-V4 " + names[i],
                        SafetyVerifier.verify(src, r.text) == null, String.valueOf(r.error));
                check("真实文件校验闸门 " + names[i], r.ok, String.valueOf(r.error));
            }
        } else {
            System.out.println("SKIP 真实文件测试（文件不存在）");
        }

        System.out.println("\n==== 通过 " + pass + " / 失败 " + fail + " ====");
        if (fail > 0) System.exit(1);
    }

    private static boolean idempotent(String src, FormatOptions o) {
        SqlFormatter.Result a = SqlFormatter.format(src, o);
        if (!a.ok) return false;
        SqlFormatter.Result b = SqlFormatter.format(a.text, o);
        return b.ok && a.text.equals(b.text);
    }

    private static FormatOptions off() {
        FormatOptions o = new FormatOptions();
        o.uppercaseKeywords = false;
        o.leadingComma = false;
        o.blockStructure = false;
        o.whereAnd = false;
        o.blankLines = false;
        o.selectClauseAlign = false;
        o.followComments = false;
        o.listAlign = false;
        o.parenCaseAlign = false;
        return o;
    }

    private static FormatOptions onlyUppercase() {
        FormatOptions o = off();
        o.uppercaseKeywords = true;
        return o;
    }

    private static FormatOptions onlyLeadingComma() {
        FormatOptions o = off();
        o.leadingComma = true;
        return o;
    }

    private static FormatOptions onlyWhere() {
        FormatOptions o = off();
        o.whereAnd = true;
        return o;
    }

    private static FormatOptions onlyBlankLines() {
        FormatOptions o = off();
        o.blankLines = true;
        return o;
    }

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            pass++;
            System.out.println("PASS  " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name + "  ::  " + detail);
        }
    }
}
