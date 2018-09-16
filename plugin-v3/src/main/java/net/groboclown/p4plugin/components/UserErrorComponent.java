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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.perforce.p4java.exception.AuthenticationFailedException;
import com.perforce.p4java.exception.ClientError;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.FileSaveException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.ResourceException;
import com.perforce.p4java.exception.SslException;
import com.perforce.p4java.exception.SslHandshakeException;
import com.perforce.p4java.exception.TrustException;
import com.perforce.p4java.exception.ZeroconfException;
import net.groboclown.p4.server.api.messagebus.CancellationMessage;
import net.groboclown.p4.server.api.messagebus.ConnectionErrorMessage;
import net.groboclown.p4.server.api.messagebus.ErrorEvent;
import net.groboclown.p4.server.api.messagebus.FileErrorMessage;
import net.groboclown.p4.server.api.messagebus.InternalErrorMessage;
import net.groboclown.p4.server.api.messagebus.LoginFailureMessage;
import net.groboclown.p4.server.api.messagebus.MessageBusClient;
import net.groboclown.p4.server.api.messagebus.P4ServerErrorMessage;
import net.groboclown.p4.server.api.messagebus.P4WarningMessage;
import net.groboclown.p4.server.api.messagebus.ReconnectRequestMessage;
import net.groboclown.p4.server.api.messagebus.ServerConnectedMessage;
import net.groboclown.p4.server.api.messagebus.ServerErrorEvent;
import net.groboclown.p4.server.api.messagebus.UserSelectedOfflineMessage;
import net.groboclown.p4plugin.messages.UserMessage;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.CancellationException;

/**
 * Reports errors to the user
 */
public class UserErrorComponent implements ProjectComponent {
    private static final Logger LOG = Logger.getInstance(UserErrorComponent.class);
    private static final String COMPONENT_NAME = "p4ic4idea:User Error Component";

    private final Project project;

    public UserErrorComponent(Project project) {
        this.project = project;
    }

    @Override
    public void projectOpened() {
        // skip
    }

    @Override
    public void projectClosed() {

    }

    // FIXME these are placeholder messages.

