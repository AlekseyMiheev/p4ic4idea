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

package com.perforce.p4java.util.compat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Jdk7 java.nio.file.Files compatibility layer.
 */
public class NioFiles {

    public static class FileTime {
        private final long millis;

        public FileTime(long millis) {
            this.millis = millis;
        }

        public long toMillis() {
            return millis;
        }
    }

    public enum LinkOption {
        NOFOLLOW_LINKS
    }

    public enum StandardCopyOption {
        ATOMIC_MOVE,
        REPLACE_EXISTING
    }


    public static boolean isReadable(File f) {
        return f != null && f.exists() && !f.isDirectory() && f.canRead();
    }

    public static boolean isExecutable(File file) {
        return file != null && file.exists() && !file.isDirectory() && file.canExecute();
    }

    public static File setAttribute(File path, String attribute, Object value)
            throws IOException {
        if (path != null && "dos:readonly".equals(attribute) && value instanceof Boolean) {
            if (! path.setReadable(!(Boolean) value)) {
                throw new IOException("failed to make path read-only: " + path);
            }
        } else if (path != null) {
            throw new IllegalArgumentException("limited compatibility");
        }
        return path;
    }

    public static File get(String path) {
        if (path == null) {
            throw new NullPointerException();
        }
        return new File(path);
    }

    public static boolean isSymbolicLink(File filePath)
            throws IOException {
        if (filePath == null) {
            return false;
        }
        if (Jdk6SymbolicLinkHelper.isSymbolicLinkCapable()) {
            return Jdk6SymbolicLinkHelper.isSymbolicLink(filePath.getAbsolutePath());
        }
        File canon;
        if (filePath.getParent() == null) {
            canon = filePath;
        } else {
            File canonDir = filePath.getParentFile().getCanonicalFile();
            canon = new File(canonDir, filePath.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    public static FileTime getLastModifiedTime(File linkPath, LinkOption linkOption) {
        if (linkPath == null) {
            return null;
        }
        if (Jdk6SymbolicLinkHelper.isSymbolicLinkCapable()) {
            return new FileTime(Jdk6SymbolicLinkHelper.getLastModifiedTime(linkPath.getAbsolutePath()));
        }
        return new FileTime(linkPath.lastModified());
    }


    public static File readSymbolicLink(File linkPath)
            throws IOException {
        if (linkPath == null) {
            return null;
        }
        if (Jdk6SymbolicLinkHelper.isSymbolicLinkCapable()) {
            String path = Jdk6SymbolicLinkHelper.readSymbolicLink(linkPath.getAbsolutePath());
            return path == null ? null : new File(path);
        }
        return linkPath.getCanonicalFile();
    }

    public static boolean exists(File filePath) {
        return exists(filePath, null);
    }

    public static boolean exists(File filePath, LinkOption linkOption) {
        if (filePath == null) {
            throw new NullPointerException();
        }
        if (linkOption != LinkOption.NOFOLLOW_LINKS) {
            if (Jdk6SymbolicLinkHelper.isSymbolicLinkCapable()) {
                return Jdk6SymbolicLinkHelper.exists(filePath.getAbsolutePath());
            }
            try {
                if (isSymbolicLink(filePath)) {
                    File real = filePath.getCanonicalFile();
                    return real.exists();
                }
            } catch (IOException e) {
                // assume it doesn't exist.
                return false;
            }
        }
        return filePath.exists();
    }

    public static File createSymbolicLink(File linkPath, File targetPath) {
        if (Jdk6SymbolicLinkHelper.isSymbolicLinkCapable()) {
            String path = Jdk6SymbolicLinkHelper.createSymbolicLink(
                    linkPath.getAbsolutePath(), targetPath.getAbsolutePath());
            return path == null ? null : new File(path);
        }
        return null;
    }

    public static boolean notExists(File file) {
        return ! exists(file);
    }

    public static void createDirectories(String path)
            throws IOException {
        createDirectories(new File(path));
    }

    public static void createDirectories(File path)
            throws IOException {
        if (path != null) {
            if (! path.mkdirs()) {
                throw new IOException("Failed to create directories for " + path);
            }
        }
    }

    public static void deleteIfExists(File file)
            throws IOException {
        if (file != null && file.exists()) {
            if (! file.delete()) {
                throw new IOException("could not delete " + file);
            }
        }
    }

    public static void createFile(File file)
            throws IOException {
        if (file == null) {
            throw new NullPointerException();
        }
        if (file.isDirectory()) {
            throw new IOException("cannot create file; exists as directory: " + file);
        }
        if (file.exists()) {
            return;
        }
        if (! file.createNewFile()) {
            throw new IOException("failed to create file: " + file);
        }
    }

    public static void move(File srcFile, File targetFile, StandardCopyOption... copyOptions)
            throws IOException {
        // Totally unsafe move operation.  So many things can go wrong here.

        if (srcFile == null || targetFile == null) {
            throw new NullPointerException();
        }
        if (! srcFile.exists()) {
            throw new IOException("source file does not exist: " + srcFile);
        }

        boolean replaceExisting = false;
        for (StandardCopyOption copyOption : copyOptions) {
            if (copyOption == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
            }
        }
        if (replaceExisting && targetFile.exists()) {
            // Yup.  We're deleting the file, even if the copy can't happen.
            if (! targetFile.delete()) {
                throw new IOException("Cannot overwrite target file: " + targetFile);
            }
        }
        if (!replaceExisting && targetFile.exists()) {
            // Nothing to do.
            return;
        }

        // Try the easy way.
        if (!srcFile.renameTo(targetFile)) {
            // Have to do it the hard way.
            if (! targetFile.exists()) {
                if (! targetFile.createNewFile()) {
                    throw new IOException("Could not create target file: " + targetFile);
                }
            }

            FileChannel source = null;
            FileChannel destination = null;
            try {
                source = new FileInputStream(srcFile).getChannel();
                destination = new FileOutputStream(targetFile).getChannel();

                long count = 0;
                long size = source.size();
                while ((count += destination.transferFrom(source, count, size-count)) < size);
            }
            finally {
                if(source != null) {
                    source.close();
                }
                if(destination != null) {
                    destination.close();
                }
            }

            // Now remove the source file
            if (! srcFile.delete()) {
                throw new IOException("Could not delete source file after copy: " + srcFile);
            }
        }
    }
}
