/*
 * $Id: $
 *
 * Copyright 2012 Stoyan Rachev (stoyanr@gmail.com)
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
package com.stoyanr.wordcounter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FileUtils {
    private static final int BUF_SIZE = 256 * 1024;
    
    interface TextProcessor<T> {
        T process(String text, T state) throws InterruptedException;
    }

    static String readFileToString(Path file) throws IOException {
        final StringBuilder sb = new StringBuilder();
        readFileAsync(file, (String text, Void x) -> {
            sb.append(text);
            return x;
        });
        return sb.toString();
    }

    static <T> void readFileAsync(Path file, TextProcessor<T> processor) throws IOException {
        try (AsynchronousFileChannel ac = AsynchronousFileChannel.open(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
            T rem = null;
            int pos = 0, read;
            do {
                read = readBuffer(buffer, ac, pos);
                pos += read;
                String text = Charset.defaultCharset().decode(buffer).toString();
                rem = processor.process(text, rem);
            } while (read == buffer.capacity());
            processor.process("", rem);
        } catch (IOException e) {
            throw e;
        } catch (ExecutionException | InterruptedException e) {
            throw new WordCounterException(String.format("Can't read file %s: %s", file.toString(), 
                e.getMessage()), e);
        }
    }

    private static int readBuffer(ByteBuffer buffer, AsynchronousFileChannel ac, int pos)
        throws InterruptedException, ExecutionException {
        buffer.rewind();
        Future<Integer> future = ac.read(buffer, pos);
        while (!future.isDone()) {
            Thread.yield();
        }
        buffer.flip();
        return future.get();
    }

}
