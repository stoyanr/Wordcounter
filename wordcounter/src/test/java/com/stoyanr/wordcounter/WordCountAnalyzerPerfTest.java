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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.Comparator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.stoyanr.util.Logger;

@RunWith(Parameterized.class)
public class WordCountAnalyzerPerfTest {
    
    private static final Comparator<Integer> COMP = (x, y) -> (y - x);
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final int MAX_LENGTH = 12;

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { 2000000, 1000000000, 10, false }, 
            { 2000000, 1000000000, 10, true }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final int numWords;
    private final int maxCount;
    private final int number;
    private final boolean par;

    private WordCountAnalyzer analyzer;
    private WordCounts wc;
    private TopWordCounts twc;

    public WordCountAnalyzerPerfTest(int numWords, int maxCount, int number, boolean par) {
        this.numWords = numWords;
        this.maxCount = maxCount;
        this.number = number;
        this.par = par;
    }

    @Before
    public void setUp() {
        Logger.level = Logger.Level.INFO;
        wc = createWordCounts();
        twc = getTopWordCounts(wc);
        analyzer = new WordCountAnalyzer(wc, par);
    }

    @Test
    public void test() throws Exception {
        System.out.printf("Processing %d words (parallel: %b) ...\n", wc.getSize(), par);
        long time0 = System.currentTimeMillis();
        TopWordCounts twcx = analyzer.findTop(number, COMP);
        long time1 = System.currentTimeMillis();
        System.out.printf("Analyzed %d words in %d ms\n", wc.getSize(), (time1 - time0));
        printSorted(twcx);
        assertEquals(twc, twcx);
    }

    private WordCounts createWordCounts() {
        WordCounts wc = new WordCounts();
        for (int i = 0; i < numWords; i++) {
            wc.set(getRandomWord(), getRandomCount());
        }
        return wc;
    }

    private String getRandomWord() {
        StringBuilder sb = new StringBuilder();
        int length = (int) (Math.random() * MAX_LENGTH) + 1;
        for (int j = 0; j < length; j++) {
            int index = (int) (Math.random() * ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    private int getRandomCount() {
        return (int) (Math.random() * maxCount);
    }

    private TopWordCounts getTopWordCounts(WordCounts wc) {
        TopWordCounts twc = new TopWordCounts(number, COMP);
        wc.forEachInRange(0, wc.getSize(), (word, count) -> twc.add(count, word));
        return twc;
    }

    private void printSorted(TopWordCounts sorted) {
        if (Logger.isDebug()) {
            sorted.print(System.out);
        }
    }
}
