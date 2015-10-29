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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import net.groboclown.idea.p4ic.P4Bundle;
import net.groboclown.idea.p4ic.config.ServerConfig;
import net.groboclown.idea.p4ic.server.VcsExceptionUtil;
import net.groboclown.idea.p4ic.server.exceptions.P4InvalidConfigException;
import net.groboclown.idea.p4ic.v2.server.cache.ClientServerId;
import net.groboclown.idea.p4ic.v2.server.cache.UpdateAction.UpdateParameterNames;
import net.groboclown.idea.p4ic.v2.server.cache.UpdateGroup;
import net.groboclown.idea.p4ic.v2.server.cache.state.PendingUpdateState;
import net.groboclown.idea.p4ic.v2.server.cache.sync.ClientCacheManager;
import net.groboclown.idea.p4ic.v2.server.connection.Synchronizer.ActionRunner;
import net.groboclown.idea.p4ic.v2.server.util.FilePathUtil;
import net.groboclown.idea.p4ic.v2.ui.alerts.ConfigurationProblemHandler;
import net.groboclown.idea.p4ic.v2.ui.alerts.DisconnectedHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The multi-threaded connections to the Perforce server for a specific client.
 */
public class ServerConnection {
    private static final Logger LOG = Logger.getInstance(ServerConnection.class);
    private static final ThreadGroup CONNECTION_THREAD_GROUP = new ThreadGroup("Server Connection");
    private static final ThreadLocal<Boolean> THREAD_EXECUTION_ACTIVE = new ThreadLocal<Boolean>();
    private final BlockingQueue<UpdateAction> pendingUpdates = new LinkedBlockingDeque<UpdateAction>();
    private final Queue<UpdateAction> redo = new ArrayDeque<UpdateAction>();
    private final Lock redoLock = new ReentrantLock();
    private final AlertManager alertManager;
    private final ClientCacheManager cacheManager;
    private final ServerConfig config;
    private final ServerStatusController statusController;
    private final String clientName;
    private final Object clientExecLock = new Object();
    private final Thread background;
    private final Synchronizer.ServerSynchronizer.ConnectionSynchronizer synchronizer;
    private volatile boolean disposed = false;
    private boolean loadedPendingUpdateStates = false;
    @Nullable
    private ClientExec clientExec;


    public static void assertInServerConnection() {
        ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
        if (currentGroup != CONNECTION_THREAD_GROUP && THREAD_EXECUTION_ACTIVE.get() != Boolean.TRUE) {
            throw new IllegalStateException("Activity can only be run from within the ServerConnection action thread");
        }
    }


    public interface CreateUpdate {
        @NotNull
        Collection<PendingUpdateState> create(@NotNull ClientCacheManager mgr);
    }


    public interface CacheQuery<T> {
        T query(@NotNull ClientCacheManager mgr) throws InterruptedException;
    }



    public ServerConnection(@NotNull final AlertManager alertManager,
            @NotNull ClientServerId clientServerId, @NotNull ClientCacheManager cacheManager,
            @NotNull ServerConfig config, @NotNull ServerStatusController statusController,
            @NotNull Synchronizer.ServerSynchronizer.ConnectionSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
        this.alertManager = alertManager;
        this.cacheManager = cacheManager;
        this.config = config;
        this.statusController = statusController;
        this.clientName = clientServerId.getClientId();

        background = new Thread(new QueueRunner());
        background.setDaemon(false);
        background.setPriority(Thread.NORM_PRIORITY - 1);
        background.start();
    }


    public synchronized void postSetup(@NotNull Project project) {
        // First, check if the server is reachable.
        // This is necessary to keep the pending changes from being gobbled
        // up if we have a mistaken online mode set.  If we know we're
        // working offline, then don't check if we're online (especially since
        // the user can manually switch to offline mode).
        if (isWorkingOnline()) {
            ClientExec.checkIfOnline(project, config, statusController);
        }

        // Push all the cached pending updates into the queue for future
        // processing.
        if (! loadedPendingUpdateStates) {
            queueUpdateActions(project, cacheManager.getCachedPendingUpdates());
            loadedPendingUpdateStates = true;
        }
    }


    public void dispose() {
        disposed = true;
        background.interrupt();
        synchronized (clientExecLock) {
            if (clientExec != null) {
                clientExec.dispose();
                clientExec = null;
            }
        }
    }



    public void queueAction(@NotNull Project project, @NotNull ServerUpdateAction action) {
        LOG.info("Queueing action for execution: " + action, new Throwable("stack capture"));
        pendingUpdates.add(new UpdateAction(project, action));
    }

