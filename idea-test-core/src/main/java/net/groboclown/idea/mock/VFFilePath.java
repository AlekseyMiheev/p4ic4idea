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

package net.groboclown.idea.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;

/**
 * Simulation of a FilePath, for when the API causes an error with using the correct
 * API.  Mostly, this is used for lightweight unit tests that don't want to mock the
 * world.
 */
public class VFFilePath
        implements FilePath {
    private final VirtualFile vf;

    public VFFilePath(@NotNull final VirtualFile vf) {
        if (! vf.isInLocalFileSystem()) {
            throw new IllegalArgumentException("not local");
        }
        this.vf = vf;
    }

    @Nullable
    @Override
    public VirtualFile getVirtualFile() {
        return vf;
    }

    @Nullable
    @Override
    public VirtualFile getVirtualFileParent() {
        return vf.getParent();
    }

    @NotNull
    @Override
    public File getIOFile() {
        return new File(vf.getPath());
    }

    @NotNull
    @Override
    public String getName() {
        return vf.getName();
    }

    @NotNull
    @Override
    public String getPresentableUrl() {
        return getIOFile().toURI().toASCIIString();
    }

    @Deprecated
    @Nullable
    @Override
    public Document getDocument() {
        return null;
    }

    @NotNull
    @Override
    public Charset getCharset() {
        return Charset.defaultCharset();
    }

    @NotNull
    @Override
    public Charset getCharset(@Nullable final Project project) {
        return Charset.defaultCharset();
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return MockFileType.MOCK;
    }

    @Deprecated
    @Override
    public void refresh() {

    }

    @Deprecated
    @Override
    public void hardRefresh() {

    }

    @NotNull
    @Override
    public String getPath() {
        return vf.getPath();
    }

    @Override
    public boolean isDirectory() {
        return vf.isDirectory();
    }

    @Override
    public boolean isUnder(@NotNull final FilePath filePath, final boolean b) {
        // not implemented here
        return false;
    }

    @Nullable
    @Override
    public FilePath getParentPath() {
        return new VFFilePath(vf.getParent());
    }

    @Override
    public boolean isNonLocal() {
        return false;
    }

    @Override
    public int hashCode() {
        return vf.getPath().hashCode() + vf.getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FilePath && vf.equals(((FilePath) obj).getVirtualFile());
    }
}
