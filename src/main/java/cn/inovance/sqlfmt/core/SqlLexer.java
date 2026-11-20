package cn.inovance.sqlfmt.core;

import java.util.ArrayList;
import java.util.List;

/**
 * G2 词法分析器：把源码切成 token，字符串 / 行注释 / 块注释作为整体保留。
 * 跨行的字符串与块注释按行切分并在段间插入 NL，保证「每行独立持有 token」
 * 且所有 token 的 text 顺序拼接后与原文完全一致。
 */
public final class SqlLexer {

    private final String src;
    private final List<Token> out = new ArrayList<>();
    private int i = 0;
    private int line = 1;
    private int col = 0;

    private SqlLexer(String src) {
        this.src = src;
    }

    public static List<Token> tokenize(String src) {
        return new SqlLexer(src == null ? "" : src).run();
    }

    private List<Token> run() {
        final int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\r') {
                // CRLF / CR 统一归为一个换行符。'\r' 绝不进入任何 token，
                // 否则它会混进 LINE_COMMENT / STR / BLOCK_COMMENT 的字面量里，
                // 规则重排后 V2（字符串/注释守恒）必然失败。
                out.add(new Token(Token.Type.NL, "\n", line, col));
                i += (i + 1 < n && src.charAt(i + 1) == '\n') ? 2 : 1;
                line++;
                col = 0;
            } else if (c == '\n') {
                out.add(new Token(Token.Type.NL, "\n", line, col));
                i++;
                line++;
                col = 0;
            } else if (isWs(c)) {
                int j = i;
                while (j < n && isWs(src.charAt(j))) j++;
                out.add(new Token(Token.Type.WS, src.substring(i, j), line, col));
                col += j - i;
                i = j;
            } else if (c == '\'') {
                readQuoted();
            } else if (c == '-' && i + 1 < n && src.charAt(i + 1) == '-') {
                int j = i;
                while (j < n && src.charAt(j) != '\n' && src.charAt(j) != '\r') j++;
                out.add(new Token(Token.Type.LINE_COMMENT, src.substring(i, j), line, col));
                col += j - i;
                i = j;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                readBlockComment();
            } else if (c == '=' && i + 1 < n && src.charAt(i + 1) == '>') {
                // 命名参数保护：'=>' 视为单个标点，规则需跳过它左侧的参数名
                out.add(new Token(Token.Type.PUNCT, "=>", line, col));
                i += 2;
                col += 2;
            } else if (isWordStart(c)) {
                int j = i + 1;
                while (j < n && isWordPart(src.charAt(j))) j++;
                out.add(new Token(Token.Type.WORD, src.substring(i, j), line, col));
                col += j - i;
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && (Character.isDigit(src.charAt(j)) || src.charAt(j) == '.')) j++;
                out.add(new Token(Token.Type.NUMBER, src.substring(i, j), line, col));
                col += j - i;
                i = j;
            } else {
                out.add(new Token(Token.Type.PUNCT, String.valueOf(c), line, col));
                i++;
                col++;
            }
        }
        return out;
    }

    /** 单引号字符串，处理 '' 转义，允许跨行。 */
    private void readQuoted() {
        final int n = src.length();
        int j = i + 1;
        while (j < n) {
            char c = src.charAt(j);
            if (c == '\'') {
                if (j + 1 < n && src.charAt(j + 1) == '\'') {
                    j += 2;
                    continue;
                }
                j++;
                break;
            }
            j++;
        }
        emitMultiline(Token.Type.STR, src.substring(i, j));
        i = j;
    }

    /** /* ... *\/ 块注释，允许跨行。 */
    private void readBlockComment() {
        final int n = src.length();
        int j = i + 2;
        while (j + 1 < n && !(src.charAt(j) == '*' && src.charAt(j + 1) == '/')) j++;
        j = Math.min(n, j + 2);
        emitMultiline(Token.Type.BLOCK_COMMENT, src.substring(i, j));
        i = j;
    }

    private void emitMultiline(Token.Type type, String text) {
        // 按 CRLF / LF / CR 三种换行切分，切出来的每段都不含 '\r'。
        String[] parts = text.split("\\r\\n|\\n|\\r", -1);
        for (int k = 0; k < parts.length; k++) {
            if (k > 0) {
                out.add(new Token(Token.Type.NL, "\n", line, col));
                line++;
                col = 0;
            }
            if (!parts[k].isEmpty()) {
                out.add(new Token(type, parts[k], line, col));
                col += parts[k].length();
            }
        }
    }

    private static boolean isWs(char c) {
        // 注意：'\r' 由主循环按换行处理，不在此列。
        return c == ' ' || c == '\t' || c == '\f';
    }

    private static boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$' || c == '#';
    }

    private static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }
}
