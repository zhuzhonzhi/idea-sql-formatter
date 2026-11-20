package cn.inovance.sqlfmt.idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import cn.inovance.sqlfmt.core.FormatOptions;
import cn.inovance.sqlfmt.core.SqlFormatter;

/** 检查动作：只运行校验并展示规则报告，绝不修改文件。 */
public final class CheckSqlAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;

        String src = editor.getDocument().getText();
        FormatOptions options = SqlFormatSettings.getInstance().toOptions();
        String report = SqlFormatter.check(src, options);
        Messages.showInfoMessage(project, report, "Oracle SQL Formatter - 检查");
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
