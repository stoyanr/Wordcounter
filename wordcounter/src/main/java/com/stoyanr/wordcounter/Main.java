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

import java.util.Arrays;

import com.stoyanr.util.Arguments;
import com.stoyanr.util.ArgumentsException;
import com.stoyanr.util.CharPredicate;
import com.stoyanr.util.Logger;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class Main {
    private static final String ARG_PATH = "p";
    private static final String ARG_CHARS = "c";
    private static final String ARG_NUMBER = "n";
    private static final String ARG_SER = "s";
    private static final String ARG_MODE = "m";
    private static final String ARG_PAR_LEVEL = "r";
    private static final String ARG_LOG_LEVEL = "l";
    private static final String ARGS_SCHEMA = ARG_PATH + "*," + ARG_CHARS + "*," + ARG_NUMBER
        + "#," + ARG_SER + "!," + ARG_MODE + "*," + ARG_PAR_LEVEL + "#," + ARG_LOG_LEVEL + "*";

    private static final String MODE_TOP = "top";
    private static final String MODE_BOTTOM = "bottom";

    private static final String LEVEL_ERROR = "error";
    private static final String LEVEL_WARNING = "warning";
    private static final String LEVEL_INFO = "info";
    private static final String LEVEL_DEBUG = "debug";

    private static final String DEFAULT_PATH = ".";
    private static final String DEFAULT_CHARS = "";
    private static final int DEFAULT_NUMBER = 10;
    private static final boolean DEFAULT_SER = false;
    private static final String DEFAULT_MODE = MODE_TOP;
    private static final int DEFAULT_PAR_LEVEL = Runtime.getRuntime().availableProcessors();
    private static final String DEFAULT_LOG_LEVEL = LEVEL_ERROR;

    private final String[] args;

    private String path;
    private Set<Character> chars;
    private int number;
    private boolean ser;
    private String mode;
    private int parLevel;
    private String logLevel;

    Main(final String[] args) {
        assert (args != null);
        this.args = Arrays.copyOf(args, args.length);
        initialize();
    }

    private void initialize() {
        try {
            final Arguments arguments = new Arguments(ARGS_SCHEMA, args);
            path = arguments.getString(ARG_PATH, DEFAULT_PATH);
            chars = createChars(arguments.getString(ARG_CHARS, DEFAULT_CHARS));
            number = arguments.getInt(ARG_NUMBER, DEFAULT_NUMBER);
            ser = arguments.getBoolean(ARG_SER, DEFAULT_SER);
            mode = arguments.getString(ARG_MODE, DEFAULT_MODE);
            parLevel = arguments.getInt(ARG_PAR_LEVEL, DEFAULT_PAR_LEVEL);
            logLevel = arguments.getString(ARG_LOG_LEVEL, DEFAULT_LOG_LEVEL);
        } catch (ArgumentsException e) {
            reportError(e);
        }
    }
    
    private static Set<Character> createChars(String s) {
        Set<Character> result = new HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            result.add(s.charAt(i));
        }
        return result;
    }

    /**
     * Runs the program.
     */
    final void run() {
        try {
            setLogLevel();
            WordCounter counter = new WordCounter(Paths.get(path), getPredicate(), !ser, parLevel);
            long t0 = System.currentTimeMillis();
            WordCounts wc = counter.count();
            long t1 = System.currentTimeMillis();
            Logger.info("Counting took %d ms", t1 - t0);
            WordCountAnalyzer analyzer = new WordCountAnalyzer(wc, !ser, parLevel);
            if (mode.equals(MODE_TOP) || mode.equals(MODE_BOTTOM)) {
                int numberx = Math.min(wc.getSize(), number);
                long t2 = System.currentTimeMillis();
                TopWordCounts twc = analyzer.findTop(numberx, getComparator());
                long t3 = System.currentTimeMillis();
                Logger.info("Analysis took %d ms", t3 - t2);
                twc.print(System.out);
            }
        } catch (final Exception e) {
            reportError(e);
        }
    }
    
    private void setLogLevel() {
        switch (logLevel) {
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

    private Comparator<Integer> getComparator() {
        return mode.equals(MODE_TOP) ? (x, y) -> (y - x) : (x, y) -> (x - y);
    }
    
    private CharPredicate getPredicate() {
        return chars.isEmpty() ? (c) -> Character.isAlphabetic(c) : 
            (c) -> Character.isAlphabetic(c) || chars.contains(c);
    }
    
    private static void reportError(final Exception e) {
        System.out.printf("%s: %s\n", e.getClass().getSimpleName(), e.getMessage());
        if (Logger.isDebug()) {
            e.printStackTrace();
        }
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
