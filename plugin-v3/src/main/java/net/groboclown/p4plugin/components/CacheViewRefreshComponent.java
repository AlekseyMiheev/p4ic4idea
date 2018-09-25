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

package net.groboclown.p4plugin.components;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import net.groboclown.idea.p4ic.compat.VcsCompat;
import net.groboclown.p4.server.api.cache.messagebus.FileCacheUpdatedMessage;
import net.groboclown.p4.server.api.messagebus.MessageBusClient;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CacheViewRefreshComponent implements ProjectComponent {
    private static final String COMPONENT_NAME = "p4ic4idea:CacheViewRefreshComponent";

    private final Project project;

    public CacheViewRefreshComponent(@NotNull Project project) {
        this.project = project;
    }

    public void refreshFileView(@NotNull Collection<VirtualFile> affectedFiles) {
        VcsFileUtil.markFilesDirty(project, affectedFiles);
        VcsCompat.getInstance().refreshFiles(project, affectedFiles);
    }

    @NotNull
    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }

    @Override
    public void initComponent() {
        MessageBusClient.ProjectClient projectClient = MessageBusClient.forProject(project, project);
        FileCacheUpdatedMessage.addListener(projectClient, this, (e) -> {
            refreshFileView(e.getFiles());
        });
    }

    @Override
    public void projectOpened() {
    }

    @Override
    public void projectClosed() {
        // Do nothing; listener automatically disposed.
    }

    @Override
    public void disposeComponent() {
        // Do nothing; listener automatically disposed.
    }
}
