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
package com.stoyanr.util;

public final class Logger {
    public enum Level {
        ERROR, WARNING, INFO, DEBUG
    }

    private Logger() {
        // No implementation needed
    }

    public static Level level = Level.INFO;

    public static boolean isError() {
        return levelHigherThan(Level.ERROR);
    }

    public static boolean isWarning() {
        return levelHigherThan(Level.WARNING);
    }

    public static boolean isInfo() {
        return levelHigherThan(Level.INFO);
    }

    public static boolean isDebug() {
        return levelHigherThan(Level.DEBUG);
    }

    public static void error(final String message, final Object... args) {
        if (isError()) {
            log(message, args);
        }
    }

    public static void warning(final String message, final Object... args) {
        if (isWarning()) {
            log(message, args);
        }
    }

    public static void info(final String message, final Object... args) {
        if (isInfo()) {
            log(message, args);
        }
    }

    public static void debug(final String message, final Object... args) {
        if (isDebug()) {
            log(message, args);
        }
    }

    private static boolean levelHigherThan(final Level levelx) {
        return level.ordinal() >= levelx.ordinal();
    }

    private static void log(final String message, final Object... args) {
        System.out.printf(message + "\n", args);
    }
}
