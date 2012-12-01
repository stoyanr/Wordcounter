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
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WordCountAnalyzerTest {
    
    private static final Comparator<Integer> COMP = (x, y) -> (y - x);

    private static final WordCounts COUNTS1 = new WordCounts();
    private static final WordCounts COUNTS2 = new WordCounts();
    private static final WordCounts COUNTS3 = new WordCounts();
    
    private static final TopWordCounts TWC1 = new TopWordCounts(2, COMP);
    private static final TopWordCounts TWC2 = new TopWordCounts(1, COMP);
    private static final TopWordCounts TWC3 = new TopWordCounts(2, COMP);

    static {
        // @formatter:off
        COUNTS1.add("one", 3); COUNTS1.add("two", 2); COUNTS1.add("three", 1);
        COUNTS2.add("five", 2); COUNTS2.add("six", 1); COUNTS2.add("seven", 1);
        COUNTS3.add("eight", 2); COUNTS3.add("nine", 1); COUNTS3.add("ten", 1); COUNTS3.add("eleven", 1);
        
        TWC1.add(3, asSet("one")); TWC1.add(2, asSet("two")); TWC1.add(1, asSet("three")); 
        TWC2.add(2, asSet("five")); TWC2.add(1, asSet("six", "seven")); 
        TWC3.add(2, asSet("eight")); TWC3.add(1, asSet("nine", "ten", "eleven")); 
        // @formatter:on
    }

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { COUNTS1, TWC1, 2, 6 }, 
            { COUNTS2, TWC2, 1, 4 }, 
            { COUNTS3, TWC3, 2, 5 }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final WordCounts wc;
    private final TopWordCounts twc;
    private final int number;
    private final int total;

    private WordCountAnalyzer a1, a2;

    public WordCountAnalyzerTest(WordCounts wc, TopWordCounts twc, int number, int total) {
        this.wc = wc;
        this.twc = twc;
        this.number = number;
        this.total = total;
    }

    @Before
    public void setUp() throws Exception {
        a1 = new WordCountAnalyzer(wc, false);
        a2 = new WordCountAnalyzer(wc, true);
    }

    @Test
    public void testFindTopSer() {
        assertEquals(twc, a1.findTop(number, COMP));
    }

    @Test
    public void testFindTopPar() {
        assertEquals(twc, a2.findTop(number, COMP));
    }

    @Test
    public void testGetTotalSer() {
        assertEquals(total, a1.getTotal());
    }

    @Test
    public void testGetTotalPar() {
        assertEquals(total, a2.getTotal());
    }

    private static Set<String> asSet(String... strings) {
        return new HashSet<>(asList(strings));
    }
}
