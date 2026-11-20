# Oracle PL/SQL Formatter（IntelliJ IDEA 插件）

依据 `PLSQL_格式化规则.md`（R1–R25、G1–G6、E1–E8、V1–V6）
实现的 IDEA 格式化插件，面向 Oracle SQL / PL-SQL 文件（`.sql / .pck / .pks / .pls / .trg ...`）。

**验证状态**：已用本机 IntelliJ IDEA Ultimate 2026.1（build 261）自带的 379 个 platform jar
完整编译全部源码；纯 Java 自测 **33/33 通过**，包含对两个真实 Oracle 包
（`cux_bi_cst_mar.pck`、CRLF 的 `cux_cst_margin_deduction_prt.pck`）的 V1–V6 只读校验。

> **v1.0.2 新增 R25**：`SELECT ... INTO ... FROM ... WHERE` 主子句以 `SELECT` 列为基准
> 对齐（默认开启，设置页可关），详见第 3 节规则映射表与第 7 节边界说明。
>
> **v1.0.1 修复**：CRLF（Windows 换行）文件此前会报 `V2 失败：字符串/注释内容发生变化`，
> 并伴随 `'N',` 字段行被误删，详见第 5 节。旧版请用
> `Oracle-PLSQL-Formatter-1.0.2.jar` 覆盖安装并**重启 IDEA**。

---

## 1. 安装（两种方式）

### 方式 A：直接安装已构建好的插件（最快）

已生成可直接安装的插件包：

```
idea-sql-formatter/build/Oracle-PLSQL-Formatter-1.0.2.jar
```

在 IDEA 中：**Settings → Plugins → ⚙ → Install Plugin from Disk...** 选择该 jar，重启 IDE。
兼容范围 `since-build=231 / until-build=263.*`（IDEA 2023.1 ~ 2026.x，已覆盖本机 2026.1）。

### 方式 B：从源码构建

```bash
cd idea-sql-formatter
./gradlew buildPlugin        # 产物在 build/distributions/*.zip
```

项目已提供 `gradle/wrapper/gradle-wrapper.properties`（Gradle 8.5），但仓库里没有
`gradle-wrapper.jar`（本次环境无网络下载）。任选一种补齐：

- 用 IDEA 打开项目根目录（会自动识别 `build.gradle.kts` 并拉取 Gradle）；
- 或本机装 Gradle 后执行 `gradle wrapper` 自动生成 `gradlew` 与 jar。

构建依赖 `org.jetbrains.intellij` 1.17.4 + IntelliJ IDEA Community 2023.3，
首次构建会下载 IDE 依赖（约数百 MB）。

---

## 2. 使用

| 入口 | 动作 |
|---|---|
| `Tools → Format Oracle SQL` / 编辑器右键 | 格式化当前文件 |
| 快捷键 `Ctrl+Alt+Shift+F` | 同上 |
| `Tools → Check Oracle SQL Formatting` | **只校验不写回**，弹窗显示规则报告 |

设置页：**Settings → Tools → Oracle PL/SQL Formatter**，可逐条开关规则、调整每级缩进空格数。

> 动作默认只在 SQL 类扩展名文件上显示；可在设置页关闭「仅在 SQL 类扩展名文件上显示动作」
> 以作用于任意文本文件。

### 重要行为约定

- **始终全文格式化**，不支持仅格式化选区。规则依赖全文上下文（结构栈、WHERE 括号深度、
  SELECT 首字段列），裁剪选区会破坏正确性。
- **V6 原子写回**：先跑完 V1–V6 校验，通过后才在 `WriteCommandAction` 中一次性替换文档，
  单次 `Ctrl+Z` 即可整体撤销；不额外生成 `.bak`（IDEA Local History 已足够）。
