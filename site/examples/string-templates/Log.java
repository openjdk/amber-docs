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

import java.lang.StringTemplate.StringProcessor;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Log {
    static boolean TRACING = true;

    static StringProcessor TRACE = st -> {
             if (TRACING) {
                StringBuilder sb = new StringBuilder();
                Iterator<String> fragmentsIter = st.fragments().iterator();

                for (Object value : st.values()) {
                    sb.append(fragmentsIter.next());

                    if (value instanceof Future future) {
                        if (future instanceof FutureTask task) {
                            task.run();
                        }

                        try {
                            sb.append(future.get());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (value instanceof Supplier<?> supplier) {
                        sb.append(supplier.get());
                    } else {
                        sb.append(value);
                    }
                }

                sb.append(fragmentsIter.next());
                String log = sb.toString();
                System.out.println(log);

                return log;
            }

            return "";
    };

    static String grab(String content, String prefix, String suffix) {
    	if (content != null) {
			int start = content.indexOf(prefix);
			int end = content.indexOf(suffix, start);

			if (start != -1 && end != -1) {
				return content.substring(start + prefix.length(), end);
			}
        }

        return "";
    }

    static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final Supplier<String> TIME_STAMP = () -> LocalDateTime.now().toLocalTime().format(HH_MM_SS);

    static final String SITE = "http://rss.cnn.com/rss/cnn_tech.rss";

    static final FutureTask<String> LONG_PROCESS = new FutureTask<>(() -> {
        URL url = new URL(SITE);
        URLConnection conn = url.openConnection();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            String item = grab(content, "<item>", "</item>");
            return grab(item, "<![CDATA[", "]]>");
        } catch (FileNotFoundException ex) {
            return SITE + " is offline";
        } catch (IOException ex) {
            return SITE + " is offline";
        }
    });

    public static void main(String... args) throws Throwable {
        TRACE."\{TIME_STAMP}: CNN Tech - \{LONG_PROCESS}";
        Thread.sleep(1000);
        TRACE."\{TIME_STAMP}: CNN Tech - \{LONG_PROCESS}";
        Thread.sleep(1000);
        TRACE."\{TIME_STAMP}: CNN Tech - \{LONG_PROCESS}";
        TRACING = false;
        TRACE."\{TIME_STAMP}: CNN Tech - \{LONG_PROCESS}";
    }
}