    @SuppressWarnings("Convert2Lambda")
    @Override
    public void initComponent() {
        MessageBusClient.ApplicationClient appClient = MessageBusClient.forApplication(project);
        MessageBusClient.ProjectClient projClient = MessageBusClient.forProject(project, project);
        CancellationMessage.addListener(projClient, this, new CancellationMessage.Listener() {
            @Override
            public void cancelled(@NotNull ErrorEvent<CancellationException> e) {
                simpleInfo("Cancelled operation", "Operation Cancelled");
                LOG.info(e.getError());
            }
        });
        ConnectionErrorMessage.addListener(appClient, this, new ConnectionErrorMessage.Listener() {
            @Override
            public void unknownServer(@NotNull ServerErrorEvent.ServerNameErrorEvent<Exception> event) {
                simpleError("Unknown server " + event.getName(), "Could Not Connect to Server");
                LOG.warn(event.getError());
            }

            @Override
            public void couldNotWrite(@NotNull ServerErrorEvent.ServerConfigErrorEvent<FileSaveException> event) {
                simpleError("Could not write " + event.getConfig().getServerName(), "Could Not Write to Server");
                LOG.warn(event.getError());
            }

            @Override
            public void zeroconfProblem(@NotNull ServerErrorEvent.ServerNameErrorEvent<ZeroconfException> event) {
                simpleError("zeroconf problem for " + event.getName(), "Zeroconf Problem");
                LOG.warn(event.getError());
            }

            @Override
            public void sslHostTrustNotEstablished(@NotNull ServerErrorEvent.ServerConfigProblemEvent event) {
                simpleError("SSL Host trust not established to " + event.getName(),
                        "SSL Host Trust Issue");
            }

            @Override
            public void sslHostFingerprintMismatch(@NotNull ServerErrorEvent.ServerConfigErrorEvent<TrustException> event) {
                simpleError("SSL host fingerprint did not match known value for " + event.getName(),
                        "SSL Host Fingerprint Mismatch");
                LOG.warn(event.getError());
            }

            @Override
            public void sslAlgorithmNotSupported(@NotNull ServerErrorEvent.ServerNameProblemEvent event) {
                simpleError("Did you install the extended cryptography package?", "SSL Algorithm Not Supported");
            }

            @Override
            public void sslPeerUnverified(@NotNull ServerErrorEvent.ServerNameErrorEvent<SslHandshakeException> event) {
                simpleError("SSL peer unverified for " + event.getName(), "SSL Error");
                LOG.warn(event.getError());
            }

            @Override
            public void sslCertificateIssue(@NotNull ServerErrorEvent.ServerNameErrorEvent<SslException> event) {
                simpleError("SSL certificate error for " + event.getName(), "SSL Certificate Error");
                LOG.warn(event.getError());
            }

            @Override
            public void connectionError(@NotNull ServerErrorEvent.ServerNameErrorEvent<ConnectionException> event) {
                simpleError("Connection to Perforce server " + event.getName() + " failed: " +
                                event.getError().getMessage(),
                        "Perforce Connection Error");
                LOG.warn(event.getError());
            }

            @Override
            public void resourcesUnavailable(@NotNull ServerErrorEvent.ServerNameErrorEvent<ResourceException> event) {
                simpleError("Resources were unavailable for " + event.getName(), "Perforce Resources Unavailable");
                LOG.warn(event.getError());
            }
        });
        FileErrorMessage.addListener(projClient, this, new FileErrorMessage.Listener() {
            @Override
            public void fileReceiveError(@NotNull ServerErrorEvent.ServerNameErrorEvent<Exception> e) {
                simpleError("Failed to receive files from " + e.getName(), "Perforce File Receive Error");
                LOG.warn(e.getError());
            }

            @Override
            public void fileSendError(@NotNull ServerErrorEvent.ServerNameErrorEvent<Exception> e) {
                simpleError("Failed to send a file to server " + e.getName(), "Perforce File Send Error");
                LOG.warn(e.getError());
            }

            @Override
            public void localFileError(@NotNull ServerErrorEvent.ServerNameErrorEvent<IOException> e) {
                simpleError("Problem with local file for " + e.getName(), "Local File Issue");
                LOG.warn(e.getError());
            }
        });
        InternalErrorMessage.addListener(projClient, this, new InternalErrorMessage.Listener() {
            @Override
            public void internalError(@NotNull ErrorEvent<Throwable> t) {
                simpleError("Internal error: " + t.getMessage(), "P4 Plugin Error");
                LOG.warn(t.getError());
            }

            @Override
            public void p4ApiInternalError(@NotNull ErrorEvent<Throwable> t) {
                simpleError("Internal error: " + t.getMessage(), "P4 Plugin Error");
                LOG.warn(t.getError());
            }

            @Override
            public void unexpectedError(@NotNull ErrorEvent<Throwable> t) {
                simpleError("Internal error: " + t.getMessage(), "P4 Plugin Error");
                LOG.warn(t.getError());
            }
        });
        LoginFailureMessage.addListener(appClient, this, new LoginFailureMessage.Listener() {
            @Override
            public void singleSignOnFailed(@NotNull ServerErrorEvent.ServerConfigErrorEvent<AuthenticationFailedException> e) {
                simpleError("Single sign on failed for " + e.getName(), "Login Failure");
                LOG.warn(e.getError());
            }

            @Override
            public void singleSignOnExecutionFailed(@NotNull LoginFailureMessage.SingleSignOnExecutionFailureEvent e) {
                simpleError("Single sign on execution failed for " + e.getConfig().getServerName(),
                        "Login Failure");
                LOG.warn("SSO error for cmd: " + e.getCmd());
                LOG.warn("Stdout: " + e.getStdout());
                LOG.warn("StdErr: " + e.getStderr());
            }

            @Override
            public void sessionExpired(@NotNull ServerErrorEvent.ServerConfigErrorEvent<AuthenticationFailedException> e) {
                simpleError("Session expired for " + e.getName(), "Login Failure");
                LOG.warn(e.getError());
            }

            @Override
            public void passwordInvalid(@NotNull ServerErrorEvent.ServerConfigErrorEvent<AuthenticationFailedException> e) {
                simpleError("Password invalid for " + e.getName(), "Login Failure");
                LOG.warn(e.getError());
            }

            @Override
            public void passwordUnnecessary(@NotNull ServerErrorEvent.ServerConfigErrorEvent<AuthenticationFailedException> e) {
                simpleError("Password unnecessary for " + e.getName(), "Login Failure");
                LOG.warn(e.getError());
            }
        });
        P4ServerErrorMessage.addListener(projClient, this, new P4ServerErrorMessage.Listener() {
            @Override
            public void requestCausedError(@NotNull ServerErrorEvent.ServerMessageEvent e) {
                simpleError("Request error: " + e.getMsg().getAllMessages(), "Perforce Server Error");
                LOG.warn(e.getError());
            }

            @Override
            public void requestCausedWarning(@NotNull ServerErrorEvent.ServerMessageEvent e) {
                simpleWarning(e.getMsg().getAllMessages().toString(), "Perforce Server Warning");
                LOG.warn(e.getError());
            }

            @Override
            public void requestCausedInfoMsg(@NotNull ServerErrorEvent.ServerMessageEvent e) {
                simpleInfo(e.getMsg().getAllMessages().toString(), "Perforce Server Information");
                LOG.warn(e.getError());
            }

            @Override
            public void requestException(@NotNull ServerErrorEvent.ServerMessageEvent e) {
                simpleError("Request error: " + e.getError().getMessage(), "Perforce Server Error");
                LOG.warn(e.getError());
            }

            @Override
            public void requestException(@NotNull ServerErrorEvent.ServerNameErrorEvent<P4JavaException> e) {
                simpleError("Request error: " + e.getError().getMessage(), "Perforce Server Error");
                LOG.warn(e.getError());
            }
        });
        P4WarningMessage.addListener(projClient, this, new P4WarningMessage.Listener() {
            @Override
            public void disconnectCausedError(@NotNull ErrorEvent<Exception> e) {
                simpleWarning("Disconnection from server caused problem: " + e.getMessage(),
                        "Disconnected from Perforce Server Error");
                LOG.warn(e.getError());
            }

            @Override
            public void charsetTranslationError(@NotNull ErrorEvent<ClientError> e) {
                simpleWarning("Problem : " + e.getMessage(),
                        "Disconnected from Perforce Server Error");
                LOG.warn(e.getError());
            }
        });
        ServerConnectedMessage.addListener(appClient, this, new ServerConnectedMessage.Listener() {
            @Override
            public void serverConnected(@NotNull ServerConnectedMessage.ServerConnectedEvent e) {
                // This can be spammy.
                //simpleInfo("Connected to " + serverConfig.getServerName() +
                //                (loggedIn ? " and logged in." : ", but not logged in."),
                //        "Perforce Server Connected");
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Connected to " + e.getServerConfig().getServerName() + ".  Logged in? " + e.isLoggedIn());
                }
            }
        });
        UserSelectedOfflineMessage.addListener(projClient, this, new UserSelectedOfflineMessage.Listener() {
            @Override
            public void userSelectedServerOffline(@NotNull UserSelectedOfflineMessage.OfflineEvent e) {
                simpleInfo("You selected to go offline for " + e.getName(),
                        "Perforce Server Disconnect");
            }
        });
        ReconnectRequestMessage.addListener(projClient, this, new ReconnectRequestMessage.Listener() {
            @Override
            public void reconnectToAllClients(@NotNull ReconnectRequestMessage.ReconnectAllEvent e) {
                simpleInfo("Requested to go online for all connections.",
                        "Perforce Server Connect");
            }

            @Override
            public void reconnectToClient(@NotNull ReconnectRequestMessage.ReconnectEvent e) {
                simpleInfo("Requested to go online for " + e.getRef()  + ".",
                        "Perforce Server Connect");
            }
        });
    }

    @Override
    public void disposeComponent() {
        // TODO Perform listener dispose?
    }

    @NotNull
    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }

    private void simpleError(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
            @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
        simpleMessage(message, UserMessage.ERROR, title, NotificationType.ERROR);
    }

    private void simpleWarning(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
            @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
        simpleMessage(message, UserMessage.WARNING, title, NotificationType.WARNING);
    }

    private void simpleInfo(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
            @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
        simpleMessage(message, UserMessage.INFO, title, NotificationType.INFORMATION);
    }

    private void simpleMessage(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message, int level,
            @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
            @NotNull NotificationType icon) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Reporting [" + message + "]", new Exception());
        }
        UserMessage.showNotification(project, level, message, title, icon);
    }
}
