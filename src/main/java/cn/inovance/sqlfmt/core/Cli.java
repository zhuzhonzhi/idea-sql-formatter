package cn.inovance.sqlfmt.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 纯 Java 命令行入口：不装 IDEA 也能格式化 / 校验 SQL 文件。
 * 便于把同一套规则接入 CI、pre-commit 或批量脚本。
 *
 * 用法：
 *   java -cp out cn.inovance.sqlfmt.core.Cli FILE [--check] [--full] [--strict] [-o OUT]
 */
public final class Cli {

    private Cli() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("用法: Cli FILE [--check] [--full] [--strict] [-o OUT]");
            return;
        }
        Path file = Paths.get(args[0]);
        if (!Files.isRegularFile(file)) {
            System.err.println("文件不存在: " + file);
            System.exit(2);
            return;
        }
        boolean check = has(args, "--check");
        boolean full = has(args, "--full");
        boolean strict = has(args, "--strict");
        String outPath = valueOf(args, "-o");

        FormatOptions opt = full ? FormatOptions.full() : FormatOptions.defaults();
        if (strict) opt.strictBlockIndent = true;
        String src = Files.readString(file, StandardCharsets.UTF_8);

        if (check) {
            System.out.println(SqlFormatter.check(src, opt));
            return;
        }

        SqlFormatter.Result result = SqlFormatter.format(src, opt);
        if (!result.ok) {
            System.err.println("已拒绝写回: " + result.error);
            System.exit(1);
            return;
        }
        if (outPath == null) {
            // V6：先备份，再写临时文件后原子替换
            Path bak = Paths.get(file + ".bak");
            if (!Files.exists(bak)) Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
            Path tmp = Paths.get(file + ".tmp");
            Files.writeString(tmp, result.text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Files.writeString(Paths.get(outPath), result.text, StandardCharsets.UTF_8);
        }
        System.out.println("格式化完成，改动 " + result.changedLines + " 行");
        System.out.print(SqlFormatter.report(result.changes));
    }

    private static boolean has(String[] args, String flag) {
        for (String a : args) if (flag.equals(a)) return true;
        return false;
    }

    private static String valueOf(String[] args, String flag) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (flag.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
