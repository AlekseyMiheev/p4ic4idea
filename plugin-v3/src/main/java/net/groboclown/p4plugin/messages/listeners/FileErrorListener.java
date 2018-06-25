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

package net.groboclown.p4plugin.messages.listeners;

import net.groboclown.p4.server.api.P4ServerName;
import net.groboclown.p4.server.api.config.ServerConfig;
import net.groboclown.p4.server.api.messagebus.FileErrorMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

// FIXME implement handlers
public class FileErrorListener implements FileErrorMessage.Listener {
    @Override
    public void fileReceiveError(@NotNull P4ServerName serverName, @Nullable ServerConfig serverConfig,
            @NotNull Exception e) {

    }

    @Override
    public void fileSendError(@NotNull P4ServerName serverName, @Nullable ServerConfig serverConfig,
            @NotNull Exception e) {

    }

    @Override
    public void localFileError(@NotNull P4ServerName serverName, @Nullable ServerConfig serverConfig,
            @NotNull IOException e) {

    }
}
