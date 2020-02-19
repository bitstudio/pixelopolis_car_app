/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bit.pixelopolis_car.services.streaming;

import java.io.IOException;
import java.io.OutputStream;

public class BufferStream extends OutputStream {
    private final byte[] mBuffer;
    private int mLength;

    BufferStream(int size) {
        this(new byte[size]);
    }

    BufferStream(byte[] buffer) {
        this.mLength = 0;
        this.mBuffer = buffer;
    }

    public void write(byte[] buffer, int offset, int count) throws IOException {
        checkSpace(count);
        System.arraycopy(buffer, offset, this.mBuffer, this.mLength, count);
        this.mLength += count;
    }

    public void write(byte[] buffer) throws IOException {
        checkSpace(buffer.length);
        System.arraycopy(buffer, 0, this.mBuffer, this.mLength, buffer.length);
        this.mLength += buffer.length;
    }

    public void write(int oneByte) throws IOException {
        checkSpace(1);
        byte[] bArr = this.mBuffer;
        int i = this.mLength;
        this.mLength = i + 1;
        bArr[i] = (byte) oneByte;
    }

    private void checkSpace(int length) throws IOException {
        if (this.mLength + length >= this.mBuffer.length) {
            throw new IOException("Buffer space not available");
        }
    }

    public void seek(int index) {
        this.mLength = index;
    }

    public byte[] getBuffer() {
        return this.mBuffer;
    }

    public int getLength() {
        return this.mLength;
    }
}
