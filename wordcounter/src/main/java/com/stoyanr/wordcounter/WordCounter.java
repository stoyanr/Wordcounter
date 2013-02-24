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
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.stoyanr.util.CharPredicate;
import com.stoyanr.util.FileUtils;
import com.stoyanr.util.ProducerConsumerExecutor;

/**
 * A word counter facility that provides a method for counting words in a {@code Path} representing 
 * a file or a directory tree, either serially or in parallel. It is initialized with a path, 
 * a predicate to determine whether a character is a word character, an optional unary operator 
 * to be performed on words, a flag indicating whether to use parallel processing, and (optionally) 
 * a parallelism level. 
 * <p>
 * To use this class, simply instantiate it with the appropriate lambdas and then call its 
 * {@code count} method:
 * <p>
 * <pre>
 * // Count all words consisting of only alphabetic chars, ignoring case, using parallel processing
 * new WordCounter(path, (c) -> Character.isAlphabetic(c), (s) -> s.toLowerCase(), true).count();
 * </pre>
 * 
 * @author Stoyan Rachev
 */
public class WordCounter {

    private final Path path;
    private final CharPredicate pred;
    private final UnaryOperator<String> op;
    private final boolean par;
    private final int parLevel;
    
    public WordCounter(Path path, CharPredicate pred, UnaryOperator<String> op, boolean par) {
        this(path, pred, op, par, ProducerConsumerExecutor.DEFAULT_PAR_LEVEL);
    }

    public WordCounter(Path path, CharPredicate pred, UnaryOperator<String> op, boolean par, 
        int parLevel) {
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("Path is null or doesn't exist.");
        }
        if (pred == null) {
            throw new IllegalArgumentException("Predicate is null.");
        }
        this.path = path;
        this.pred = pred;
        this.op = op;
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
                    (file) -> wc.add(countWords(readFileToString(file), pred, op))));
            } else {
                wc = countWords(readFileToString(path), pred, op);
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
                this::collectPaths,
                this::readFileToBlock,
            (text) -> wc.add(countWords(text, pred, op)), parLevel).execute();
        return wc;
    }

    private void collectPaths(Consumer<Path> block) {
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new FileVisitor(block::accept));
            } else {
                block.accept(path);
            }
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't walk directory tree %s: %s", 
                path.toString(), e.getMessage()), e);
        }
    }
    
    private void readFileToBlock(Path file, Consumer<String> block) {
        try {
            FileUtils.readFileAsync(file, (String text, String state) -> { 
                return applyText(text, state, block); 
            });
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't read file %s: %s", file.toString(), 
                e.getMessage()), e);
        }
    }

    private String applyText(String text, String state, Consumer<String> block) {
        int ei = getEndWordIndex(text, pred);
        String rem = (state != null) ? state : "";
        String textx = rem + text.substring(0, ei);
        rem = text.substring(ei);
        block.accept(textx);
        return rem;
    }
    
    final static class FileVisitor extends SimpleFileVisitor<Path> {
    
        private final Consumer<Path> block;

        public FileVisitor(Consumer<Path> block) {
            this.block = block;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            block.accept(file);
            return FileVisitResult.CONTINUE;
        }
    }

}
