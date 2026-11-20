package cn.inovance.sqlfmt.idea;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.SqlFormatter;

/**
 * 主动作：格式化当前文件。
 *
 * 注意：规则依赖全文上下文（结构栈、WHERE 括号深度、SELECT 首字段列），
 * 因此始终对整篇文档格式化，不对局部选区做裁剪 —— 选区格式化会破坏规则正确性。
 * V6：先通过 V1-V6 校验，再在 WriteCommandAction 中一次性替换（可撤销、原子）。
 */
public final class FormatSqlAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;

        Document document = editor.getDocument();
        String src = document.getText();
        FormatOptions options = SqlFormatSettings.getInstance().toOptions();
        SqlFormatter.Result result = SqlFormatter.format(src, options);

        if (!result.ok) {
            ActionSupport.notify(project, "格式化已取消", result.error, NotificationType.ERROR);
            return;
        }
        if (result.text.equals(src)) {
            ActionSupport.notify(project, "Oracle SQL Formatter", "无改动，文件已符合规则",
                    NotificationType.INFORMATION);
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, () -> document.setText(result.text));
        ActionSupport.notify(project, "格式化完成",
                "改动 " + result.changedLines + " 行\n" + SqlFormatter.report(result.changes),
                NotificationType.INFORMATION);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean enabled = project != null && editor != null;
        if (enabled && SqlFormatSettings.getInstance().getState().onlyForSqlFiles) {
            enabled = ActionSupport.isSqlFile(file);
        }
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
