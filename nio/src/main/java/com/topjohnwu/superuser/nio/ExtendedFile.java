/*
 * Copyright 2023 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.topjohnwu.superuser.nio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * {@link File} API with extended features.
 * <p>
 * The goal of this class is to extend missing features in the {@link File} API that are available
 * in the NIO package but not possible to be re-implemented without low-level file system access.
 * For instance, detecting file types other than regular files and directories, handling and
 * creating hard links and symbolic links.
 * <p>
 * Another goal of this class is to provide a generalized API interface for custom file system
 * backends. The library includes backends for accessing files locally, accessing files remotely
 * via IPC, and accessing files through shell commands (by using {@code SuFile}, included in the
 * {@code io} module). The developer can get instances of this class with
 * {@link FileSystemManager#getFile}.
 * <p>
 * Implementations of this class is required to return the same type of {@link ExtendedFile} in
 * all of its APIs returning {@link File}s. This means that, for example, if the developer is
 * getting a list of files in a directory using a remote file system with {@link #listFiles()},
 * all files returned in the array will also be using the same remote file system backend.
 */
public abstract class ExtendedFile extends File {

    /**
     * @see File#File(String)
     */
    protected ExtendedFile(@NonNull String pathname) {
        super(pathname);
    }

    /**
     * @see File#File(String, String)
     */
    protected ExtendedFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * @see File#File(File, String)
     */
    protected ExtendedFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * @see File#File(URI)
     */
    protected ExtendedFile(@NonNull URI uri) {
        super(uri);
    }

    /**
     * @return true if the abstract pathname denotes a block device.
     */
    public abstract boolean isBlock();

    /**
     * @return true if the abstract pathname denotes a character device.
     */
    public abstract boolean isCharacter();

    /**
     * @return true if the abstract pathname denotes a symbolic link.
     */
    public abstract boolean isSymlink();

    /**
     * @return true if the abstract pathname denotes a named pipe (FIFO).
     */
    public abstract boolean isNamedPipe();

    /**
     * @return true if the abstract pathname denotes a socket file.
     */
    public abstract boolean isSocket();

    /**
     * Creates a new hard link named by this abstract pathname of an existing file
     * if and only if a file with this name does not yet exist.
     * @param existing a path to an existing file.
     * @return <code>true</code> if the named file does not exist and was successfully
     *         created; <code>false</code> if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    public abstract boolean createNewLink(String existing) throws IOException;

    /**
     * Creates a new symbolic link named by this abstract pathname to a target file
     * if and only if a file with this name does not yet exist.
     * @param target the target of the symbolic link.
     * @return <code>true</code> if the named file does not exist and was successfully
     *         created; <code>false</code> if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    public abstract boolean createNewSymlink(String target) throws IOException;

    /**
     * Opens an InputStream with the matching file system backend of the file.
     * @see FileInputStream#FileInputStream(File)
     */
    @NonNull
    public abstract InputStream newInputStream() throws IOException;

    /**
     * Opens an OutputStream with the matching file system backend of the file.
     * @see FileOutputStream#FileOutputStream(File)
     */
    @NonNull
    public final OutputStream newOutputStream() throws IOException {
        return newOutputStream(false);
    }

    /**
     * Opens an OutputStream with the matching file system backend of the file.
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    @NonNull
    public abstract OutputStream newOutputStream(boolean append) throws IOException;

    /**
     * Create a child relative to the abstract pathname using the same file system backend.
     * @see File#File(File, String)
     */
    @NonNull
    public abstract ExtendedFile getChildFile(String child);

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public abstract ExtendedFile getAbsoluteFile();

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public abstract ExtendedFile getCanonicalFile() throws IOException;

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile getParentFile();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles(@Nullable FilenameFilter filter);

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles(@Nullable FileFilter filter);
}
