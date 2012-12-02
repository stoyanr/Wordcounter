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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.stoyanr.util.Logger;

@RunWith(Parameterized.class)
public class WordCounterPerfTest {

    private static final String[] VOCABULARY = { "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen" };

    private static final String DELIMS = " \t\n\r\f;,.:?!/\\'\"()[]{}<>+-*=~@#$%^&|`";

    private static final String DIR = "words";
    private static final String FILE = "words.txt";

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { 1, 10000000, false }, 
            { 1, 10000000, true }, 
            { 100, 100000, false }, 
            { 100, 100000, true }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final int numFiles;
    private final int maxWords;
    private final boolean par;

    private WordCounter counter;
    private WordCounts wc;
    private Path tree;

    public WordCounterPerfTest(int numFiles, int maxWords, boolean par) {
        this.numFiles = numFiles;
        this.maxWords = maxWords;
        this.par = par;
    }

    @Before
    public void setUp() throws Exception {
        Logger.level = Logger.Level.INFO;
        wc = new WordCounts();
        tree = createTree(wc);
        counter = new WordCounter(tree, Character::isAlphabetic, null, par);
    }

    @Test
    public void test() throws Exception {
        System.out.printf("Processing %d files (parallel: %b) ...\n", numFiles, par);
        long time0 = System.currentTimeMillis();
        WordCounts wcx = counter.count();
        long time1 = System.currentTimeMillis();
        System.out.printf("Processed %d files in %d ms\n", numFiles, (time1 - time0));
        printCounts(wcx);
        assertEquals(wc, wcx);
    }

    private Path createTree(WordCounts wc) throws IOException {
        File dir = new File(DIR);
        WordCounterTest.deleteDir(dir);
        dir.mkdirs();
        for (int i = 0; i < numFiles; i++) {
            File dirx = new File(DIR + "/" + i);
            dirx.mkdirs();
            FileUtils.writeStringToFile(new File(DIR + "/" + i + "/" + FILE), createText(wc));
        }
        return Paths.get(dir.getPath());
    }

    private String createText(WordCounts wc) {
        int numWords = maxWords;
        StringBuilder sb = new StringBuilder(numWords * 10);
        for (int i = 0; i < numWords; i++) {
            int index = (int) (Math.random() * VOCABULARY.length);
            String word = VOCABULARY[index];
            if ((int) (Math.random() * 2) == 0) {
                word = word.toUpperCase();
            }
            sb.append(word);
            if (i < numWords - 1) {
                appendDelimiters(sb);
            }
            wc.add(word, 1);
        }
        return sb.toString();
    }

    private void appendDelimiters(StringBuilder sb) {
        int numDelims = (int) (Math.random() * 3) + 1;
        for (int i = 0; i < numDelims; i++) {
            int index = (int) (Math.random() * DELIMS.length());
            sb.append(DELIMS.charAt(index));
        }
    }

    private void printCounts(WordCounts counts) {
        if (Logger.isDebug()) {
            counts.print(System.out);
        }
    }

}
