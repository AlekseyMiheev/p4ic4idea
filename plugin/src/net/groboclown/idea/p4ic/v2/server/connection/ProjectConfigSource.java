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

package net.groboclown.idea.p4ic.v2.server.connection;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import net.groboclown.idea.p4ic.config.*;
import net.groboclown.idea.p4ic.server.exceptions.P4InvalidConfigException;
import net.groboclown.idea.p4ic.v2.server.cache.ClientServerId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Maps the specific Perforce configuration object to the corresponding Project directories it covers.
 * <p/>
 * These are created via {@link P4ConfigProject#loadProjectConfigSources()}.
 */
public class ProjectConfigSource {

    private final Project project;
    private final List<VirtualFile> projectSourceDirs;
    private final String clientName;
    private final ServerConfig configuration;
    private final ClientServerId clientServerId;

    public static class Builder {
        private final Project project;
        private final String clientName;
        private final ServerConfig serverConfig;
        private final P4Config baseConfig;
        private final Set<VirtualFile> dirs = new HashSet<VirtualFile>();

        public Builder(@NotNull Project project, @NotNull P4Config config) {
            this.project = project;
            this.baseConfig = config;
            this.clientName = config.getClientname();
            this.serverConfig = ServerConfig.createNewServerConfig(project, config);
        }

        public boolean isInvalid() {
            return serverConfig == null;
        }


        public P4Config getBaseConfig() {
            return baseConfig;
        }


        public Collection<VirtualFile> getDirs() {
            return Collections.unmodifiableCollection(dirs);
        }


        public boolean isSame(@NotNull P4Config other) {
            return new ManualP4Config(serverConfig, clientName).equals(other);
        }

        public void add(@NotNull VirtualFile dir) {
            dirs.add(dir);
        }

        public ProjectConfigSource create() throws P4InvalidConfigException {
            if (isInvalid()) {
                throw new IllegalStateException("must call isInvalid before calling this function");
            }
            return new ProjectConfigSource(project, new ArrayList<VirtualFile>(dirs),
                    clientName, serverConfig);
        }

        @Override
        public String toString() {
            return clientName + "; " + serverConfig + "; " + dirs;
        }
    }


    ProjectConfigSource(@NotNull Project project, @NotNull List<VirtualFile> projectSourceDirs,
            @Nullable String clientName, @NotNull ServerConfig configuration) {
        this.project = project;
        this.projectSourceDirs = Collections.unmodifiableList(projectSourceDirs);
        this.clientName = clientName;
        this.configuration = configuration;
        this.clientServerId = ClientServerId.create(configuration, clientName);
    }

    @NotNull
    public Project getProject() {
        return project;
    }

    @Nullable
    public String getClientName() {
        return clientName;
    }

    @NotNull
    public ServerConfig getServerConfig() {
        return configuration;
    }

    @NotNull
    public ClientServerId getClientServerId() {
        return clientServerId;
    }

    @NotNull
    public List<VirtualFile> getProjectSourceDirs() {
        return projectSourceDirs;
    }

    @Override
    public String toString() {
        return getClientServerId() + " - " + getProjectSourceDirs();
    }
}
