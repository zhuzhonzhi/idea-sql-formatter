package cn.inovance.sqlfmt.core;

/**
 * 词法单元。text 可变，规则可就地改写；line/col 记录原始位置，仅用于诊断。
 * 对应规则手册 G2：字符串 / 注释 / 命名参数一律不参与变换。
 */
public final class Token {

    public enum Type {
        WS, NL, STR, LINE_COMMENT, BLOCK_COMMENT, WORD, NUMBER, PUNCT
    }

    public final Type type;
    public String text;
    public final int line;   // 1-based
    public final int col;    // 0-based

    public Token(Type type, String text, int line, int col) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.col = col;
    }

    /** 参与代码语义的 token（WORD / NUMBER / 标点）。 */
    public boolean isCode() {
        return type == Type.WORD || type == Type.NUMBER || type == Type.PUNCT;
    }

    /** G2 保护对象：字符串与注释内容绝不改动。 */
    public boolean isProtected() {
        return type == Type.STR || type == Type.LINE_COMMENT || type == Type.BLOCK_COMMENT;
    }

    public boolean isWord(String upper) {
        return type == Type.WORD && text.equalsIgnoreCase(upper);
    }

    public boolean isPunct(String s) {
        return type == Type.PUNCT && text.equals(s);
    }

    public Token copy() {
        return new Token(type, text, line, col);
    }

    @Override
    public String toString() {
        return type + "[" + text.replace("\n", "\\n") + "]";
    }
}
