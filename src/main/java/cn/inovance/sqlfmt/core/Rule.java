package cn.inovance.sqlfmt.core;

/** 单条格式化规则。apply 返回本次改动的行数，供报告与通知展示。 */
public interface Rule {

    /** 规则编号，例如 "R1"（对应规则手册）。 */
    String id();

    /** 就地修改 doc，返回改动项数。只允许改行首空白、WORD 大小写、逗号位置、纯空行。 */
    int apply(SqlDocument doc, FormatOptions options);
}
