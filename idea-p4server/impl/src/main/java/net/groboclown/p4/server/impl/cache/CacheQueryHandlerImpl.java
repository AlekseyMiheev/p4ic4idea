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

package net.groboclown.p4.server.impl.cache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import net.groboclown.p4.server.api.P4ServerName;
import net.groboclown.p4.server.api.cache.CacheQueryHandler;
import net.groboclown.p4.server.api.commands.changelist.AddJobToChangelistAction;
import net.groboclown.p4.server.api.commands.changelist.CreateChangelistAction;
import net.groboclown.p4.server.api.commands.changelist.DeleteChangelistAction;
import net.groboclown.p4.server.api.commands.changelist.EditChangelistAction;
import net.groboclown.p4.server.api.commands.changelist.MoveFilesToChangelistAction;
import net.groboclown.p4.server.api.commands.file.AddEditAction;
import net.groboclown.p4.server.api.commands.file.DeleteFileAction;
import net.groboclown.p4.server.api.commands.file.MoveFileAction;
import net.groboclown.p4.server.api.commands.file.RevertFileAction;
import net.groboclown.p4.server.api.config.ClientConfig;
import net.groboclown.p4.server.api.values.P4ChangelistId;
import net.groboclown.p4.server.api.values.P4FileAction;
import net.groboclown.p4.server.api.values.P4JobSpec;
import net.groboclown.p4.server.api.values.P4LocalChangelist;
import net.groboclown.p4.server.api.values.P4LocalFile;
import net.groboclown.p4.server.api.values.P4RemoteChangelist;
import net.groboclown.p4.server.api.values.P4ResolveType;
import net.groboclown.p4.server.api.values.P4Revision;
import net.groboclown.p4.server.api.values.P4WorkspaceSummary;
import net.groboclown.p4.server.impl.cache.store.ProjectCacheStore;
import net.groboclown.p4.server.impl.cache.store.ServerQueryCacheStore;
import net.groboclown.p4.server.impl.values.P4ChangelistIdImpl;
import net.groboclown.p4.server.impl.values.P4LocalChangelistImpl;
import net.groboclown.p4.server.impl.values.P4LocalFileImpl;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unoptimized code.
 */
public class CacheQueryHandlerImpl implements CacheQueryHandler {
    private static final Logger LOG = Logger.getInstance(CacheQueryHandler.class);

    private final ProjectCacheStore cache;

    public CacheQueryHandlerImpl(@NotNull ProjectCacheStore cache) {
        this.cache = cache;
    }


    // FIXME these updates need to be aware of issues if several pending actions affect the same object.