- **校验失败绝不写回**：任何语义不守恒或不可收敛的结果只会弹出错误通知，文件保持原样。
- **只读文件需先放开写权限**：`cux_cst_margin_deduction_prt.pck` 等源文件权限为 `-r--r--r--`，
  IDEA 写回会失败。先 `chmod u+w FILE.pck`，或改用 CLI 的 `-o OUT.pck` 输出到新文件。
- **CRLF 自动保留**：词法层把 `\r\n` / `\r` 归一化为换行处理，输出按原文件换行风格还原，
  Windows 换行文件不会被改成 LF（见第 5 节）。

### 命令行（不装 IDEA 也能用 / 接 CI）

内核是零依赖纯 Java，已编译到 `out/`：

```bash
cd idea-sql-formatter
java -cp out cn.inovance.sqlfmt.core.Cli FILE.pck --check   # 只校验
java -cp out cn.inovance.sqlfmt.core.Cli FILE.pck            # 原地格式化（自动备份 .bak）
java -cp out cn.inovance.sqlfmt.core.Cli FILE.pck -o OUT.pck
java -cp out cn.inovance.sqlfmt.core.Cli FILE.pck --full --strict
```

`--full` 打开全部对齐规则，`--strict` 启用 R24 严格块缩进重算。

---

## 3. 规则映射表

| 规则 | 实现 | 默认 |
|---|---|---|
| G1 只改空白/大小写/逗号位置 | 全部规则 + `SafetyVerifier` | 内建 |
| G2 字符串、注释、命名参数 `=>` 保护 | `SqlLexer` / `Token.isProtected()` / `KeywordUppercaseRule` | 内建 |
| G3 SQL CASE 不按 PL/SQL 块处理 | `BlockStructureRule`（CASE 不列入 STARTERS）、`ParenCaseRule` | 内建 |
| G4 结构锚点、不用硬编码行号 | 全部规则 | 内建 |
| G5 写回前强制校验 | `SqlFormatter.format()` | 开 |
| G6 幂等 | **收敛循环**（最多 8 轮，见第 5 节） | 开 |
| R1/R2 关键字大写（白名单） | `KeywordUppercaseRule` | 开 |
| R3/R4/R7/R8/R24 块结构、ELSE 对齐、每级 2 空格 | `BlockStructureRule` | 开；严格重算关 |
| R5/R11/R22/R23 子查询与 CASE 列对齐 | `ParenCaseRule` | 关 |
| R6 区间整体平移 | **不纳入**：一次性、非幂等，与 G6 冲突 | — |
| R9/R12/R13/R14/R15/R16 列表列对齐、SET 等号对齐 | `ListAlignRule` | 关 |
| R10 前导逗号（逗号列 = 首元素列 − 2） | `LeadingCommaRule` | 开 |
| R17/R18/R19 WHERE 换行、AND 对齐、完整行括号深度 | `ClauseLayoutRule` | 开 |
| R20/R21 字段清单内空行清理 | `ClauseLayoutRule` | 开 |
| R25 SELECT 主子句以 SELECT 列为基准对齐 | `SelectClauseAlignRule` | 开 |
| E1–E8 合法例外 | `SqlLexer` 保护 + 规则跳过 guarded 行 | 内建 |
| V1 token 签名 / V2 字面量 / V3 逗号数 / V4 结构计数 | `SafetyVerifier` | 开 |
| V5 幂等 / V6 原子写回 | 收敛循环 / `WriteCommandAction`、`Cli` | 开 |

### 为什么「对齐类」默认关闭

规则手册第 12 章记录过教训：把泛化的 SELECT/FROM 对齐并入通用工具曾造成 **92 行回归**，
且手册中 R9–R16、R22/R23 本来就是针对 `cux_bi_cst_mar.pck` 的精确列号定制规则。
因此插件分层：

- **结构类（默认开）**：对任意 SQL 文件都安全，只调整块结构起始行，不动字段列；
- **对齐类（默认关）**：启发式实现，建议先跑 `Check` 预览，确认后再开；
- **对已按手册排版好的成品文件**：建议保持对齐类关闭，只用结构类，或直接 `--check`。

