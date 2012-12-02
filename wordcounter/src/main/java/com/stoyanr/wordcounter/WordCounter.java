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

import static com.stoyanr.wordcounter.WordUtils.countWords;
import static com.stoyanr.wordcounter.WordUtils.getEndWordIndex;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.functions.Block;

import com.stoyanr.util.CharPredicate;
import com.stoyanr.util.FileUtils;
import com.stoyanr.util.ProducerConsumerExecutor;

public class WordCounter {

    private static final int MAX_ST_SIZE = 1024 * 1024;
    
    private final Path path;
    private final CharPredicate pred;
    private final boolean par;
    private final int parLevel;
    
    public WordCounter(Path path, CharPredicate pred, boolean par) {
        this(path, pred, par, ProducerConsumerExecutor.DEFAULT_PAR_LEVEL);
    }

    public WordCounter(Path path, CharPredicate pred, boolean par, int parLevel) {
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("Path is null or doesn't exist.");
        }
        if (pred == null) {
            throw new IllegalArgumentException("Predicate is null.");
        }
        this.path = path;
        this.pred = pred;
        this.par = par;
        this.parLevel = parLevel;
    }

    public WordCounts count() {
        return (par) ? countPar() : countSer();
    }

    private WordCounts countSer() {
        final WordCounts wc;
        try {
            if (Files.isDirectory(path)) {
                wc = new WordCounts();
                Files.walkFileTree(path, new FileVisitor(
                    (file) -> wc.add(countWords(readFileToString(file), pred))));
            } else {
                wc = countWords(readFileToString(path), pred);
            }
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't walk directory tree %s: %s", 
                path.toString(), e.getMessage()), e);
        }
        return wc;
    }
    
    private String readFileToString(Path file) {
        try {
            return FileUtils.readFileToString(file);
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't read file %s: %s", file.toString(), 
                e.getMessage()), e);
        }
    }

    private WordCounts countPar() {
        final WordCounts wc = new WordCounts(parLevel);
        new ProducerConsumerExecutor<Path, String>(
            (block) -> collectPaths(block), 
            (file, block) -> readFileToBlock(file, block),
            (text) -> wc.add(countWords(text, pred)), parLevel).execute();
        return wc;
    }

    private void collectPaths(Block<Path> block) {
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new FileVisitor((file) -> block.apply(file)));
            } else {
                block.apply(path);
            }
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't walk directory tree %s: %s", 
                path.toString(), e.getMessage()), e);
        }
    }
    
    private void readFileToBlock(Path file, Block<String> block) {
        try {
            FileUtils.readFileAsync(file, (String text, String state) -> { 
                return applyText(text, state, block); 
            });
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't read file %s: %s", file.toString(), 
                e.getMessage()), e);
        }
    }

    private String applyText(String text, String state, Block<String> block) {
        int ei = getEndWordIndex(text, pred);
        String rem = (state != null) ? state : "";
        String textx = rem + text.substring(0, ei);
        rem = text.substring(ei);
        block.apply(textx);
        return rem;
    }
    
    final static class FileVisitor extends SimpleFileVisitor<Path> {
    
        private final Block<Path> block;

        public FileVisitor(Block<Path> block) {
            this.block = block;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            block.apply(file);
            return FileVisitResult.CONTINUE;
        }
    }

}
