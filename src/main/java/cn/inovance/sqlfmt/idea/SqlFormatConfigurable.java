package cn.inovance.sqlfmt.idea;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.inovance.sqlfmt.idea.SqlFormatSettings.State;

/** Settings | Tools | Oracle PL/SQL Formatter。 */
public final class SqlFormatConfigurable implements Configurable {

    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();
    private JSpinner indentSpinner;
    private JPanel root;

    @Override
    public @Nls String getDisplayName() {
        return "Oracle PL/SQL Formatter";
    }

    @Override
    public @Nullable JComponent createComponent() {
        root = new JPanel(new BorderLayout());
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        add(content, "uppercaseKeywords", "R1/R2  关键字大写（白名单，标识符不动）");
        add(content, "leadingComma", "R10     前导逗号：逗号置于行首，列 = 首元素列 - 2");
        add(content, "blockStructure", "R3/R4/R7/R24  块结构：同级块同列、每级 2 空格、ELSE 对齐 IF");
        add(content, "whereAnd", "R17/R18/R19  WHERE 单独换行、AND 按 WHERE 首条件对齐");
        add(content, "selectClauseAlign", "R25  SELECT 主子句（INTO/FROM/WHERE/GROUP/ORDER 等）与 SELECT 同列");
        add(content, "blankLines", "R20/R21  删除字段清单内空行，保留语句间空行");
        add(content, "followComments", "R8      纯行注释行跟随相邻代码行");
        add(content, "strictBlockIndent", "R24 严格模式  完整按「父块 + 2」重算块首行（激进）");
        add(content, "listAlign", "R9/R12-R16  列表列对齐（启发式，建议先预览）");
        add(content, "parenCaseAlign", "R5/R11/R22/R23  子查询 / CASE 对齐（启发式）");
        add(content, "verify", "V1-V6  安全闸门：语义不守恒或不可收敛时拒绝写回");
        add(content, "onlyForSqlFiles", "仅在 SQL 类扩展名文件上显示动作");

        JPanel indentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        indentPanel.add(new JLabel("每级缩进空格数 (R24)："));
        indentSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
        indentPanel.add(indentSpinner);
        content.add(indentPanel);

        root.add(new JScrollPane(content), BorderLayout.CENTER);
        reset();
        return root;
    }

    private void add(JPanel parent, String key, String text) {
        JCheckBox box = new JCheckBox(text);
        boxes.put(key, box);
        parent.add(box);
    }

    private State uiState() {
        State s = new State();
        s.uppercaseKeywords = checked("uppercaseKeywords");
        s.leadingComma = checked("leadingComma");
        s.blockStructure = checked("blockStructure");
        s.whereAnd = checked("whereAnd");
        s.selectClauseAlign = checked("selectClauseAlign");
        s.blankLines = checked("blankLines");
        s.followComments = checked("followComments");
        s.strictBlockIndent = checked("strictBlockIndent");
        s.listAlign = checked("listAlign");
        s.parenCaseAlign = checked("parenCaseAlign");
        s.verify = checked("verify");
        s.onlyForSqlFiles = checked("onlyForSqlFiles");
        s.indentSize = (Integer) indentSpinner.getValue();
        return s;
    }

    private boolean checked(String key) {
        JCheckBox box = boxes.get(key);
        return box != null && box.isSelected();
    }

    @Override
    public boolean isModified() {
        return !uiState().matches(SqlFormatSettings.getInstance().getState());
    }

    @Override
    public void apply() {
        SqlFormatSettings.getInstance().loadState(uiState());
    }

    @Override
    public void reset() {
        State s = SqlFormatSettings.getInstance().getState();
        boxes.get("uppercaseKeywords").setSelected(s.uppercaseKeywords);
        boxes.get("leadingComma").setSelected(s.leadingComma);
        boxes.get("blockStructure").setSelected(s.blockStructure);
        boxes.get("whereAnd").setSelected(s.whereAnd);
        boxes.get("selectClauseAlign").setSelected(s.selectClauseAlign);
        boxes.get("blankLines").setSelected(s.blankLines);
        boxes.get("followComments").setSelected(s.followComments);
        boxes.get("strictBlockIndent").setSelected(s.strictBlockIndent);
        boxes.get("listAlign").setSelected(s.listAlign);
        boxes.get("parenCaseAlign").setSelected(s.parenCaseAlign);
        boxes.get("verify").setSelected(s.verify);
        boxes.get("onlyForSqlFiles").setSelected(s.onlyForSqlFiles);
        indentSpinner.setValue(s.indentSize);
    }

    @Override
    public void disposeUIResources() {
        root = null;
        boxes.clear();
    }
}
