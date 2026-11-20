package cn.inovance.sqlfmt.core.rules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.Rule;
import cn.inovance.sqlfmt.core.SqlDocument;
import cn.inovance.sqlfmt.core.Token;

/**
 * R3 / R4 / R7 / R8 / R24：块结构缩进。
 *
 * 关键设计（对应手册 E7）：本规则**只调整块结构起始行**的行首空白，
 * 绝不触碰字段行、表达式续行和以 ',' 开头的元素行 —— 否则会破坏
 * R9-R16 精心对齐的 col23/25/27 等列，正是手册反复警告的「通用规则误伤」。
 *
 * G3：SQL 表达式里的 CASE 不进入块栈，但块栈结束后由 R22/R23 单独按 CASE 列对齐
 * （CASE 为基准，WHEN/THEN/ELSE +2，END 同列），避免把 CASE 的 ELSE/END 误当 IF 的。
 */
public final class BlockStructureRule implements Rule {

    /** 只有这些真正引入嵌套层级的行入栈；定义行(DECLAREs)保留原缩进作锚点。 */
    private static final Set<String> STARTERS = Set.of("BEGIN", "IF", "LOOP", "FOR", "WHILE");
    private static final Set<String> MID = Set.of("ELSIF", "ELSE", "EXCEPTION");
    private static final Set<String> END_KINDS = Set.of("IF", "LOOP", "CASE");

    private static final class Frame {
        final String kind;
        final int indent;

        Frame(String kind, int indent) {
            this.kind = kind;
            this.indent = indent;
        }
    }

    @Override
    public String id() {
        return "R3/R4/R7/R8/R24 块结构";
    }

    @Override
    public int apply(SqlDocument doc, FormatOptions options) {
        if (!options.blockStructure) return 0;
        int changed = 0;
        Deque<Frame> stack = new ArrayDeque<>();
        List<SqlDocument.Line> lines = doc.lines();

        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line line = lines.get(i);
            if (line.removed) continue;

            // 注释行延后到第二阶段，避免「基于尚未处理完的缩进」造成震荡（G6）
            if (line.guarded || line.isPureComment()) continue;
            if (line.startsWithComma()) continue;   // 字段/元素行，R24 不约束

            String kw = line.firstWord();
            if (kw == null) continue;
            List<Token> codes = line.codeTokens();

            if (STARTERS.contains(kw)) {
                // 单行块（IF ... END IF; 等）净变化为 0，不入栈
                if (containsWord(codes, "END")) continue;
                int target = (options.strictBlockIndent && !stack.isEmpty())
                        ? stack.peek().indent + options.indentSize
                        : line.indentLen();       // 保守模式：保留原缩进作为锚点
                if (line.indentLen() != target) {
                    line.setIndent(target);
                    changed++;
                }
                stack.push(new Frame(kindOf(kw), target));
                continue;
            }

            if (kw.equals("END")) {
                Frame frame = popMatching(stack, endKind(codes));
                // END 与配对块首同列（R3 / R24 第 2 条）
                if (frame != null && line.indentLen() != frame.indent) {
                    line.setIndent(frame.indent);
                    changed++;
                }
                continue;
            }

            if (MID.contains(kw) && !stack.isEmpty()) {
                // ELSE / ELSIF / EXCEPTION 与配对块首同列（R4）
                int target = stack.peek().indent;
                if (line.indentLen() != target) {
                    line.setIndent(target);
                    changed++;
                }
            }
        }
        // 第二阶段（R22/R23）：SQL CASE 的 ELSE/END 可能已被上面的 IF 块栈改列，
        // 这里统一按 CASE 自身列重排；必须在块栈之后、注释跟随之前。
        changed += alignSqlCase(lines, options);

