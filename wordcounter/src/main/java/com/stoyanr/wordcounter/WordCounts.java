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

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiBlock;

import com.stoyanr.util.Logger;

/**
 * A list of words mapped to their usage counts. Provides methods for adding word counts, checking 
 * for equality, printing, and internal iterations over its contents.
 * Internally, this class encapsulates a {@code Map<String, AtomicInteger>} which is either a 
 * {@code HashMap} or a {@code ConcurrentHashMap} depending on the parallelism level specified 
 * upon construction. The word counting methods of {@link WordUtils} and {@link WordCounter} return 
 * instances of this class.
 * 
 * @author Stoyan Rachev
 */
public class WordCounts {
    
    private final Map<String, AtomicInteger> m;
    
    public WordCounts() {
        this(1);
    }
    
    public WordCounts(int parLevel) {
        this.m = (parLevel == 1) ? new HashMap<String, AtomicInteger>() : 
            new ConcurrentHashMap<String, AtomicInteger>(4096, 0.75f, parLevel);
    }
    
    public int getSize() {
        return m.size();
    }
    
    public void add(String word, int count) {
        AtomicInteger cc = m.get(word);
        if (cc != null) {
            cc.addAndGet(count);
        } else {
            if (m instanceof ConcurrentMap) {
                cc = ((ConcurrentMap<String, AtomicInteger>) m).putIfAbsent(word, new AtomicInteger(count));
                // Another thread might have added the same value in the meantime
                if (cc != null) {
                    cc.addAndGet(count);
                }
            } else {
                m.put(word, new AtomicInteger(count));
            }
        }
    }

    public void add(WordCounts wc) {
        for (Map.Entry<String, AtomicInteger> e : wc.m.entrySet()) {
            add(e.getKey(), e.getValue().get());
        }
    }
    
    public void set(String word, int count) {
        AtomicInteger cc = m.get(word);
        if (cc != null) {
            cc.set(count);
        } else {
            if (m instanceof ConcurrentMap) {
                cc = ((ConcurrentMap<String, AtomicInteger>) m).putIfAbsent(word, new AtomicInteger(count));
                // Another thread might have added the same value in the meantime
                if (cc != null) {
                    cc.set(count);
                }
            } else {
                m.put(word, new AtomicInteger(count));
            }
        }
    }
    
    public void forEachInRange(int lo, int hi, BiBlock<String, Integer> block) {
        Iterator<Map.Entry<String, AtomicInteger>> it = m.entrySet().iterator();
        for (int i = 0; i < lo; i++) {
            it.next();
        }
        for (int i = lo; i < hi; i++) {
            Map.Entry<String, AtomicInteger> e = it.next();
            block.accept(e.getKey(), e.getValue().get());
        }
    }

    public void print(PrintStream ps) {
        Logger.debug("Printing raw word counts");
        for (Entry<String, AtomicInteger> e : m.entrySet()) {
            ps.printf("%20s: %d\n",  e.getKey(), e.getValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WordCounts)) {
            return false;
        }
        WordCounts wc = (WordCounts) o;
        if (m.size() != wc.m.size()) {
            return false;
        }
        for (Entry<String, AtomicInteger> e : wc.m.entrySet()) {
            if (m.get(e.getKey()).get() != e.getValue().get()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(m);
    }
    
    Set<Entry<String, AtomicInteger>> getEntries() {
        return m.entrySet();
    }
}
