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

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;

import com.stoyanr.util.Arguments;
import com.stoyanr.util.ArgumentsException;
import com.stoyanr.util.Logger;

public class Main {
    private static final String ARG_PATH = "p";
    private static final String ARG_DELIMS = "d";
    private static final String ARG_NUMBER = "n";
    private static final String ARG_MODE = "m";
    private static final String ARG_LEVEL = "l";
    private static final String ARGS_SCHEMA = ARG_PATH + "*," + ARG_DELIMS + "*," + ARG_NUMBER
        + "#," + ARG_MODE + "*," + ARG_LEVEL + "*";

    private static final String MODE_TOP = "top";
    private static final String MODE_BOTTOM = "bottom";

    private static final String LEVEL_ERROR = "error";
    private static final String LEVEL_WARNING = "warining";
    private static final String LEVEL_INFO = "info";
    private static final String LEVEL_DEBUG = "debug";

    private static final String DEFAULT_PATH = ".";
    private static final String DEFAULT_DELIMS = WordCounter.DEFAULT_DELIMITERS;
    private static final int DEFAULT_NUMBER = 10;
    private static final String DEFAULT_MODE = MODE_TOP;
    private static final String DEFAULT_LEVEL = LEVEL_INFO;

    private final String[] args;

    private String path;
    private String delims;
    private int number;
    private String mode;
    private String level;

    Main(final String[] args) {
        assert (args != null);
        this.args = Arrays.copyOf(args, args.length);
        initialize();
    }

    private void initialize() {
        try {
            final Arguments arguments = new Arguments(ARGS_SCHEMA, args);
            path = arguments.getString(ARG_PATH, DEFAULT_PATH);
            delims = arguments.getString(ARG_DELIMS, DEFAULT_DELIMS);
            number = arguments.getInt(ARG_NUMBER, DEFAULT_NUMBER);
            mode = arguments.getString(ARG_MODE, DEFAULT_MODE);
            level = arguments.getString(ARG_LEVEL, DEFAULT_LEVEL);
        } catch (ArgumentsException e) {
            reportError(e);
        }
    }

    /**
     * Runs the program.
     */
    public final void run() {
        try {
            setLogLevel();
            WordCounter counter = new WordCounter(delims);
            Map<String, Integer> counts = counter.countWords(new File(path), true);
            if (Logger.isDebug()) {
                printCounts(counts);
            }
            WordCountAnalyzer analyzer = new WordCountAnalyzer();
            if (mode.equals(MODE_TOP) || mode.equals(MODE_BOTTOM)) {
                int numberx = Math.min(counts.size(), number);
                boolean top = mode.equals(MODE_TOP);
                SortedMap<Integer, Set<String>> sorted = analyzer.findTop(counts, numberx, top,
                    true);
                printSorted(sorted, number, top);
            }
        } catch (final Exception e) {
            reportError(e);
        }
    }

    private void setLogLevel() {
        switch (level) {
        case LEVEL_ERROR:
            Logger.level = Logger.Level.ERROR;
            break;
        case LEVEL_WARNING:
            Logger.level = Logger.Level.WARNING;
            break;
        case LEVEL_INFO:
            Logger.level = Logger.Level.INFO;
            break;
        case LEVEL_DEBUG:
            Logger.level = Logger.Level.DEBUG;
            break;
        }
    }

    static void printCounts(Map<String, Integer> counts) {
        Logger.debug("Printing raw word counts");
        for (Entry<String, Integer> e : counts.entrySet()) {
            String word = e.getKey();
            int count = e.getValue();
            System.out.printf("%20s: %d\n", word, count);
        }
    }

    static void printSorted(SortedMap<Integer, Set<String>> sorted, int number, boolean top) {
        Logger.debug("Printing %s %d words", (top) ? "top" : "bottom", number);
        int i = 0;
        for (Entry<Integer, Set<String>> e : sorted.entrySet()) {
            int count = e.getKey();
            Set<String> words = e.getValue();
            for (String word : words) {
                System.out.printf("%20s: %d\n", word, count);
                if (++i == number) {
                    return;
                }
            }
        }
    }

    private static void reportError(final Exception e) {
        System.out.printf("%s: %s\n", e.getClass().getSimpleName(), e.getMessage());
        e.printStackTrace();
    }

    /**
     * Runs the command line program. Creates a new Main instance with the passed arguments its
     * {@link #run()} method.
     * 
     * @param args The program arguments.
     */
    public static void main(final String[] args) {
        new Main(args).run();
    }

}
