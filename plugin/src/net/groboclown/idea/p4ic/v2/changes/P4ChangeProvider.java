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
package net.groboclown.idea.p4ic.v2.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import net.groboclown.idea.p4ic.P4Bundle;
import net.groboclown.idea.p4ic.changes.ChangeListBuilderCache;
import net.groboclown.idea.p4ic.extension.P4Vcs;
import net.groboclown.idea.p4ic.v2.server.connection.AlertManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/**
 * Pushes changes FROM Perforce INTO idea.  No Perforce jobs will be altered.
 * <p/>
 * If there was an IDEA changelist that referenced a Perforce changelist that
 * has since been submitted or deleted, the IDEA changelist will be removed,
 * and any contents will be moved into the default changelist.
 * <p/>
 * If there is a Perforce changelist that has no mapping to IDEA, an IDEA
 * change list is created.
 */
public class P4ChangeProvider implements ChangeProvider {
    private static final Logger LOG = Logger.getInstance(P4ChangeProvider.class);

    // TODO make configurable
    private static final long CHANGELIST_CACHE_EXPIRES_SECONDS = 10L;

    private final Project project;
    private final P4Vcs vcs;
    private final ChangeListSync changeListSync;

    private long lastRefreshTime = 0L;
    private long lastRefreshRequest = 0L;
    private ChangeListBuilderCache.CachedChanges cachedChanges;

    public P4ChangeProvider(@NotNull P4Vcs vcs) {
        this.project = vcs.getProject();
        this.vcs = vcs;
        this.changeListSync = new ChangeListSync(vcs, AlertManager.getInstance());
    }

    @Override
    public void getChanges(VcsDirtyScope dirtyScope, ChangelistBuilder builder, ProgressIndicator progress,
            ChangeListManagerGate addGate) throws VcsException {
        lastRefreshRequest = System.currentTimeMillis();
        if (project.isDisposed()) {
            return;
        }

        // This check is kind of necessary.  It's ensuring that IntelliJ is doing the right thing
        if (dirtyScope.getVcs() != vcs) {
            throw new VcsException(P4Bundle.message("error.vcs.dirty-scope.wrong"));
        }

        // How this is called by IntelliJ:
        // IntelliJ calls this method on updates to the files or changes.  If this method ends up
        // changing the list of changes, it seems like IntelliJ will call it a second time to make
        // sure that everything is still fine.

        // Because of both the frequency of invocations (essentially on every save), and the double
        // calls, we need to cache the long-running P4 changelist calls.

        // It also does not expect files that are not in the dirty scope to
        // be updated.  If they are, this method is called again.  This can cause
        // infinite reloading of the changelists, which is really really annoying.

        final Set<FilePath> dirtyFiles = dirtyScope.getDirtyFiles();
        if (dirtyFiles == null || dirtyFiles.isEmpty()) {
            LOG.info("No dirty files: nothing to do.");
            return;
        }


        if (cachedChanges != null) {
            // Check for cache expiration
            // FIXME DEBUG
            LOG.info("Has cache; checking if timeout occurred");
            if (lastRefreshRequest - lastRefreshTime <
                    TimeUnit.SECONDS.toMillis(CHANGELIST_CACHE_EXPIRES_SECONDS)) {

                // See if the cached changes are still valid
                if (! cachedChanges.hasChanged(dirtyFiles, addGate)) {
                    LOG.info("Loading changelists through the cache");
                    cachedChanges.applyCache(builder);
                    return;
                }
            } else {
                LOG.info("Reloading the changelists due to cache expiration");
            }

        } else {
            LOG.info("Creating changelist cache");
        }

        // Else, reload the cache

        lastRefreshTime = lastRefreshRequest;

        // null out the cache first, in case of exception.
        cachedChanges = null;
        ChangeListBuilderCache cacheBuilder = new ChangeListBuilderCache(project, builder, dirtyFiles);
        try {
            changeListSync.syncChanges(dirtyFiles, cacheBuilder, addGate, progress);
        } catch (VcsException e) {
            LOG.warn("sync changes caused error", e);
            throw e;
        } catch (InterruptedException e) {
            // TODO see if this is the right handling mechanism

            CancellationException ex = new CancellationException();
            ex.initCause(e);
            throw ex;
        }
        cachedChanges = cacheBuilder.getCache();
    }


    @Override
    public boolean isModifiedDocumentTrackingRequired() {
        // editing a file requires opening the file for edit or add, and thus changing its dirty state.
        return true;
    }

    @Override
    public void doCleanup(List<VirtualFile> files) {
        // clean up the working copy.
        // Nothing to do?
        System.out.println("Cleanup called for  " + files);
    }
}