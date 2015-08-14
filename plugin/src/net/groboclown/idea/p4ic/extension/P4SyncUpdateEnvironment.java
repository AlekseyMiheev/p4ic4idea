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

package net.groboclown.idea.p4ic.extension;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.*;
import net.groboclown.idea.p4ic.config.Client;
import net.groboclown.idea.p4ic.extension.P4RevisionNumber.RevType;
import net.groboclown.idea.p4ic.server.P4FileInfo;
import net.groboclown.idea.p4ic.server.P4FileInfo.ClientAction;
import net.groboclown.idea.p4ic.server.exceptions.P4InvalidConfigException;
import net.groboclown.idea.p4ic.ui.sync.SyncOptionConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;

public class P4SyncUpdateEnvironment implements UpdateEnvironment {
    private static final Logger LOG = Logger.getInstance(P4SyncUpdateEnvironment.class);

    private final P4Vcs vcs;

    private final SyncOptionConfigurable syncOptions = new SyncOptionConfigurable();

    public P4SyncUpdateEnvironment(final P4Vcs vcs) {
        this.vcs = vcs;
    }

    @Override
    public void fillGroups(final UpdatedFiles updatedFiles) {
        // No non-standard file status, so ignored.
    }

    @NotNull
    @Override
    public UpdateSession updateDirectories(@NotNull final FilePath[] contentRoots, final UpdatedFiles updatedFiles,
            final ProgressIndicator progressIndicator, @NotNull final Ref<SequentialUpdatesContext> context)
            throws ProcessCanceledException {
        // Run the Perforce operation in the current thread, because that's the context in which this operation
        // is expected to run.

        LOG.info("updateDirectories: sync options are " + syncOptions.getCurrentOptions());

        final SyncUpdateSession session = new SyncUpdateSession();
        final Map<String, FileGroup> groups = sortByFileGroupId(updatedFiles.getTopLevelGroups(), null);
        final Map<Client, List<FilePath>> clientRoots = findClientRoots(contentRoots, session);

        for (Entry<Client, List<FilePath>> entry: clientRoots.entrySet()) {
            Client client = entry.getKey();
            try {
                // Get the revision or changelist from the Configurable that the user wants to sync to.
                final List<P4FileInfo> results = client.getServer().synchronizeFiles(
                        entry.getValue(),
                        syncOptions.getRevision(),
                        syncOptions.getChangelist(),
                        syncOptions.isForceSync(), session.exceptions);
                for (P4FileInfo file: results) {
                    updateFileInfo(file);
                    addToGroup(file, groups);
                }
            } catch (VcsException e) {
                session.exceptions.add(e);
            }
        }

        return session;
    }


    /**
     * File was synchronized, so we need to inform Idea that the file state
     * needs to be refreshed.
     *
     * @param file p4 file information
     */
    private void updateFileInfo(@Nullable final P4FileInfo file) {
        if (file != null) {
            final FilePath path = file.getPath();
            path.hardRefresh();
        }
    }

    private void addToGroup(@Nullable final P4FileInfo file,
            @NotNull final Map<String, FileGroup> groups) {
        final String groupId = getGroupIdFor(file);
        if (groupId != null) {
            final FileGroup group = groups.get(groupId);
            if (group != null) {
                group.add(file.getPath().getIOFile().getAbsolutePath(),
                        P4Vcs.getKey(), new P4RevisionNumber(file.getDepotPath(), file, RevType.HAVE));
            } else {
                LOG.warn("Unknown group " + groupId + " for action " + file.getClientAction() +
                        "; caused by synchronizing " + file.getPath());
            }
        }
    }


    @Nullable
    private String getGroupIdFor(@Nullable final P4FileInfo file) {
        if (file == null) {
            return null;
        }
        LOG.info("sync: " + file.getClientAction() + " / " + file.getPath());
        if (file.getClientAction() == ClientAction.NONE) {
            return FileGroup.UPDATED_ID;
        }
        return file.getClientAction().getFileGroupId();
    }

    @Nullable
    @Override
    public Configurable createConfigurable(final Collection<FilePath> files) {
        // Allow for the user to select the right revision for synchronizing
        return syncOptions;
    }

    @Override
    public boolean validateOptions(final Collection<FilePath> roots) {
        // This checks to make sure the selected files allow for this option to be shown.
        // We allow update on any file or directory that's under a client root.

        // To make this option easy and fast, just return true.

        return true;
    }



    private Map<String, FileGroup> sortByFileGroupId(final List<FileGroup> groups, Map<String, FileGroup> sorted) {
        if (sorted == null) {
            sorted = new HashMap<String, FileGroup>();
        }

        for (FileGroup group: groups) {
            sorted.put(group.getId(), group);
            sorted = sortByFileGroupId(group.getChildren(), sorted);
        }

        return sorted;
    }


    /**
     * Find the lowest client roots for each content root.  This is necessary, because each content root
     * might map to multiple clients.
     *
     * @param contentRoots input context roots
     * @param session session
     * @return clients mapped to roots
     */
    private Map<Client, List<FilePath>> findClientRoots(final FilePath[] contentRoots, final SyncUpdateSession session) {
        Map<Client, List<FilePath>> ret = new HashMap<Client, List<FilePath>>();

        Set<FilePath> discoveredRoots = new HashSet<FilePath>();

        final List<Client> clients = vcs.getClients();
        for (Client client: clients) {
            final List<FilePath> clientPaths = new ArrayList<FilePath>();
            final List<FilePath> clientRoots;
            try {
                clientRoots = client.getFilePathRoots();
            } catch (P4InvalidConfigException e) {
                session.exceptions.add(e);
                continue;
            }

            // Find the double mapping - if a content root is a child of the client root, then add the
            // content root.  If the client root is a child of the content root, then add the client root.
            for (FilePath clientRoot: clientRoots) {
                for (FilePath contentRoot : contentRoots) {
                    if (contentRoot.isUnder(clientRoot, false) && ! discoveredRoots.contains(contentRoot)) {
                        clientPaths.add(contentRoot);
                        discoveredRoots.add(contentRoot);
                    } else if (clientRoot.isUnder(contentRoot, false) && ! discoveredRoots.contains(clientRoot)) {
                        clientPaths.add(clientRoot);
                        discoveredRoots.add(clientRoot);
                    }
                }
            }

            // We could shrink the contents of the list - we don't want both a/b/c AND a/b in the list.
            // However, the p4 command will shrink it for us.

            if (! clientPaths.isEmpty()) {
                ret.put(client, clientPaths);
            }
        }

        return ret;
    }

    static class SyncUpdateSession implements UpdateSession {
        private boolean cancelled = false;
        private List<VcsException> exceptions = new ArrayList<VcsException>();

        @NotNull
        @Override
        public List<VcsException> getExceptions() {
            return exceptions;
        }

        @Override
        public void onRefreshFilesCompleted() {
            // TODO if any cache needs update, call it from here.
        }

        @Override
        public boolean isCanceled() {
            return cancelled;
        }
    }


}
