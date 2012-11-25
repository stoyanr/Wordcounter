package com.stoyanr.wordcounter;

import java.io.IOException;
import java.nio.file.Path;

interface FileProcessor {
    void process(Path file) throws IOException;
}