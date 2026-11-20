package cn.inovance.sqlfmt.core;

/**
 * 规则开关。对应规则手册的规则编号，可在 IDEA 设置页逐条开关。
 *
 * 分层的理由（见手册第 12 章「回归护栏」）：
 * 泛化的列对齐规则会误伤刻意对齐的 SQL，因此「对齐类」默认关闭，
 * 只对任意 SQL 文件都安全的「结构类」默认开启。
 */
public final class FormatOptions {

    // ---- 结构类：默认开启，语义安全 ----
    public boolean uppercaseKeywords = true;   // R1 / R2
    public boolean leadingComma = true;        // R10
    public boolean blockStructure = true;      // R3 / R4 / R7 / R8 / R24
    public boolean whereAnd = true;            // R17 / R18 / R19
    public boolean blankLines = true;          // R20 / R21
    public boolean selectClauseAlign = true;   // R25 SELECT 主子句以 SELECT 列为基准

    // ---- R24 严格模式：完整按「父块 + 2」重算块首行缩进 ----
    // 关闭时只修复配对不一致（END / ELSE / EXCEPTION），更保守。
    public boolean strictBlockIndent = false;

    // ---- 对齐类：默认关闭，建议先预览差异再启用 ----
    public boolean listAlign = false;          // R9 / R12 / R13 / R14 / R15 / R16
    public boolean parenCaseAlign = false;     // R5 / R11 / R22 / R23

    // ---- 通用 ----
    public int indentSize = 2;                 // 每一级缩进 2 空格（R24）
    public boolean verify = true;              // V1-V6 安全闸门
    public boolean followComments = true;      // R8 纯注释行跟随相邻代码行

    public static FormatOptions defaults() {
        return new FormatOptions();
    }

    /** 最保守：只做大小写 + 前导逗号 + 校验，不碰任何缩进。 */
    public static FormatOptions conservative() {
        FormatOptions o = new FormatOptions();
        o.blockStructure = false;
        o.whereAnd = false;
        o.blankLines = false;
        o.followComments = false;
        o.selectClauseAlign = false;
        return o;
    }

    /** 全开：按手册 R1-R25 尽量完整格式化（对已有成品文件请先预览）。 */
    public static FormatOptions full() {
        FormatOptions o = new FormatOptions();
        o.strictBlockIndent = true;
        o.listAlign = true;
        o.parenCaseAlign = true;
        return o;
    }

    public FormatOptions copy() {
        FormatOptions o = new FormatOptions();
        o.uppercaseKeywords = uppercaseKeywords;
        o.leadingComma = leadingComma;
        o.blockStructure = blockStructure;
        o.whereAnd = whereAnd;
        o.blankLines = blankLines;
        o.selectClauseAlign = selectClauseAlign;
        o.strictBlockIndent = strictBlockIndent;
        o.listAlign = listAlign;
        o.parenCaseAlign = parenCaseAlign;
        o.indentSize = indentSize;
        o.verify = verify;
        o.followComments = followComments;
        return o;
    }
}
