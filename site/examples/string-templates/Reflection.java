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

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.StringTemplate.SimpleProcessor;
import java.util.*;

public class Reflection {
    private static final Lookup LOOKUP = MethodHandles.lookup();


    static final SimpleProcessor<MethodHandle>
        MH = st -> {
            String string = st.interpolate();
            MethodHandle mh = null;
            SimpleJavac javac = new SimpleJavac("public class XYZ {" + string + "}");
            if (!javac.hasErrors()) {
                mh = javac.getMethodHandles().get(0);
            }
            javac.deleteDirectory();
            if (mh == null) {
                System.err.print(javac.getMessages());
                System.exit(1);
            }
            return mh;
        };

    static final SimpleProcessor<Class<?>> CLASS = st -> {
        String string = st.interpolate();
        Class<?> cls = null;
        SimpleJavac javac = new SimpleJavac(string);
        if (!javac.hasErrors()) {
            cls = javac.getMainClass();
        }
        javac.deleteDirectory();
        if (cls == null) {
            System.err.print(javac.getMessages());
            System.exit(1);
        }
        return cls;
    };

    public static void main(String args[]) throws Throwable {
        String name = "HelloWorld";
        String message = "I'd like to take a moment…";
        Class<?> cls = CLASS."""
           public class \{name} {
               public static void main(String args[]) {
                   System.out.println("\{message}");
               }
           }
        """;
        System.out.println(cls.getName());
        MethodHandle mh = LOOKUP.findStatic(cls, "main", MethodType.methodType(void.class, String[].class));
        mh.invoke(new String[0]);

        MH."""
           public static void main(String args[]) {
               System.out.println("This is in another java file");
           }
           """.invoke(new String[0]);
    }
}
