/*
 * Copyright 2020 Advanced Software Technologies Lab at ETH Zurich, Switzerland
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
package com.android.commands.monkey.ape.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class RandomHelper {

    public static Random getRandom() {
        return ThreadLocalRandom.current();
    }
    
    public static int nextInt() {
        return getRandom().nextInt();
    }

    public static int nextInt(int b) {
        return getRandom().nextInt(b);
    }

    public static boolean nextBoolean() {
        return getRandom().nextBoolean();
    }

    public static <V extends PriorityObject> V randomPickWithPriority(List<V> list) {
        if (list.isEmpty()) {
            return null;
        }
        int count = 0;
        for (V o : list) {
            if (o.getPriority() <= 0) {
                throw new IllegalStateException("Object should have a positive priority for random pick.");
            }
            count += o.getPriority();
        }
        int index = nextInt(count);
        count = 0;
        for (V o : list) {
            if (o.getPriority() <= 0) {
                throw new IllegalStateException("Object should have a positive priority for random pick.");
            }
            count += o.getPriority();
            if (count > index) {
                return o;
            }
        }
        throw new IllegalStateException("Should not reach here");
    }

    public static <V> V randomPick(List<V> list) {
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        int index = nextInt(list.size());
        return list.get(index);
    }

    public static int nextBetween(int minValue, int maxValue) {
        if (maxValue <= minValue) {
            throw new IllegalArgumentException("max is not greater than min.");
        }
        return nextInt(maxValue - minValue) + minValue;
    }

    static final String CHARS = " abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+~-=`:\";'{}[]|\\'<>,.?/";
    static final String DIGITS = "0123456789";

    public static String nextString() {
        int stringType = RandomHelper.nextInt(4);
        switch (stringType) {
            case 0:
                return nextIntegerString(32);
            case 1:
                return nextFloatString(24, 8);
            case 2:
                return nextDateString();
        }
        return nextString(32);
    }

    static String[] dateFormats = new String[] {
            "yyyy.MM.dd",
            "HH:mm:ss",
            "yyy-MM-dd",
            "yyyy.MM.dd HH:mm:ss",
    };

    static <E> E next(E[] array) {
        return array[nextInt(array.length)];
    }

    private static String nextDateString() {
        long timestamp = nextLong();
        Date date = new Date(timestamp);
        return new SimpleDateFormat(next(dateFormats)).format(date);
    }

    public static String nextIntegerString(int maxLength) {
        return nextIntegerString(maxLength, true);
    }

    public static String nextIntegerString(int maxLength, boolean includeMinus) {
        int total = nextInt(maxLength); // total may be zero
        char[] value = new char[total];
        int charTotal = DIGITS.length();
        int i = 0;
        if (i < total && includeMinus && toss(0.5)) {
            value[i++] = toss(0.5) ? '+' : '-';
        }
        for (; i < total; i++) {
            value[i] = DIGITS.charAt(nextInt(charTotal));
        }
        return String.valueOf(value);
    }

    public static String nextFloatString(int maxLength, int maxLength2) {
        return nextIntegerString(maxLength) + "." + nextIntegerString(maxLength2, false);
    }

    public static String nextFormattedString() {
        int stringType = RandomHelper.nextInt(3);
        switch (stringType) {
            case 0:
                return nextDateString();
            case 1:
                return nextFloatString(24, 8);
            case 2:
                return nextIntegerString(32);
        }
        return nextIntegerString(32);
    }

    public static String nextString(int maxLength) {
        int total = nextInt(maxLength);
        char[] value = new char[total];
        int charTotal = CHARS.length();
        for (int i = 0; i < total; i++) {
            value[i] = CHARS.charAt(nextInt(charTotal));
        }
        return String.valueOf(value);
    }

    public static int nextByte() {
        return nextBetween(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    public static int nextShort() {
        return nextBetween(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    public static double nextDouble() {
        return getRandom().nextDouble();
    }

    public static Long nextLong() {
        return getRandom().nextLong();
    }

    public static Float nextFloat() {
        return getRandom().nextFloat();
    }

    public static boolean toss(double d) {
        return nextDouble() < d;
    }
}
