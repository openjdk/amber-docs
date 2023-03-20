/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.lang.StringTemplate.SimpleProcessor;

import static java.lang.StringTemplate.RAW;

public class AsTemplatedString {
    static final SimpleProcessor<StringTemplate> NOOP =
        new SimpleProcessor<>() {
            @Override
            public StringTemplate process(StringTemplate templatedString) {
                return templatedString;
            }
        };

    static SimpleProcessor<StringTemplate> noop() {
        return NOOP;
    }

    public static void main(String... args) {
        byte a = 10;
        byte b = 20;

        StringTemplate i0 = NOOP."The answer is \{a} + \{b} = \{a + b}";
        StringTemplate i1 = noop()."The answer is \{a} + \{b} = \{a + b}";
        String i2 = RAW."The answer is \{a} + \{b} = \{a + b}".interpolate();
        String i3 = STR."The answer is \{a} + \{b} = \{a + b}";
        Object i4 = RAW."The answer is \{a} + \{b} = \{a + b}";
        System.out.println(i0.getClass());
        System.out.println(i1.getClass());
        System.out.println(i2.getClass());
        System.out.println(i3.getClass());
        System.out.println(i4.getClass());
    }
}
