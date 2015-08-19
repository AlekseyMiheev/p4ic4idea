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
package net.groboclown.idea.p4ic.ui.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsConnectionProblem;
import com.intellij.openapi.vfs.VirtualFile;
import com.perforce.p4java.client.IClientSummary;
import net.groboclown.idea.p4ic.P4Bundle;
import net.groboclown.idea.p4ic.background.VcsFutureSetter;
import net.groboclown.idea.p4ic.config.P4Config;
import net.groboclown.idea.p4ic.config.P4ConfigUtil;
import net.groboclown.idea.p4ic.config.ServerConfig;
import net.groboclown.idea.p4ic.server.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Used by the setup configuration UI to test the connection settings and return
 * the list of clients owned by the user.
 */
public class UserClientsLoader {
    private final Project project;
    private final P4Config config;

    public UserClientsLoader(@NotNull Project project, @NotNull P4Config config) {
        this.project = project;
        this.config = config;
    }


    /**
     *
     * @return null when any connection could not be made.
     */
    @Nullable
    public List<String> loadClients() {
        if (config.getConnectionMethod() == P4Config.ConnectionMethod.REL_P4CONFIG) {
            // relative p4config files do not support this API.  However, we still need
            // to properly check them.
            if (! checkAllConfigFiles()) {
                // There was a problem in the config files, so return null.
                return null;
            }
            return Collections.emptyList();
        }
        ServerConfig serverConfig = ServerConfig.createNewServerConfig(config);
        if (serverConfig == null) {
            Messages.showMessageDialog(project,
                    P4Bundle.message("configuration.error.not-fully-qualified"),
                    P4Bundle.message("configuration.check-connection"),
                    Messages.getErrorIcon());
            return null;
        }

        try {
            return loadClientsFor(null, serverConfig);
        } catch (VcsConnectionProblem e) {
            Messages.showMessageDialog(project,
                    P4Bundle.message("configuration.connection-problem", e.getMessage()),
                    P4Bundle.message("configuration.check-connection"),
                    Messages.getErrorIcon());
            return null;
        }
    }

    private boolean checkAllConfigFiles() {
        assert config.getConnectionMethod() == P4Config.ConnectionMethod.REL_P4CONFIG;
        final String configFile = config.getConfigFile();
        if (configFile == null) {
            Messages.showMessageDialog(project,
                    P4Bundle.message("configuration.error.no-p4config-found",
                            config.getConfigFile(), project.getBaseDir()),
                    P4Bundle.message("configuration.check-connection"),
                    Messages.getErrorIcon());
            return false;
        }
        Map<VirtualFile, P4Config> configsMap = P4ConfigUtil.loadProjectP4Configs(project,
                configFile, true);
        if (configsMap.isEmpty()) {
            Messages.showMessageDialog(project,
                    P4Bundle.message("configuration.error.no-p4config-found",
                            config.getConfigFile(), project.getBaseDir()),
                    P4Bundle.message("configuration.check-connection"),
                    Messages.getErrorIcon());
            return false;
        } else {
            int problemCount = 0;
            StringBuilder problems = new StringBuilder();
            for (Map.Entry<VirtualFile, P4Config> en: configsMap.entrySet()) {
                if (en.getValue().getClientname() == null || en.getValue().getClientname().length() <= 0) {
                    problems.append(P4Bundle.message("configuration.error.no-client.one",
                            en.getKey().getPath()));
                    problemCount++;
                    continue;
                }
                ServerConfig serverConfig = ServerConfig.createNewServerConfig(en.getValue());
                if (serverConfig == null) {
                    problems.append(P4Bundle.message("configuration.error.not-fully-qualified.one",
                            en.getKey().getPath()));
                    problemCount++;
                    continue;
                }
                final List<String> clients;
                try {
                    clients = loadClientsFor(en.getKey().getPath(), serverConfig);
                    if (!clients.contains(en.getValue().getClientname())) {
                        problems.append(P4Bundle.message("configuration.error.not-exist-client.one",
                                en.getKey().getPath(), en.getValue().getUsername()));
                        problemCount++;
                    }
                } catch (VcsConnectionProblem e) {
                    problems.append(P4Bundle.message("configuration.error.connection-failure",
                            en.getKey().getPath(), en.getValue().getUsername(), e.getMessage()));
                    problemCount++;
                }
            }
            if (problemCount > 0) {
                Messages.showMessageDialog(project,
                        P4Bundle.message("configuration.error.problem-list", problemCount, problems),
                        P4Bundle.message("configuration.check-connection"),
                        Messages.getErrorIcon());
                return false;
            }
            return true;
        }
    }

    @NotNull
    private List<String> loadClientsFor(@Nullable String desc, @NotNull ServerConfig serverConfig)
            throws VcsConnectionProblem {
        // TODO should run with a progress bar
        try {
            ServerStatus serverStatus = ServerStoreService.getInstance().getServerStatus(project, serverConfig);

            P4Exec exec = new P4Exec(serverStatus, config.getClientname(),
                    ConnectionHandler.getHandlerFor(serverConfig), new OnServerConfigurationProblem() {
                @Override
                public void onInvalidConfiguration(@NotNull VcsFutureSetter<Boolean> future,
                        @Nullable ServerConfig config, @Nullable String message) {
                    Messages.showMessageDialog(project,
                            P4Bundle.message("configuration.connection-problem", message),
                            P4Bundle.message("configuration.check-connection"),
                            Messages.getErrorIcon());
                    future.set(Boolean.FALSE);
                }
            });
            // Checks the connection, username, and password
            List<IClientSummary> clients = exec.getClientsForUser(project);
            List<String> ret = new ArrayList<String>(clients.size());
            for (IClientSummary cs : clients) {
                if (cs != null) {
                    ret.add(cs.getName());
                }
            }
            return ret;
        } finally {
            ServerStoreService.getInstance().removeServerConfig(project, serverConfig);
        }
    }
}