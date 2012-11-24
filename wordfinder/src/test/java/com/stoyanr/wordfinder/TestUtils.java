package com.stoyanr.wordfinder;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;

public class TestUtils {

    public static void deleteDir(File dir) {
        while (dir.exists()) {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
            }
        }
    }
    
    public static void assertEqualMaps(Map<String, Integer> expected, Map<String, Integer> actual) {
        assertEquals(expected.size(), actual.size());
        for (Entry<String, Integer> e : actual.entrySet()) {
            assertEquals(expected.get(e.getKey()), e.getValue());
        }
    }
}