---

## 4. 架构

```
src/main/java/cn/inovance/sqlfmt/
├── core/                      零依赖纯 Java，可独立编译 / 单测 / 命令行 / CI
│   ├── Token.java             词法单元（含类型枚举）
│   ├── SqlLexer.java          G2 词法分析，跨行字符串与注释按行切分
│   ├── SqlDocument.java       行模型 + render()，保证可完整还原原文
│   ├── Keywords.java          R1 白名单（与 plsql_formatter.py 一致）
│   ├── FormatOptions.java     规则开关与三套预设
│   ├── Rule.java              规则接口
│   ├── SafetyVerifier.java    V1-V4 四重一致性校验
│   ├── SqlFormatter.java      编排 + V5 收敛循环 + 报告
│   ├── Cli.java               纯 Java 命令行入口
│   └── rules/
│       ├── KeywordUppercaseRule.java   R1/R2
│       ├── LeadingCommaRule.java       R10
│       ├── ClauseLayoutRule.java       R17-R21
│       ├── BlockStructureRule.java     R3/R4/R7/R8/R24
│       ├── ListAlignRule.java          R9/R12-R16
│       ├── ParenCaseRule.java          R5/R11/R22/R23
│       └── SelectClauseAlignRule.java R25
└── idea/                      薄适配层，仅依赖 IntelliJ Platform
    ├── FormatSqlAction.java       格式化动作
    ├── CheckSqlAction.java        校验（不写回）动作
    ├── SqlFormatSettings.java     设置持久化
    ├── SqlFormatConfigurable.java Settings 页面
    └── ActionSupport.java         扩展名判断 / 通知
```

设计要点：**core 完全不 import 任何 `com.intellij.*`**，所以同一套规则既跑在 IDE 里，
也能跑在命令行、pre-commit、CI 中；同时让内核能被 `javac` 直接编译验证。

---

## 5. 安全体系（V1–V6）

| 编号 | 检查 | 实现 |
|---|---|---|
| V1 | 非逗号 token 序列一致（WORD 统一大写后比较） | `SafetyVerifier.tokenSig` |
| V2 | 字符串 / 注释内容完全一致 | `literalSig` |
| V3 | 逗号总数守恒 | `commaCount` |
| V4 | 结构关键字计数一致 | `structSig` |
| V5 | 幂等 | 收敛循环 |
| V6 | 校验通过才原子写回 | `Result.ok` 门控 |

**关于 V5 收敛循环**：对齐类规则之间存在列号互相反馈（ParenCase 改了行缩进 →
下一轮 ListAlign 的基准列随之变化 → 注释跟随又变），单遍运行未必稳定。
`SqlFormatter` 会重复应用规则链直到输出为不动点（最多 8 轮），再对最终结果复验 V1–V4。
若 8 轮仍不收敛，返回 `ok=false` 并拒绝写回。这样交付的结果天然满足幂等。

自测与真实文件校验先后抓到并修复了 5 个真实缺陷：

1. 注释跟随基于未处理完的缩进（单遍不收敛）；
2. `indexOf` 命中注释内关键字导致列号自反馈；
3. `', NVL(...)'` 被误判为 `', ('` 子查询块；
4. **CRLF 文件 `\r` 被吞进注释/字符串 token** → V2 失败（1.0.1 修复：`SqlLexer` 把 CRLF/CR
   归一化为换行、`\r` 不进入任何 token，行注释扫描遇 `\r` 停止、跨行字面量按三种换行切分；
   `SqlDocument` 记录并还原原换行风格）；
5. **R10 误删含字面量的行**：`'N',` 这类行的 `STR` 是 protected token，不计入
   `codeTokens()`，被判为「逗号独占的占位行」整行删除（1.0.1 修复：改用整行非空白 token
   判定，并以最后一个非空白 token 判断行尾逗号）。

