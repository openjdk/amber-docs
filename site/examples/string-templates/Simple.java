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

import java.lang.StringTemplate.Processor;
import java.util.*;
import java.util.function.*;

class Simple {
	static class MyProcessor implements Processor<String, IllegalArgumentException> {
		@Override
		public String process(StringTemplate templatedString) throws IllegalArgumentException {
            StringBuilder sb = new StringBuilder();
            List<String> fragments = templatedString.fragments();
            List<Object> values = templatedString.values();
            Iterator<Object> iter = values.iterator();

            for (String fragment : fragments) {
                sb.append(fragment);

                if (iter.hasNext()) {
                    Object value = iter.next();

                    if (value instanceof Supplier supplier) {
                        value = supplier.get();
                    } else if (value instanceof Boolean) {
                        throw new IllegalArgumentException("I don't like Booleans");
                    }

                    sb.append(value);
                }
            }

            return sb.toString();
		}
	}

    public static void main(String... args) {
    	final MyProcessor SP = new MyProcessor();
    	Supplier<String> thing = () -> "answer";
        int a = 10;
        int b = 20;
        System.out.println(SP."The \{thing} is \{a} + \{b} = \{a + b}");
    }

}
