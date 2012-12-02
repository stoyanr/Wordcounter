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

import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.functions.UnaryOperator;

import com.stoyanr.util.Arguments;
import com.stoyanr.util.ArgumentsException;
import com.stoyanr.util.CharPredicate;
import com.stoyanr.util.Logger;

public class Main {
    private static final String ARG_PATH = "p";
    private static final String ARG_CHARS = "c";
    private static final String ARG_IGNORE_CASE = "i";
    private static final String ARG_NUMBER = "n";
    private static final String ARG_SER = "s";
    private static final String ARG_MODE = "m";
    private static final String ARG_PAR_LEVEL = "r";
    private static final String ARG_LOG_LEVEL = "l";
    private static final String ARGS_SCHEMA = ARG_PATH + "*," + ARG_CHARS + "*," + 
        ARG_IGNORE_CASE + "!," + ARG_NUMBER  + "#," + ARG_SER + "!," + ARG_MODE + "*," + 
        ARG_PAR_LEVEL + "#," + ARG_LOG_LEVEL + "*";

    private static final String MODE_TOP = "top";
    private static final String MODE_BOTTOM = "bottom";
    private static final String MODE_TOTAL = "total";

    private static final String LEVEL_ERROR = "error";
    private static final String LEVEL_WARNING = "warning";
    private static final String LEVEL_INFO = "info";
    private static final String LEVEL_DEBUG = "debug";

    private static final String DEFAULT_PATH = ".";
    private static final String DEFAULT_CHARS = "";
    private static final boolean DEFAULT_IGNORE_CASE = false;
    private static final int DEFAULT_NUMBER = 10;
    private static final boolean DEFAULT_SER = false;
    private static final String DEFAULT_MODE = MODE_TOP;
    private static final int DEFAULT_PAR_LEVEL = Runtime.getRuntime().availableProcessors();
    private static final String DEFAULT_LOG_LEVEL = LEVEL_ERROR;

    private final String[] args;

    private String path;
    private Set<Character> chars;
    private boolean ignoreCase;
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
            ignoreCase = arguments.getBoolean(ARG_IGNORE_CASE, DEFAULT_IGNORE_CASE);
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
            WordCounter counter = new WordCounter(Paths.get(path), getPredicate(), getOperator(), 
                !ser, parLevel);
            long t0 = System.currentTimeMillis();
            WordCounts wc = counter.count();
            long t1 = System.currentTimeMillis();
            WordCountAnalyzer analyzer = new WordCountAnalyzer(wc, !ser, parLevel);
            long tx = 0;
            switch (mode) {
            case MODE_TOP:
            case MODE_BOTTOM:
                tx = runTopBottom(analyzer, wc);
                break;
            case MODE_TOTAL:
                tx = runTotal(analyzer, wc);
                break;
            }
            Logger.info("Counting took %d ms", t1 - t0);
            Logger.info("Analysis took %d ms", tx);
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
    
    private long runTopBottom(WordCountAnalyzer analyzer, WordCounts wc) {
        int nx = Math.min(wc.getSize(), number);
        long t0 = System.currentTimeMillis();
        TopWordCounts twc = analyzer.findTop(nx, getComparator());
        long t1 = System.currentTimeMillis();
        twc.print(System.out);
        return t1 - t0;
    }

    private long runTotal(WordCountAnalyzer analyzer, WordCounts wc) {
        long t0 = System.currentTimeMillis();
        int total = analyzer.getTotal();
        long t1 = System.currentTimeMillis();
        System.out.printf("Total words: %d\n", total);
        return t1 - t0;
    }
    
    private Comparator<Integer> getComparator() {
        return mode.equals(MODE_TOP) ? (x, y) -> (y - x) : (x, y) -> (x - y);
    }
    
    private CharPredicate getPredicate() {
        return chars.isEmpty() ? Character::isAlphabetic : 
            (c) -> Character.isAlphabetic(c) || chars.contains(c);
    }
    
    private UnaryOperator<String> getOperator() {
        return (ignoreCase) ? (s) -> s.toLowerCase() : null;
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