    /**
     * Run the command within the current thread.  This will still block if another action
     * is happening within the other thread.
     *
     * @param action action to run
     */
    public void runImmediately(@NotNull final Project project, @NotNull final ServerUpdateAction action)
            throws InterruptedException {
        synchronizer.runImmediateAction(new ActionRunner<Void>() {
            @Override
            public Void perform() throws InterruptedException {
                try {
                    THREAD_EXECUTION_ACTIVE.set(Boolean.TRUE);
                    action.perform(getExec(project), cacheManager, ServerConnection.this, alertManager);
                } catch (P4InvalidConfigException e) {
                    alertManager.addCriticalError(new ConfigurationProblemHandler(project, statusController, e), e);
                } finally {
                    THREAD_EXECUTION_ACTIVE.remove();
                }
                return null;
            }
        });
    }

    @Nullable
    public <T> T query(@NotNull final Project project, @NotNull final ServerQuery<T> query) throws InterruptedException {
        return synchronizer.runImmediateAction(new ActionRunner<T>() {
            @Override
            public T perform() throws InterruptedException {
                try {
                    THREAD_EXECUTION_ACTIVE.set(Boolean.TRUE);
                    return query.query(getExec(project), cacheManager, ServerConnection.this, alertManager);
                } catch (P4InvalidConfigException e) {
                    alertManager.addCriticalError(new ConfigurationProblemHandler(project, statusController, e), e);
                    return null;
                } finally {
                    THREAD_EXECUTION_ACTIVE.remove();
                }
            }
        });
    }


    /**
     * Retry running a command that failed.  This should usually be put back at the head
     * of the action queue.  It is sometimes necessary if the command fails due to a
     * login or config issue.
     *
     * @param action action
     */
    public void requeueAction(@NotNull Project project, @NotNull ServerUpdateAction action) {
        pushAbortedAction(new UpdateAction(project, action));
    }


    public boolean isWorkingOnline() {
        return statusController.isWorkingOnline();
    }


    public boolean isWorkingOffline() {
        return statusController.isWorkingOffline();
    }


    public void workOffline() {
        statusController.disconnect();
    }


    public void workOnline() {
        statusController.connect();
    }

    public ServerConnectedController getServerConnectedController() {
        return statusController;
    }




    public void queueUpdates(@NotNull Project project, @NotNull CreateUpdate update) {
        final Collection<PendingUpdateState> updates = update.create(cacheManager);
        List<PendingUpdateState> nonNullUpdates = new ArrayList<PendingUpdateState>(updates.size());
        for (PendingUpdateState updateState : updates) {
            if (updateState != null) {
                cacheManager.addPendingUpdateState(updateState);
                nonNullUpdates.add(updateState);
            }
        }
        queueUpdateActions(project, nonNullUpdates);
    }


    public <T> T cacheQuery(@NotNull CacheQuery<T> q) throws InterruptedException {
        return q.query(cacheManager);
    }


    P4Exec2 getExec(@NotNull Project project) throws P4InvalidConfigException {
        if (disposed) {
            throw new IllegalStateException("connection disposed");
        }
        // double-check locking.  This is why clientExec must be volatile.
        synchronized (clientExecLock) {
            if (clientExec == null) {
                clientExec = new ClientExec(config, statusController, clientName);
            }
            return new P4Exec2(project, clientExec);
        }
    }


    private void queueUpdateActions(@NotNull Project project, @NotNull Collection<PendingUpdateState> updates) {
        UpdateGroup currentGroup = null;
        List<PendingUpdateState> currentGroupUpdates = null;
        for (PendingUpdateState update : updates) {
            // FIXME debug
            LOG.info("adding update state as action: " + update);

            if (currentGroup != null && !update.getUpdateGroup().equals(currentGroup)) {
                // new group, so add the old stuff and clear it out.
                if (!currentGroupUpdates.isEmpty()) {
                    queueAction(project,
                            currentGroup.getServerUpdateActionFactory().create(currentGroupUpdates));
                }
                currentGroupUpdates = null;
            }
            currentGroup = update.getUpdateGroup();
            if (currentGroupUpdates == null) {
                currentGroupUpdates = new ArrayList<PendingUpdateState>();
            }
            currentGroupUpdates.add(update);
        }
        if (currentGroup != null && currentGroupUpdates != null && !currentGroupUpdates.isEmpty()) {
            queueAction(project,
                    currentGroup.getServerUpdateActionFactory().create(currentGroupUpdates));
        }
    }


