/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.groboclown.p4plugin.ui;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListDecorator;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import net.groboclown.p4.server.api.ClientServerRef;
import net.groboclown.p4.server.api.values.P4ChangelistId;
import net.groboclown.p4plugin.P4Bundle;
import net.groboclown.p4plugin.extension.P4Vcs;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class P4ChangeListDecorator implements ChangeListDecorator, ProjectComponent {
    private final Project project;

    public static class ChangelistConnectionInfo {
        private final List<P4ChangelistId> validIds = new ArrayList<>();
        private final List<ClientServerRef> defaults = new ArrayList<>();
        private final List<ClientServerRef> unsynced = new ArrayList<>();
        private final List<ClientServerRef> unknowns = new ArrayList<>();
        private final List<ClientServerRef> offline = new ArrayList<>();
        private final boolean hasOneServer;

        public ChangelistConnectionInfo(int serverCount) {
            this.hasOneServer = serverCount == 1;
        }

        public void addOffline(@NotNull ClientServerRef ref, @NotNull P4ChangelistId p4cl) {
            offline.add(ref);
        }

        public void addOnline(@NotNull ClientServerRef ref, @NotNull P4ChangelistId p4cl) {
            switch (p4cl.getState()) {
                case NUMBERED:
                    validIds.add(p4cl);
                    break;
                case PENDING_CREATION:
                    unsynced.add(ref);
                    break;
                case DEFAULT:
                    defaults.add(ref);
                default:
                    unknowns.add(ref);
            }
        }
    }

    public P4ChangeListDecorator(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void decorateChangeList(LocalChangeList changeList, ColoredTreeCellRenderer cellRenderer, boolean selected, boolean expanded, boolean hasFocus) {
        if (isProjectInvalid()) {
            return;
        }
        final P4Vcs vcs = P4Vcs.getInstance(project);
        // FIXME
        throw new IllegalStateException("not implemented");
        /*
        final P4ChangeListMapping changeListMapping = P4ChangeListMapping.getInstance(project);
        final List<P4Server> servers = vcs.getP4Servers();
        ChangelistConnectionInfo info = new ChangelistConnectionInfo(servers.size());
        for (P4Server server: servers) {
            final P4ChangeListId p4cl = changeListMapping.getPerforceChangelistFor(server, changeList);
            if (p4cl != null) {
                if (server.isWorkingOnline()) {
                    info.addOnline(server.getClientServerId(), p4cl);
                } else {
                    info.addOffline(server.getClientServerId(), p4cl);
                }
            }
        }

        decorateInfo(info, cellRenderer);
        */
    }

    public static void decorateInfo(@NotNull ChangelistConnectionInfo info,
            @NotNull ColoredTreeCellRenderer cellRenderer) {
        boolean hasOne = false;
        if (info.hasOneServer && info.validIds.size() == 1) {
            hasOne = true;
            cellRenderer.append(P4Bundle.message("changelist.render", info.validIds.get(0).getChangelistId()),
                    SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        } else if (! info.validIds.isEmpty()) {
            hasOne = true;
            Iterator<P4ChangelistId> iter = info.validIds.iterator();
            P4ChangelistId next = iter.next();
            StringBuilder sb = new StringBuilder(P4Bundle.message("changelist.render-many.first",
                    next.getClientname(), next.getChangelistId()));
            while (iter.hasNext()) {
                next = iter.next();
                sb.append(P4Bundle.message("changelist.render-many.after",
                        next.getClientname(), next.getChangelistId()));
            }
            cellRenderer.append(sb.toString(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        }

        if (info.hasOneServer && info.defaults.size() == 1) {
            String msg = P4Bundle.message("changelist.decorator.default");
            cellRenderer.append(msg, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
            hasOne = true;
        } else if (! info.defaults.isEmpty()) {
            Iterator<ClientServerRef> iter = info.defaults.iterator();
            ClientServerRef next = iter.next();
            StringBuilder sb = new StringBuilder();
            if (hasOne) {
                sb.append(P4Bundle.message("changelist.decorator.default.second.first", next.getClientName()));
            } else {
                sb.append(P4Bundle.message("changelist.decorator.default.first.first", next.getClientName()));
            }
            while (iter.hasNext()) {
                next = iter.next();
                sb.append(P4Bundle.message("changelist.decorator.default.middle", next.getClientName()));
            }
            sb.append(P4Bundle.message("changelist.decorator.default.end"));
            cellRenderer.append(sb.toString(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
            hasOne = true;
        }

        if (info.hasOneServer && info.unknowns.size() == 1) {
            String msg = P4Bundle.message("changelist.decorator.unknowns");
            cellRenderer.append(msg, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
            hasOne = true;
        } else if (! info.unknowns.isEmpty()) {
            Iterator<ClientServerRef> iter = info.unknowns.iterator();
            ClientServerRef next = iter.next();
            StringBuilder sb = new StringBuilder();
            if (hasOne) {
                sb.append(P4Bundle.message("changelist.decorator.unknowns.second.first", next.getClientName()));
            } else {
                sb.append(P4Bundle.message("changelist.decorator.unknowns.first.first", next.getClientName()));
            }
            while (iter.hasNext()) {
                next = iter.next();
                sb.append(P4Bundle.message("changelist.decorator.unknowns.middle", next.getClientName()));
            }
            sb.append(P4Bundle.message("changelist.decorator.unknowns.end"));
            cellRenderer.append(sb.toString(), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
            hasOne = true;
        }

        if (info.hasOneServer && info.unsynced.size() == 1) {
            String msg = P4Bundle.message("changelist.decorator.unsynced");
            cellRenderer.append(msg, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
        } else if (! info.unsynced.isEmpty()) {
            Iterator<ClientServerRef> iter = info.unsynced.iterator();
            ClientServerRef next = iter.next();
            StringBuilder sb = new StringBuilder();
            if (hasOne) {
                sb.append(P4Bundle.message("changelist.decorator.unsynced.second.first", next.getClientName()));
            } else {
                sb.append(P4Bundle.message("changelist.decorator.unsynced.first.first", next.getClientName()));
            }
            while (iter.hasNext()) {
                next = iter.next();
                sb.append(P4Bundle.message("changelist.decorator.unsynced.middle", next.getClientName()));
            }
            sb.append(P4Bundle.message("changelist.decorator.unsynced.end"));
            cellRenderer.append(sb.toString(), SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
            hasOne = true;
        }

        if (info.hasOneServer && info.offline.size() == 1) {
            String msg = P4Bundle.message("changelist.decorator.offline");
            cellRenderer.append(msg, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
        } else if (!info.offline.isEmpty()) {
            Iterator<ClientServerRef> iter = info.offline.iterator();
            ClientServerRef next = iter.next();
            StringBuilder sb = new StringBuilder();
            if (hasOne) {
                sb.append(P4Bundle.message("changelist.decorator.offline.second.first", next.getClientName()));
            } else {
                sb.append(P4Bundle.message("changelist.decorator.offline.first.first", next.getClientName()));
            }
            while (iter.hasNext()) {
                next = iter.next();
                sb.append(P4Bundle.message("changelist.decorator.offline.middle", next.getClientName()));
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(P4Bundle.message("changelist.decorator.offline.end"));
            cellRenderer.append(sb.toString(), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
        }
    }

    @Override
    public void projectOpened() {
        // ignore
    }

    @Override
    public void projectClosed() {
        // ignore
    }

    @Override
    public void initComponent() {
        // ignore
    }

    @Override
    public void disposeComponent() {
        // ignore
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "PerforceChangeListDecorator";
    }


    private boolean isProjectInvalid() {
        return ! P4Vcs.isProjectValid(project);
    }
}