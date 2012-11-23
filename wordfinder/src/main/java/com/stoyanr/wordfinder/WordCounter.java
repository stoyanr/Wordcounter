/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.stoyanr.wordfinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

/**
 * 
 * @author i027947
 */
class WordCounter {

    public Map<String, Integer> countWords(String text) {
        if (text == null)
            throw new IllegalArgumentException();
        Scanner scanner = new Scanner(text);
        return countWords(scanner);
    }

    public Map<String, Integer> countWords(File file) throws IOException {
        if (file == null || !file.exists())
            throw new IllegalArgumentException();
        return (file.isDirectory()) ? countWordsDir(file) : countWords(new Scanner(file));
    }

    private Map<String, Integer> countWordsDir(File dir) throws IOException {
        Path path = Paths.get(dir.getPath());
        WordCounterVisitor visitor = new WordCounterVisitor();
        Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
            visitor);
        return visitor.getCounts();
    }

    public Map<String, Integer> countWords(Scanner scanner) {
        if (scanner == null)
            throw new IllegalArgumentException();
        Map<String, Integer> map = new HashMap<>();
        while (scanner.hasNext()) {
            String word = scanner.next();
            int count = (map.containsKey(word)) ? map.get(word) + 1 : 1;
            map.put(word, count);
        }
        return map;
    }

    private class WordCounterVisitor implements FileVisitor<Path> {

        private Map<String, Integer> counts = new HashMap<>();

        public Map<String, Integer> getCounts() {
            return counts;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            addCounts(counts, countWords(new Scanner(file.toFile())));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

    }

    static void addCounts(Map<String, Integer> counts, Map<String, Integer> countsx) {
        for (Entry<String, Integer> e : countsx.entrySet()) {
            String word = e.getKey();
            int count = (counts.containsKey(word)) ? counts.get(word) + e.getValue() : e
                .getValue();
            counts.put(word, count);
        }
    }

}
