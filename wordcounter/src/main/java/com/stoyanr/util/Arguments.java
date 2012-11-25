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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class Arguments {
    public static final String SCHEMA_DELIM = ",";
    public static final char TYPE_CHAR_BOOLEAN = '!';
    public static final char TYPE_CHAR_STRING = '*';
    public static final char TYPE_CHAR_INTEGER = '#';
    public static final String HYPHENS = "-/";

    private final transient Map<String, Class<?>> types = new HashMap<String, Class<?>>();
    private final transient Map<String, Object> values = new HashMap<String, Object>();

    public Arguments(final String schema, final String[] args) {
        assert (schema != null && !schema.isEmpty());
        parseSchema(schema);
        parseArgs(args);
    }

    private void parseSchema(final String schema) {
        final StringTokenizer tokenizer = new StringTokenizer(schema, SCHEMA_DELIM);
        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            if (!token.isEmpty()) {
                parseToken(token);
            }
        }
    }

    private void parseToken(final String token) {
        final int lastIndex = token.length() - 1;
        final char typeChar = token.charAt(lastIndex);
        final String argName = token.substring(0, lastIndex);
        putType(typeChar, argName);
    }

    private void putType(final char typeChar, final String argName) {
        if (typeChar == TYPE_CHAR_BOOLEAN) {
            types.put(argName, Boolean.class);
        } else if (typeChar == TYPE_CHAR_STRING) {
            types.put(argName, String.class);
        } else if (typeChar == TYPE_CHAR_INTEGER) {
            types.put(argName, Integer.class);
        }
    }

    private void parseArgs(final String[] args) {
        final List<String> argsList = Arrays.asList(args);
        final Iterator<String> iter = argsList.iterator();
        while (iter.hasNext()) {
            parseArg(iter);
        }
    }

    private void parseArg(final Iterator<String> iter) {
        assert (iter != null && iter.hasNext());
        final String name = getArgName(iter);
        final Class<?> type = getArgType(name);
        putValue(iter, name, type);
    }

    private void putValue(final Iterator<String> iter, final String name, final Class<?> type) {
        if (type.equals(Boolean.class)) {
            values.put(name, true);
        } else if (type.equals(String.class)) {
            values.put(name, getArgValue(iter));
        } else if (type.equals(Integer.class)) {
            values.put(name, parseInt(getArgValue(iter)));
        }
    }

    private String getArgName(final Iterator<String> iter) {
        final String arg = iter.next();
        if (!isValidHyphenedArg(arg)) {
            throw new ArgumentsException();
        }
        return arg.substring(1);
    }

    private Class<?> getArgType(final String name) {
        final Class<?> type = types.get(name);
        if (type == null) {
            throw new ArgumentsException();
        }
        return type;
    }

    private String getArgValue(final Iterator<String> iter) {
        if (!iter.hasNext()) {
            throw new ArgumentsException();
        }
        final String value = iter.next();
        assert (value != null && !value.isEmpty());
        return value;
    }

    private int parseInt(final String value) {
        int result = 0;
        try {
            result = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ArgumentsException(String.format("Can't parse %s to a number: %s", value, 
                e.getMessage()), e);
        }
        return result;
    }

    private boolean isValidHyphenedArg(final String arg) {
        return (arg.length() > 1) && (HYPHENS.indexOf(arg.charAt(0)) != -1);
    }

    public final boolean getBoolean(final String name, final boolean defaultValue) {
        return (Boolean) get(name, Boolean.valueOf(defaultValue), Boolean.class);
    }

    public final String getString(final String name, final String defaultValue) {
        return (String) get(name, defaultValue, String.class);
    }

    public final int getInt(final String name, final int defaultValue) {
        return (Integer) get(name, Integer.valueOf(defaultValue), Integer.class);
    }

    private Object get(final String name, final Object defaultValue, final Class<?> type) {
        assert (name != null && !name.isEmpty());
        final Object value = getValue(name, type);
        return (value == null) ? defaultValue : value;
    }

    private Object getValue(final String name, final Class<?> type) {
        checkName(name, type);
        final Object value = values.get(name);
        if (value != null) {
            assert value.getClass().equals(type);
        }
        return value;
    }

    private void checkName(final String name, final Class<?> type) {
        final Class<?> typex = getArgType(name);
        if (!typex.equals(type)) {
            throw new ArgumentsException();
        }
    }

}
