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

import static com.stoyanr.util.Logger.debug;
import static com.stoyanr.util.Logger.isDebug;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;

final class Reader {

    private static final int MAX_ST_SIZE = 1024 * 1024;
    private static final EnumSet<FileVisitOption> OPTIONS = EnumSet
        .of(FileVisitOption.FOLLOW_LINKS);
    private final File file;
    private final BlockingQueue<String> queue;
    private final WordCounter wc;

    Reader(File file, BlockingQueue<String> queue, WordCounter wc) {
        this.file = file;
        this.queue = queue;
        this.wc = wc;
    }

    void read() {
        try {
            if (file.isDirectory()) {
                readDirectory(Paths.get(file.getPath()));
            } else {
                readFile(Paths.get(file.getPath()));
            }
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't read file %s: %s", file.toString(), 
                e.getMessage()), e);
        }
    }

    private void readDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, OPTIONS, Integer.MAX_VALUE, 
            new WordCounterFileVisitor((file) -> readFile(file)));
    }

    private void readFile(final Path file) throws IOException {
        try {
            if (Files.size(file) <= MAX_ST_SIZE) {
                String text = FileUtils.readFileToString(file);
                logReaderJobDone(file, text);
                produceText(text);
            } else {
                FileUtils.readFileAsync(file, (String text, String state) -> {
                    logReaderJobDone(file, text);
                    return produceText(text, state);
                });
            }
        } catch (InterruptedException e) {
            throw new WordCounterException(String.format("Interrupted while reading file %s: %s", 
                file.toString(), e.getMessage()), e);
        }
    }

    private String produceText(String text, String state) throws InterruptedException {
        int ei = wc.getEndIndex(text);
        String rem = (state != null) ? state : "";
        String textx = rem + text.substring(0, ei);
        rem = text.substring(ei);
        produceText(textx);
        return rem;
    }
    
    private void produceText(String text)
        throws InterruptedException {
        long t0 = logReaderQueueFull();
        queue.put(text);
        logReaderWaitTime(t0);
    }

    private long logReaderQueueFull() {
        long t0 = 0;
        if (isDebug() && queue.remainingCapacity() == 0) {
            debug("[Reader (%s)] Queue full, waiting ...", getThreadName());
            t0 = System.nanoTime();
        }
        return t0;
    }

    private void logReaderWaitTime(long t0) {
        if (isDebug() && t0 != 0) {
            long t1 = System.nanoTime();
            debug("[Reader (%s)] Waited for %.2f us", getThreadName(), ((double) (t1 - t0)) / 1000);
        }
    }

    private void logReaderJobDone(Path file, String text) {
        if (isDebug()) {
            debug("[Reader (%s)] Read text '%s' (%s)", getThreadName(), trim(text), file.toString());
        }
    }

    private static String trim(String text) {
        return text.substring(0, Math.min(text.length(), 20));
    }

    private static String getThreadName() {
        return Thread.currentThread().getName();
    }
}