        // 第三阶段（R8）：此时块结构与 CASE 缩进均已确定，注释行取相邻代码行 min 缩进，
        // 结果只依赖最终状态，重复运行稳定（不依赖原始缩进）。
        if (options.followComments) {
            for (int i = 0; i < lines.size(); i++) changed += followComment(lines, i);
        }
        return changed;
    }

    /** R22 / R23：CASE 为基准列，WHEN/THEN/ELSE + 一个缩进级，END 与 CASE 同列。 */
    private static int alignSqlCase(List<SqlDocument.Line> lines, FormatOptions options) {
        int changed = 0;
        for (int i = 0; i < lines.size(); i++) {
            SqlDocument.Line start = lines.get(i);
            if (start.removed || start.guarded) continue;
            int caseCol = caseColumn(start);
            if (caseCol < 0) continue;

            Deque<Integer> cols = new ArrayDeque<>();
            cols.push(caseCol);
            int endIndex = i;
            for (int j = i + 1; j < lines.size(); j++) {
                SqlDocument.Line m = lines.get(j);
                endIndex = j;
                if (m.removed || m.guarded || m.isPureComment()) continue;
                List<Token> codes = m.codeTokens();
                if (codes.isEmpty()) continue;

                int nested = caseColumn(m);
                if (nested >= 0) {
                    cols.push(nested);          // 嵌套 CASE：CASE 行自身不动
                    continue;
                }
                String kw = m.firstWord();
                if ("END".equals(kw) && closesCase(codes)) {
                    int target = cols.pop();
                    if (m.indentLen() != target) {
                        m.setIndent(target);
                        changed++;
                    }
                    if (cols.isEmpty()) break;  // 配对 CASE 结束
                    continue;
                }
                if ("WHEN".equals(kw) || "THEN".equals(kw) || "ELSE".equals(kw)) {
                    int target = cols.peek() + options.indentSize;
                    if (m.indentLen() != target) {
                        m.setIndent(target);
                        changed++;
                    }
                }
            }
            i = endIndex;                       // 嵌套 CASE 已在本轮内处理，跳过整段
        }
        return changed;
    }

    /** @return CASE 表达式起始行中 CASE token 的列；非起始行返回 -1。 */
    private static int caseColumn(SqlDocument.Line line) {
        List<Token> codes = line.codeTokens();
        if (codes.isEmpty()) return -1;
        Token first = codes.get(0);
        int idx = -1;
        if (first.isWord("CASE")) idx = 0;
        else if (codes.size() > 1 && codes.get(1).isWord("CASE")
                && (first.isWord("SELECT") || first.isPunct(","))) idx = 1;
        if (idx < 0) return -1;

        Token target = codes.get(idx);
        int col = line.indentLen();
        for (Token t : line.tokens) {
            if (t == target) return col;
            col += t.text.length();
        }
        return -1;
    }

    /** END / END, / END) / END CASE 关闭 CASE；END IF / END LOOP 不关。 */
    private static boolean closesCase(List<Token> codes) {
        if (codes.isEmpty() || !codes.get(0).isWord("END")) return false;
        if (codes.size() == 1) return true;
        Token next = codes.get(1);
        if (next.type != Token.Type.WORD) return true;
        return next.isWord("CASE");
    }

    private static String kindOf(String kw) {
        if (kw.equals("IF")) return "IF";
        if (kw.equals("LOOP") || kw.equals("FOR") || kw.equals("WHILE")) return "LOOP";
        return "BLOCK";
    }

    private static String endKind(List<Token> codes) {
        if (codes.size() > 1 && codes.get(1).type == Token.Type.WORD) {
            String k = codes.get(1).text.toUpperCase(java.util.Locale.ROOT);
            if (END_KINDS.contains(k)) return k;
        }
        return "BLOCK";
    }

    private static Frame popMatching(Deque<Frame> stack, String kind) {
        if (stack.isEmpty()) return null;
        if (kind.equals("BLOCK")) return stack.pop();
        Iterator<Frame> it = stack.iterator();   // head(栈顶) -> tail
        int depth = 0;
        while (it.hasNext()) {
            Frame f = it.next();
            if (f.kind.equals(kind)) {
                for (int k = 0; k <= depth; k++) stack.pop();
                return f;
            }
            depth++;
        }
        return null;
    }

    private static boolean containsWord(List<Token> codes, String upper) {
        for (Token t : codes) if (t.isWord(upper)) return true;
        return false;
    }

    /** R8：纯行注释行跟随相邻代码行，取上下代码行缩进的较小值（确定性、幂等）。 */
    private static int followComment(List<SqlDocument.Line> lines, int i) {
        SqlDocument.Line line = lines.get(i);
        if (!isLineCommentOnly(line)) return 0;   // 块注释/字符串内容不动（E1）

        Integer prev = null;
        Integer next = null;
        for (int k = i - 1; k >= 0; k--) {
            SqlDocument.Line p = lines.get(k);
            if (skip(p)) continue;
            prev = p.indentLen();
            break;
        }
        for (int k = i + 1; k < lines.size(); k++) {
            SqlDocument.Line p = lines.get(k);
            if (skip(p)) continue;
            next = p.indentLen();
            break;
        }
        if (prev == null && next == null) return 0;
        int target = prev == null ? next : (next == null ? prev : Math.min(prev, next));
        if (line.indentLen() != target) {
            line.setIndent(target);
            return 1;
        }
        return 0;
    }

    private static boolean skip(SqlDocument.Line p) {
        return p.removed || p.isBlank() || p.isPureComment();
    }

    private static boolean isLineCommentOnly(SqlDocument.Line line) {
        boolean hasComment = false;
        for (Token t : line.tokens) {
            if (t.type == Token.Type.WS) continue;
            if (t.type == Token.Type.LINE_COMMENT) {
                hasComment = true;
            } else {
                return false;
            }
        }
        return hasComment;
    }
}
