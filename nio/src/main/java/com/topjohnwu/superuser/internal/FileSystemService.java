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

package com.topjohnwu.superuser.internal;

import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_NONBLOCK;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class FileSystemService extends IFileSystemService.Stub {

    static final int PIPE_CAPACITY = 16 * 4096;

    private final LruCache<String, File> mCache = new LruCache<String, File>(100) {
        @Override
        protected File create(String key) {
            return new File(key);
        }
    };

    @Override
    public IOResult getCanonicalPath(String path) {
        try {
            return new IOResult(mCache.get(path).getCanonicalPath());
        } catch (IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        return mCache.get(path).isDirectory();
    }

    @Override
    public boolean isFile(String path) {
        return mCache.get(path).isFile();
    }

    @Override
    public boolean isHidden(String path) {
        return mCache.get(path).isHidden();
    }

    @Override
    public long lastModified(String path) {
        return mCache.get(path).lastModified();
    }

    @Override
    public long length(String path) {
        return mCache.get(path).length();
    }

    @Override
    public IOResult createNewFile(String path) {
        try {
            return new IOResult(mCache.get(path).createNewFile());
        } catch (IOException e) {
            return new IOResult(e);
        }
    }

    @Override
    public boolean delete(String path) {
        return mCache.get(path).delete();
    }

    @Override
    public String[] list(String path) {
        return mCache.get(path).list();
    }

    @Override
    public boolean mkdir(String path) {
        return mCache.get(path).mkdir();
    }

    @Override
    public boolean mkdirs(String path) {
        return mCache.get(path).mkdirs();
    }

    @Override
    public boolean renameTo(String path, String dest) {
        return mCache.get(path).renameTo(mCache.get(dest));
    }

    @Override
    public boolean setLastModified(String path, long time) {
        return mCache.get(path).setLastModified(time);
    }

    @Override
    public boolean setReadOnly(String path) {
        return mCache.get(path).setReadOnly();
    }

    @Override
    public boolean setWritable(String path, boolean writable, boolean ownerOnly) {
        return mCache.get(path).setWritable(writable, ownerOnly);
    }

    @Override
    public boolean setReadable(String path, boolean readable, boolean ownerOnly) {
        return mCache.get(path).setReadable(readable, ownerOnly);
    }

    @Override
    public boolean setExecutable(String path, boolean executable, boolean ownerOnly) {
        return mCache.get(path).setExecutable(executable, ownerOnly);
    }

    @Override
    public boolean checkAccess(String path, int access) {
        try {
            return Os.access(path, access);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public long getTotalSpace(String path) {
        return mCache.get(path).getTotalSpace();
    }

    @Override
    public long getFreeSpace(String path) {
        return mCache.get(path).getFreeSpace();
    }

    @SuppressLint("UsableSpace")
    @Override
    public long getUsableSpace(String path) {
        return mCache.get(path).getUsableSpace();
    }

    @Override
    public int getMode(String path) {
        try {
            return Os.lstat(path).st_mode;
        } catch (ErrnoException e) {
            return 0;
        }
    }

    @Override
    public IOResult createLink(String link, String target, boolean soft) {
        try {
            if (soft)
                Os.symlink(target, link);
            else
                Os.link(target, link);
            return new IOResult(true);
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EEXIST) {
                return new IOResult(false);
            } else {
                return new IOResult(e);
            }
        }
    }

    // I/O APIs

    private final FileContainer openFiles = new FileContainer();
    private final ExecutorService streamPool = Executors.newCachedThreadPool();

    @Override
    public void register(IBinder client) {
        int pid = Binder.getCallingPid();
        try {
            client.linkToDeath(() -> openFiles.pidDied(pid), 0);
        } catch (RemoteException ignored) {}
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public IOResult openChannel(String path, int mode, String fifo) {
        OpenFile f = new OpenFile();
        try {
            f.fd = Os.open(path, mode | O_NONBLOCK, 0666);
            f.read = Os.open(fifo, O_RDONLY | O_NONBLOCK, 0);
            f.write = Os.open(fifo, O_WRONLY | O_NONBLOCK, 0);
            return new IOResult(openFiles.put(f));
        } catch (ErrnoException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @Override
    public IOResult openReadStream(String path, ParcelFileDescriptor fd) {
        OpenFile f = new OpenFile();
        try {
            f.fd = Os.open(path, O_RDONLY, 0);
            streamPool.execute(() -> {
                try (OpenFile of = f) {
                    of.write = FileUtils.createFileDescriptor(fd.detachFd());
                    while (of.pread(PIPE_CAPACITY, -1) > 0);
                } catch (ErrnoException | IOException ignored) {}
            });
            return new IOResult();
        } catch (ErrnoException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public IOResult openWriteStream(String path, ParcelFileDescriptor fd, boolean append) {
        OpenFile f = new OpenFile();
        try {
            int mode = O_CREAT | O_WRONLY | (append ? O_APPEND : O_TRUNC);
            f.fd = Os.open(path, mode, 0666);
            streamPool.execute(() -> {
                try (OpenFile of = f) {
                    of.read = FileUtils.createFileDescriptor(fd.detachFd());
                    while (of.pwrite(PIPE_CAPACITY, -1, false) > 0);
                } catch (ErrnoException | IOException ignored) {}
            });
            return new IOResult();
        } catch (ErrnoException e) {
            f.close();
            return new IOResult(e);
        }
    }

    @Override
    public void close(int handle) {
        openFiles.remove(handle);
    }

    @Override
    public IOResult pread(int handle, int len, long offset) {
        try {
            return new IOResult(openFiles.get(handle).pread(len, offset));
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult pwrite(int handle, int len, long offset) {
        try {
            openFiles.get(handle).pwrite(len, offset, true);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult lseek(int handle, long offset, int whence) {
        try {
            return new IOResult(openFiles.get(handle).lseek(offset, whence));
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult size(int handle) {
        try {
            return new IOResult(openFiles.get(handle).size());
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult ftruncate(int handle, long length) {
        try {
            openFiles.get(handle).ftruncate(length);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }

    @Override
    public IOResult sync(int handle, boolean metadata) {
        try {
            openFiles.get(handle).sync(metadata);
            return new IOResult();
        } catch (IOException | ErrnoException e) {
            return new IOResult(e);
        }
    }
}