    void goOffline() {
        synchronized (clientExecLock) {
            if (clientExec != null) {
                clientExec.dispose();
                clientExec = null;
            }
        }
    }


    private UpdateAction pullNextAction() throws InterruptedException {
        UpdateAction action;
        redoLock.lock();
        try {
            action = redo.poll();
        } finally {
            redoLock.unlock();
        }
        if (action == null) {
            // FIXME debug
            LOG.info("Polling pending updates for action");
            action = pendingUpdates.take();
        }
        // FIXME debug
        LOG.info("pulled action " + action + "; pending size " + pendingUpdates.size() + "; redo size " + redo.size());
        return action;
    }


    private void pushAbortedAction(@NotNull final UpdateAction updateAction) {
        redoLock.lock();
        try {
            redo.add(updateAction);
        } finally {
            redoLock.unlock();
        }
    }


    class QueueRunner implements Runnable {
        @Override
        public void run() {
            while (!disposed) {
                // Wait for something to do first
                final UpdateAction action;
                try {
                    action = pullNextAction();
                } catch (InterruptedException e) {
                    // this is fine.
                    LOG.info(e);
                    continue;
                }

                try {
                    boolean didRun = synchronizer.runBackgroundAction(new ActionRunner<Void>() {
                        @Override
                        public Void perform() throws InterruptedException {
                            LOG.info("Running action " + action);
                            final P4Exec2 exec;
                            try {
                                exec = getExec(action.project);
                                // Perform a second connection attempt, just to be sure.
                                exec.getServerInfo();
                            } catch (P4InvalidConfigException e) {
                                alertManager.addCriticalError(new ConfigurationProblemHandler(action.project,
                                        statusController, e), e);
                                // do not requeue the action
                                cacheManager.removePendingUpdateStates(action.action.getPendingUpdateStates());
                                action.action.abort(cacheManager);
                                return null;
                            } catch (VcsException e) {
                                // FIXME need a more nuanced handler for general connection problems.
                                alertManager.addCriticalError(new DisconnectedHandler(action.project,
                                        statusController, e), e);
                                // go offline and requeue the action
                                getServerConnectedController().disconnect();
                                pushAbortedAction(action);
                                return null;
                            }
                            action.action.perform(exec,
                                        cacheManager, ServerConnection.this, alertManager);
                                // only remove the state once we've successfully
                                // processed the action.
                                cacheManager.removePendingUpdateStates(action.action.getPendingUpdateStates());
                            return null;
                        }
                    });
                    if (!didRun) {
                        // Had to wait for the action to run, so requeue it and try again.
                        pushAbortedAction(action);
                    }
                } catch (InterruptedException e) {
                    // Requeue the action, because it is still in the
                    // cached pending update states.
                    LOG.info(e);
                    pushAbortedAction(action);
                } catch (Throwable e) {
                    // Ensure exceptions that we should never trap are handled right.
                    VcsExceptionUtil.alwaysThrown(e);

                    // Big time error, so remove the update
                    cacheManager.removePendingUpdateStates(action.action.getPendingUpdateStates());
                    alertManager.addWarning(action.project,
                            P4Bundle.message("error.update-state"),
                            action.action.toString(),
                            e, getFilesFor(action.action.getPendingUpdateStates()));

                    // do not requeue action, because we removed it
                    // from the cached update list.
                    LOG.error(e);
                }
            }
        }
    }

    @Nullable
    private static FilePath[] getFilesFor(final Collection<PendingUpdateState> pendingUpdateStates) {
        List<FilePath> ret = new ArrayList<FilePath>(pendingUpdateStates.size());
        for (PendingUpdateState state : pendingUpdateStates) {
            Object file = state.getParameters().get(UpdateParameterNames.FILE.getKeyName());
            if (file != null && file instanceof String) {
                ret.add(FilePathUtil.getFilePath(file.toString()));
            } else {
                file = state.getParameters().get(UpdateParameterNames.FILE_SOURCE.getKeyName());
                if (file != null && file instanceof String) {
                    ret.add(FilePathUtil.getFilePath(file.toString()));
                }
            }
        }
        return ret.toArray(new FilePath[ret.size()]);
    }


    static class UpdateAction {
        final ServerUpdateAction action;
        final Project project;

        UpdateAction(@NotNull Project project, @NotNull ServerUpdateAction action) {
            this.action = action;
            this.project = project;
        }

        @Override
        public String toString() {
            return "Action " + action;
        }
    }
}
