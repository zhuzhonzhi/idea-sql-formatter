package cn.inovance.sqlfmt.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import cn.inovance.sqlfmt.core.FormatOptions;

/** 设置持久化：与规则手册的规则编号一一对应。 */
@State(name = "OracleSqlFormatterSettings", storages = @Storage("oracleSqlFormatter.xml"))
public final class SqlFormatSettings
        implements PersistentStateComponent<SqlFormatSettings.State> {

    public static final class State {
        public boolean uppercaseKeywords = true;   // R1/R2
        public boolean leadingComma = true;        // R10
        public boolean blockStructure = true;      // R3/R4/R7/R8/R24
        public boolean whereAnd = true;            // R17/R18/R19
        public boolean blankLines = true;          // R20/R21
        public boolean selectClauseAlign = true;   // R25
        public boolean strictBlockIndent = false;  // R24 严格重算
        public boolean listAlign = false;          // R9/R12-R16
        public boolean parenCaseAlign = false;     // R5/R11/R22/R23
        public boolean followComments = true;      // R8
        public int indentSize = 2;                 // R24
        public boolean verify = true;              // V1-V6
        public boolean onlyForSqlFiles = true;     // 仅在 SQL 类文件上显示动作

        public boolean matches(State o) {
            return uppercaseKeywords == o.uppercaseKeywords
                    && leadingComma == o.leadingComma
                    && blockStructure == o.blockStructure
                    && whereAnd == o.whereAnd
                    && blankLines == o.blankLines
                    && selectClauseAlign == o.selectClauseAlign
                    && strictBlockIndent == o.strictBlockIndent
                    && listAlign == o.listAlign
                    && parenCaseAlign == o.parenCaseAlign
                    && followComments == o.followComments
                    && indentSize == o.indentSize
                    && verify == o.verify
                    && onlyForSqlFiles == o.onlyForSqlFiles;
        }
    }

    private State state = new State();

    public static SqlFormatSettings getInstance() {
        return ApplicationManager.getApplication().getService(SqlFormatSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public FormatOptions toOptions() {
        FormatOptions o = new FormatOptions();
        o.uppercaseKeywords = state.uppercaseKeywords;
        o.leadingComma = state.leadingComma;
        o.blockStructure = state.blockStructure;
        o.whereAnd = state.whereAnd;
        o.blankLines = state.blankLines;
        o.selectClauseAlign = state.selectClauseAlign;
        o.strictBlockIndent = state.strictBlockIndent;
        o.listAlign = state.listAlign;
        o.parenCaseAlign = state.parenCaseAlign;
        o.followComments = state.followComments;
        o.indentSize = state.indentSize;
        o.verify = state.verify;
        return o;
    }
}
