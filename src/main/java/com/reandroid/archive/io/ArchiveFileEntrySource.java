/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.archive.io;

import com.reandroid.archive.Archive;
import com.reandroid.archive.ArchiveEntry;
import com.reandroid.utils.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ArchiveFileEntrySource extends ArchiveEntrySource<ZipFileInput> {

    public ArchiveFileEntrySource(ZipFileInput zipInput, ArchiveEntry archiveEntry){
        super(zipInput, archiveEntry);
        setSort(archiveEntry.getIndex());
    }

    @Override
    public byte[] getBytes(int length) throws IOException {
        FileChannel fileChannel = getFileChannel();
        if(getMethod() != Archive.STORED || fileChannel == null){
            return super.getBytes(length);
        }
        byte[] bytes = new byte[length];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        fileChannel.read(byteBuffer);
        return bytes;
    }

    FileChannel getFileChannel() throws IOException {
        ZipFileInput zipInput = getZipSource();
        FileChannel fileChannel = zipInput.getFileChannel();
        fileChannel.position(getArchiveEntry().getFileOffset());
        return fileChannel;
    }

    @Override
    public void write(File file) throws IOException {
        FileChannel fileChannel = getFileChannel();
        if(getMethod() != Archive.STORED || fileChannel == null){
            super.write(file);
            return;
        }
        FileChannel outputChannel = FileUtil.openWriteChannel(file);

        long totalTransferred = 0;
        long remaining = getLength();

        while (remaining > 0) {
            long transferred = outputChannel.transferFrom(fileChannel, totalTransferred, remaining);
            totalTransferred += transferred;
            remaining -= transferred;
        }

        outputChannel.close();
    }

}
