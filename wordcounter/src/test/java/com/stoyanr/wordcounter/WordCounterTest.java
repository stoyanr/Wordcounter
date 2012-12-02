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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WordCounterTest {

    private static final String TEXT1 = "one two three one two one";
    private static final String TEXT2 = "five six\tseven#five";
    private static final String TEXT3 = "eight; nine\t?!ten<eleven...eight";
    private static final WordCounts COUNTS1 = new WordCounts();
    private static final WordCounts COUNTS2 = new WordCounts();
    private static final WordCounts COUNTS3 = new WordCounts();

    private static final String DIR = "words";
    private static final String FILE = "words.txt";

    static {
        // @formatter:off
        COUNTS1.add("one", 3); COUNTS1.add("two", 2); COUNTS1.add("three", 1);
        COUNTS2.add("five", 2); COUNTS2.add("six", 1); COUNTS2.add("seven", 1);
        COUNTS3.add("eight", 2); COUNTS3.add("nine", 1); COUNTS3.add("ten", 1); COUNTS3.add("eleven", 1);
        // @formatter:on
    }

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { asList(TEXT1), asList(COUNTS1) }, 
            { asList(TEXT2), asList(COUNTS2) }, 
            { asList(TEXT1, TEXT2), asList(COUNTS1, COUNTS2) }, 
            { asList(TEXT3), asList(COUNTS3) }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final List<String> texts;
    private final List<WordCounts> wcs;

    public WordCounterTest(List<String> texts, List<WordCounts> wcs) {
        this.texts = texts;
        this.wcs = wcs;
    }

    @Test
    public void testCountWordsString() {
        WordCounts result = WordUtils.countWords(createText(), Character::isAlphabetic);
        assertEquals(combineCounts(), result);
    }

    @Test
    public void testCountWordsFile() throws Exception {
        WordCounter counter = new WordCounter(createFile(), Character::isAlphabetic, null, false);
        WordCounts result = counter.count();
        assertEquals(combineCounts(), result);
    }

    @Test
    public void testCountWordsTree() throws Exception {
        WordCounter counter = new WordCounter(createTree(), Character::isAlphabetic, null, false);
        WordCounts result = counter.count();
        assertEquals(combineCounts(), result);
    }

    private String createText() {
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            sb.append(text).append("\n");
        }
        return sb.toString();
    }

    private Path createFile() throws IOException {
        File file = new File(FILE);
        FileUtils.writeStringToFile(file, createText());
        return Paths.get(file.getPath());
    }

    private Path createTree() throws IOException {
        File dir = new File(DIR);
        deleteDir(dir);
        dir.mkdirs();
        int count = 0;
        for (String text : texts) {
            File dirx = new File(DIR + "/" + count);
            dirx.mkdirs();
            FileUtils.writeStringToFile(new File(DIR + "/" + count + "/" + FILE), text);
            count++;
        }
        return Paths.get(dir.getPath());
    }

    private WordCounts combineCounts() {
        WordCounts result = new WordCounts();
        for (WordCounts wc : wcs) {
            result.add(wc);
        }
        return result;
    }
    
    static void deleteDir(File dir) {
        while (dir.exists()) {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
            }
        }
    }
}
