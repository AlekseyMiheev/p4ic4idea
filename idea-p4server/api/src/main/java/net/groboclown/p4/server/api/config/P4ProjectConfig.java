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
package net.groboclown.p4.server.api.config;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Manages the configuration for an entire project.  The configuration must be refined through a file path,
 * or by getting a list of configurations.
 *
 * TODO this needs to be re-thought out.  It seems to be better implemented by the
 * {@link net.groboclown.p4.server.api.ProjectConfigRegistry}.  As a stateful object, it is able to
 * contain the initial setup for a project with all of its settings.  Configurations with problems would be
 * reported to the user, and valid ones would be registered.  However, the user would want all validated configurations
 * to be registered.  The registration of the configs the user wants would always be registered at startup time,
 * even if it becomes bad.  The bad configs would be reported to the user.
 *
 * That said, this class needs to redefine its purpose.
 */
public interface P4ProjectConfig extends Disposable {

    boolean isDisposed();

    void refresh();

    /**
     *
     * @return all valid and invalid client configurations.
     */
    @NotNull
    Collection<ClientConfigSetup> getClientConfigSetups();

    /**
     *
     * @return all valid client configurations.
     */
    @NotNull
    Collection<ClientConfig> getClientConfigs();

    @NotNull
    Collection<ServerConfig> getServerConfigs();

    @Nullable
    ClientConfig getClientConfigFor(@NotNull FilePath file);

    @Nullable
    ClientConfig getClientConfigFor(@NotNull VirtualFile file);

    @NotNull
    Collection<ConfigProblem> getConfigProblems();

    /**
     * Should not consider "no client name defined" as an error.
     *
     * @return true if there are config errors.
     */
    boolean hasConfigErrors();

    @NotNull
    Project getProject();
}
