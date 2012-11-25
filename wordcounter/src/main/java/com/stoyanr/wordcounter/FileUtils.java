package com.stoyanr.wordcounter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.stoyanr.wordcounter.TextProcessor;

public class FileUtils {
    private static final int BUF_SIZE = 256 * 1024;

    public static String readFileToString(Path file) throws IOException {
        final StringBuilder sb = new StringBuilder();
        readFileAsync(file, new TextProcessor<Void>() {
            @Override
            public Void process(String text, Void x) throws InterruptedException {
                sb.append(text);
                return x;
            }
        });
        return sb.toString();
    }

    public static <T> void readFileAsync(Path file, TextProcessor<T> processor) throws IOException {
        try (AsynchronousFileChannel ac = AsynchronousFileChannel.open(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
            T rem = null;
            int pos = 0, read = 0;
            do {
                read = readBuffer(buffer, ac, pos);
                pos += read;
                String text = Charset.defaultCharset().decode(buffer).toString();
                rem = processor.process(text, rem);
            } while (read == buffer.capacity());
            processor.process("", rem);
        } catch (IOException ex) {
            throw ex;
        } catch (ExecutionException | InterruptedException ex) {
        }
    }

    private static int readBuffer(ByteBuffer buffer, AsynchronousFileChannel ac, int pos)
        throws InterruptedException, ExecutionException {
        buffer.rewind();
        Future<Integer> future = ac.read(buffer, pos);
        while (!future.isDone()) {
            Thread.yield();
        }
        buffer.flip();
        return future.get();
    }

}