---

## 6. 本地编译与自测

```bash
ROOT=idea-sql-formatter
OUT=$ROOT/out
mkdir -p "$OUT"

# 仅内核（不需要 IDEA）
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT"/src/main/java/cn/inovance/sqlfmt/core/*.java \
  "$ROOT"/src/main/java/cn/inovance/sqlfmt/core/rules/*.java

# 含 IDEA 适配层（用本机 IDEA 自带 platform jar）
CP="/Applications/IntelliJ IDEA.app/Contents/lib/*"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
  "$ROOT"/src/main/java/cn/inovance/sqlfmt/idea/*.java

# 自测（33 项，含两个真实 .pck 只读校验）
javac -encoding UTF-8 -cp "$OUT" -d "$OUT" \
  "$ROOT"/src/test/java/cn/inovance/sqlfmt/*.java
java -cp "$OUT" cn.inovance.sqlfmt.FormatterSelfTest
java -cp "$OUT" cn.inovance.sqlfmt.DiagnoseIdempotency   # 单遍幂等诊断
```

---

## 7. 已知限制

1. **R6 未实现**：区间整体平移是一次性非幂等操作，与 G6 冲突，插件不提供。
2. **R22/R23 CASE 内部列对齐为启发式**：按 CASE 列 +2 / +4 重排，不复刻手册
   `1714-1732` 模板中每一条续行的精确列号；需要完全一致的成品请沿用
   `/tmp/fmt_case_inner.py` 那类一次性脚本。
3. **不支持选区格式化**（见第 2 节）。
4. **对齐类规则默认关闭**，且对刻意对齐的 SQL 仍可能改变列号，使用前务必 `Check`。
5. **未做 PSI / Language 级集成**：插件通过 AnAction 处理文档文本，不接管 IDE 原生
   `Reformat Code`（Ctrl+Alt+L）。好处是不依赖 Database Tools 的 SQL 语言实现，
   对 `.pck` 这类非标准扩展名同样可用。
6. `gradle-wrapper.jar` 未随附（网络受限），按第 1 节方式 B 补齐。
7. **升级 jar 后必须重启 IDEA**：IntelliJ 会缓存已加载的插件 class，从磁盘覆盖安装
   后需重启才生效。曾出现「源码已修复但仍报 V2 失败」——原因就是 `build/` 里
   一直只有旧的 1.0.0 jar。改完内核务必重新打包并覆盖安装。
8. **`--full` / `--strict` 会有额外改动，但属预期**：`--full` 打开启发式对齐类规则，
   `--strict` 启用 R24 严格块缩进重算，两者仍全部通过 V1–V6；日常用默认配置即可，
   且默认配置下格式化结果幂等。
9. **R25 的边界（对应手册 E8）**：基准由**行首 `SELECT`** 建立；`FROM (SELECT ...)`
   这类内联子查询不建立基准，其内部 `FROM / WHERE` 保持原样，不按外层 `SELECT` 列重排。
   若需把内联子查询也纳入，须先定义「行内 `SELECT` 列」的基准。`AND` 不属 R25，
   仍按 R17 = `WHERE + 2`，会随基准联动。

---

## 8. 相关文件

| 文件 | 说明 |
|---|---|
| `PLSQL_格式化规则.md` | 规则权威来源（本插件的依据） |
| `/Users/zzz/Develop/汇川/plsql_formatter.py` | 早期 Python 通用工具（仅基础三件套） |
| `/Users/zzz/Develop/汇川/cux_bi_cst_mar.pck` | 已按规则排版的成品（本插件未改动） |
| `build/Oracle-PLSQL-Formatter-1.0.2.jar` | 可直接安装的插件包（v1.0.2，新增 R25） |
| `/Users/zzz/Develop/汇川/cux_cst_margin_deduction_prt.formatted.pck` | 按修复后工具生成的格式化副本；原只读文件未改动 |
