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

package net.groboclown.p4.server.api.commands.file;

import com.intellij.openapi.vcs.FilePath;
import net.groboclown.p4.server.api.P4CommandRunner;
import net.groboclown.p4.server.api.values.P4RemoteFile;
import org.jetbrains.annotations.NotNull;

public class AnnotateFileQuery implements P4CommandRunner.ServerQuery<AnnotateFileResult> {
    private final FilePath localFile;
    private final P4RemoteFile remoteFile;
    private final int rev;

    // Eventually, this might be needed.  For now, though, it's not.
    // private final String changelist;

    public AnnotateFileQuery(@NotNull FilePath localFile, int rev) {
        this.localFile = localFile;
        this.remoteFile = null;
        this.rev = rev;
    }

    public AnnotateFileQuery(@NotNull P4RemoteFile remoteFile, int rev) {
        this.localFile = null;
        this.remoteFile = remoteFile;
        this.rev = rev;
    }

    @NotNull
    @Override
    public Class<? extends AnnotateFileResult> getResultType() {
        return AnnotateFileResult.class;
    }

    @Override
    public P4CommandRunner.ServerQueryCmd getCmd() {
        return P4CommandRunner.ServerQueryCmd.ANNOTATE_FILE;
    }

    public FilePath getLocalFile() {
        return localFile;
    }

    public P4RemoteFile getRemoteFile() {
        return remoteFile;
    }

    public int getRev() {
        return rev;
    }
}