    @NotNull
    @Override
    public Collection<P4LocalChangelist> getCachedOpenedChangelists(@NotNull ClientConfig config) {
        final Map<P4ChangelistId, P4LocalChangelistImpl.Builder> changelists = new HashMap<>();
        try {
            cache.read(config, (store) ->
                    store.getChangelists().forEach(
                            (cl) -> changelists
                                    .put(cl.getChangelistId(), new P4LocalChangelistImpl.Builder().withSrc(cl))));

            cache.readClientActions(config).forEach((action) -> {
                switch (action.getCmd()) {
                    case CREATE_CHANGELIST: {
                        CreateChangelistAction a = (CreateChangelistAction) action;
                        P4ChangelistId id = new P4ChangelistIdImpl(
                                cache.getPendingChangelistId(a), config.getClientServerRef());
                        P4LocalChangelistImpl.Builder builder = new P4LocalChangelistImpl.Builder();
                        builder.withChangelistId(id)
                                .withComment(a.getComment())
                                .withClientname(config.getClientname())
                                .withUsername(config.getServerConfig().getUsername());
                        changelists.put(id, builder);
                        break;
                    }
                    case DELETE_CHANGELIST: {
                        DeleteChangelistAction a = (DeleteChangelistAction) action;
                        // Remove the changelist entirely from the returned list.
                        changelists.remove(a.getChangelistId());
                        break;
                    }
                    case MOVE_FILES_TO_CHANGELIST: {
                        MoveFilesToChangelistAction a = (MoveFilesToChangelistAction) action;
                        for (P4LocalChangelistImpl.Builder builder : changelists.values()) {
                            if (builder.is(((MoveFilesToChangelistAction) action).getChangelistId())) {
                                builder.addFiles(a.getFiles());
                            } else {
                                builder.removeFiles(a.getFiles());
                            }
                        }
                        break;
                    }
                    case EDIT_CHANGELIST_DESCRIPTION: {
                        EditChangelistAction a = (EditChangelistAction) action;
                        P4LocalChangelistImpl.Builder builder = changelists.get(a.getChangelistId());
                        if (builder != null) {
                            builder.withComment(a.getComment());
                        }
                        break;
                    }
                    case ADD_JOB_TO_CHANGELIST: {
                        AddJobToChangelistAction a = (AddJobToChangelistAction) action;
                        P4LocalChangelistImpl.Builder builder = changelists.get(a.getChangelistId());
                        if (builder != null) {
                            builder.withJob(a.getJob());
                        }
                        break;
                    }
                    case REMOVE_JOB_FROM_CHANGELIST: {
                        // FIXME implement
                        LOG.warn("FIXME implement REMOVE_JOB_FROM_CHANGELIST condition");
                        throw new IllegalStateException("not implemented");
                    }
                }
            });
        } catch (InterruptedException e) {
            LOG.error("Spent too long waiting for a read cache; Something is spending too much time writing to the cache.", e);
        }

        return changelists.values().stream().map(P4LocalChangelistImpl.Builder::build).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public Collection<P4LocalFile> getCachedOpenedFiles(@NotNull ClientConfig config) {
        Map<FilePath, P4LocalFileImpl.Builder> files = new HashMap<>();
        try {
            cache.read(config, (store) -> store.getFiles()
                    .forEach((file) -> files.put(file.getFilePath(), new P4LocalFileImpl.Builder().withLocalFile(file))));

            // Because we read the pending actions in order of their behavior,
            // this will (should?) update each file with the correct status.

            cache.readClientActions(config).forEach((action) -> {
                switch (action.getCmd()) {
                    case MOVE_FILE: {
                        MoveFileAction a = (MoveFileAction) action;
                        P4LocalFileImpl.Builder srcBuilder = files.get(a.getSourceFile());
                        P4LocalFileImpl.Builder tgtBuilder = files.get(a.getTargetFile());
                        if (srcBuilder == null) {
                            srcBuilder = new P4LocalFileImpl.Builder()
                                    .withLocal(a.getSourceFile())
                                    .withHave(new P4Revision(-1));
                            files.put(a.getSourceFile(), srcBuilder);
                        }
                        srcBuilder
                                .withAction(P4FileAction.MOVE_DELETE)
                                .withResolveType(P4ResolveType.NO_RESOLVE);
                        if (tgtBuilder == null) {
                            tgtBuilder = new P4LocalFileImpl.Builder()
                                    .withLocal(a.getTargetFile())
                                    .withHave(new P4Revision(-1));
                            files.put(a.getTargetFile(), tgtBuilder);
                        }
                        tgtBuilder
                                .withAction(P4FileAction.MOVE_ADD_EDIT)
                                .withIntegrateFrom(srcBuilder.getDepot())
                                .withResolveType(P4ResolveType.NO_RESOLVE);
                        break;
                    }
                    case ADD_EDIT_FILE: {
                        AddEditAction a = (AddEditAction) action;
                        P4LocalFileImpl.Builder builder = files.get(a.getFile());
                        if (builder == null) {
                            builder = new P4LocalFileImpl.Builder()
                                    .withLocal(a.getFile());
                            files.put(a.getFile(), builder);
                        }
                        builder
                                .withAction(P4FileAction.ADD_EDIT)
                                .withResolveType(P4ResolveType.NO_RESOLVE);
                        break;
                    }
                    case DELETE_FILE: {
                        DeleteFileAction a = (DeleteFileAction) action;
                        P4LocalFileImpl.Builder builder = files.get(a.getFile());
                        if (builder == null) {
                            builder = new P4LocalFileImpl.Builder()
                                    .withLocal(a.getFile());
                            files.put(a.getFile(), builder);
                        }
                        builder
                                .withAction(P4FileAction.DELETE)
                                .withResolveType(P4ResolveType.NO_RESOLVE);
                        break;
                    }
                    case REVERT_FILE: {
                        RevertFileAction a = (RevertFileAction) action;
                        files.remove(a.getFile());
                        break;
                    }
                }
            });
        } catch (InterruptedException e) {
            LOG.error("Spent too long waiting for a read cache; Something is spending too much time writing to the cache.", e);
        }
        return files.values().stream().map(P4LocalFileImpl.Builder::build).collect(Collectors.toList());
    }

    @Nullable
    @Override
    public P4RemoteChangelist getCachedChangelist(P4ServerName serverName, P4ChangelistId changelistId) {
        // FIXME implement
        LOG.warn("FIXME implement getCachedChangelist");
        return null;
    }

    @Nullable
    @Override
    public P4JobSpec getCachedJobSpec(P4ServerName serverName) {
        try {
            return cache.read(serverName, null, ServerQueryCacheStore::getJobSpec);
        } catch (InterruptedException e) {
            LOG.error("Spent too long waiting for a read cache; Something is spending too much time writing to the cache.", e);
            return null;
        }
    }

    @Override
    public Collection<P4WorkspaceSummary> getCachedClientsForUser(@NotNull P4ServerName serverName,
            @NotNull String username) {
        // FIXME implement
        LOG.warn("FIXME implement getCachedClientsForUser");
        return null;
    }
}
