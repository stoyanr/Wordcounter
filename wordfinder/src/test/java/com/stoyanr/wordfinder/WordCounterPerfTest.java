package com.stoyanr.wordfinder;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WordCounterPerfTest {

    private static final String[] VOCABULARY = { "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen" };

    private static final String DELIMS = WordCounter.DEFAULT_DELIMITERS
        + ";,.:?!/\\'\"()[]{}<>+-*=~@#$%^&|`";

    private static final String DIR = "words";
    private static final String FILE = "words.txt";

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { 100, 100000 }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final int numFiles;
    private final int maxWords;

    private WordCounter counter;

    public WordCounterPerfTest(int numFiles, int maxWords) {
        super();
        this.numFiles = numFiles;
        this.maxWords = maxWords;
    }

    @Before
    public void setUp() {
        counter = new WordCounter(DELIMS, 4);
    }

    @Test
    public void testCountWordsDirectoryTree() throws Exception {
        Map<String, Integer> expected = new HashMap<>();
        File tree = tree(expected);
        System.out.printf("Processin %d files ...\n", numFiles);
        long time0 = System.currentTimeMillis();
        Map<String, Integer> result = counter.countWords(tree);
        long time1 = System.currentTimeMillis();
        System.out.printf("Processed %d files in %d ms\n", numFiles, (time1 - time0));
        TestUtils.assertEqualMaps(expected, result);
    }

    private File tree(Map<String, Integer> counts) throws IOException {
        File dir = new File(DIR);
        TestUtils.deleteDir(dir);
        dir.mkdirs();
        for (int i = 0; i < numFiles; i++) {
            File dirx = new File(DIR + "/" + i);
            dirx.mkdirs();
            FileUtils.writeStringToFile(new File(DIR + "/" + i + "/" + FILE), text(counts));
        }
        return dir;
    }

    private String text(Map<String, Integer> counts) {
        int numWords = (int) (Math.random() * maxWords);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numWords; i++) {
            int index = (int) (Math.random() * VOCABULARY.length);
            String word = VOCABULARY[index];
            sb.append(word);
            appendDelimiters(sb);
            counts.put(word, (counts.containsKey(word)) ? counts.get(word) + 1 : 1);
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

}
