/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.stoyanr.wordfinder;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 *
 * @author i027947
 */
class WordCounter {

    public Map<String, Integer> countWords(String text) {
        Scanner scanner = new Scanner(text);
        System.out.printf("Counting words in %s\n", text);
        return count(scanner);
    }

    public Map<String, Integer> countWords(File file) throws FileNotFoundException {
        Scanner scanner = new Scanner(file);
        System.out.printf("Counting words in %s\n", file);
        return count(scanner);
    }

    private Map<String, Integer> count(Scanner scanner) {
        Map<String, Integer> map = new HashMap<>();
        while (scanner.hasNext()) {
            String word = scanner.next();
            int count = (map.containsKey(word)) ? map.get(word) + 1 : 1;
            map.put(word, count);
        }
        return map;
    }
}
