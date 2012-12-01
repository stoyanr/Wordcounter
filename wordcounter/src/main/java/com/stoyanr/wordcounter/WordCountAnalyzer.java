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

import com.stoyanr.util.ForkJoinComputer;
import java.util.Comparator;

public class WordCountAnalyzer {

    private static final int THRESHOLD = 32 * 1024;
    
    private final WordCounts wc;
    private final boolean par;
    private final int parLevel;
    
    public WordCountAnalyzer(WordCounts wc, boolean par) {
        this(wc, par, ForkJoinComputer.DEFAULT_PAR_LEVEL);
    }
    
    public WordCountAnalyzer(WordCounts wc, boolean par, int parLevel) {
        if (wc == null) {
            throw new IllegalArgumentException("Word counts is null.");
        }
        this.wc = wc;
        this.par = par;
        this.parLevel = parLevel;
    }

    public TopWordCounts findTop(int number, Comparator<Integer> comparator) {
        return analyse(new FindTopAnalysis(number, comparator));
    }
    
    public int getTotal() {
        return analyse(new TotalAnalysis());
    }
    
    private <T> T analyse(Analysis<T> a) {
        if (par) {
            return new ForkJoinComputer<T>(wc.getSize(), THRESHOLD, a::compute, a::merge, parLevel).compute();
        } else {
            return a.compute(0, wc.getSize());
        }
    }
    
    interface Analysis<T> {
        T compute(int lo, int hi);
        
        T merge(T r1, T r2);
    }
    
    final class FindTopAnalysis implements Analysis<TopWordCounts> {

        private final int number;
        private final Comparator<Integer> comparator;

        FindTopAnalysis(int number, Comparator<Integer> comparator) {
            if (number < 0 || number > wc.getSize()) {
                throw new IllegalArgumentException("Number is negative or too big.");
            }
            if (comparator == null) {
                throw new IllegalArgumentException("Comparator is null.");
            }
            this.number = (number != 0) ? number : wc.getSize();
            this.comparator = comparator;
        }

        @Override
        public TopWordCounts compute(int lo, int hi) {
            TopWordCounts result = new TopWordCounts(number, comparator);
            wc.forEachInRange(lo, hi, (word, count) -> result.addIfNeeded(count, word));
            return result;
        }
        
        @Override
        public TopWordCounts merge(TopWordCounts r1, TopWordCounts r2) {
            r1.add(r2);
            return r1;
        }
    }
    
    final class TotalAnalysis implements Analysis<Integer> {

        @Override
        public Integer compute(int lo, int hi) {
            int[] result = new int[] { 0 };
            wc.forEachInRange(lo, hi, (word, count) -> { result[0] += count; });
            return result[0];
        }
        
        @Override
        public Integer merge(Integer r1, Integer r2) {
            return r1 + r2;
        }
    }
    
}
