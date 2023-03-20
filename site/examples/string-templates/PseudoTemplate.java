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
import java.util.*;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.*;

public class PseudoTemplate {
    record TemplateBuilder() implements SimpleProcessor<Template> {
        @Override
        @SuppressWarnings("unchecked")
        public Template process(StringTemplate templatedString) {
            String template = templatedString.fragments()
            	.stream()
            	.collect(Collectors.joining("\\E(.*)\\Q", "\\Q", "\\E"));
            Pattern pattern = Pattern.compile(template);
            List<Consumer<String>> consumers = templatedString
                    .values()
                    .stream()
                    .map (value -> (Consumer<String>)value)
                    .toList();

            return new Template(pattern, consumers);
        }
    }

    record Template(Pattern pattern, List<Consumer<String>> consumers) {
        void match(String string) {
            Matcher matcher = pattern.matcher(string);

            if (matcher.find()) {
                int i = 0;
                for (Consumer<String> consumer : consumers) {
                    consumer.accept(matcher.group(++i));
                }
            } else {
                for (Consumer<String> consumer : consumers) {
                    consumer.accept("");
                }
            }
        }
    }

    static TemplateBuilder TEMPLATE_BUILDER = new TemplateBuilder();

    static public class Info {
        String name, phone, address;

        @Override
        public String toString() {
            return STR."""
            Name:    "\{name}"
            Phone:   "\{phone}"
            Address: "\{address}"
            """;
        }
    }

    public static void main(String... args) {
        Info info = new Info();
        Consumer<String> name = n -> info.name = n;
        Consumer<String> phone = p -> info.phone = p;
        Consumer<String> address = a -> info.address = a;

        Template template = TEMPLATE_BUILDER."Name: \{name}, Phone: \{phone}, Address: \{address}";
        template.match("Name: Joan J. Smith, Phone: 555-123-4567, Address: 1 Maple Dr., Anytown, SomeCountry");
        System.out.println(info);
        template.match("Name: Smitty S. Jones, Phone: 555-987-6543, Address: 2 Oak Dr., Anytown, SomeCountry");
        System.out.println(info);
    }
}
