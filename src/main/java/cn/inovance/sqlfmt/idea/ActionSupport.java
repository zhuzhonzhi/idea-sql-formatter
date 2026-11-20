package cn.inovance.sqlfmt.idea;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/** 动作层共用工具。 */
final class ActionSupport {

    private static final String GROUP_ID = "Oracle SQL Formatter";

    private static final Set<String> SQL_EXTENSIONS = Set.of(
            "sql", "pck", "pks", "pls", "plb", "pkb", "ddl", "dml",
            "prc", "fnc", "trg", "pkg", "tpb", "tps", "vw", "tab", "seq");

    private ActionSupport() {}

    static boolean isSqlFile(@Nullable VirtualFile file) {
        if (file == null) return false;
        String ext = file.getExtension();
        return ext != null && SQL_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT));
    }

    static void notify(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(title, content, type)
                .notify(project);
    }
}
