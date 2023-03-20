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
import java.lang.reflect.Field;
import java.util.*;

public class Mustache {
    public static class MapMustache implements StringProcessor {
        private final Map<String, ? extends Object> map;

        public MapMustache(Map<String, ? extends Object> map) {
            Objects.requireNonNull(map, "map must not be null");
            this.map = map;
        }

        @Override
        public String process(StringTemplate st) {
            StringBuilder sb = new StringBuilder();
            Iterator<String> segmentsIter = st.fragments().iterator();

            for (Object value : st.values()) {
                sb.append(segmentsIter.next());

                if (value instanceof String s && map.containsKey(s)) {
                    value = map.get(s);
                 }

                sb.append(value);
            }

            sb.append(segmentsIter.next());

            return sb.toString();
        }
    }

    public static class RecMustache extends MapMustache {
        public RecMustache(Record object) {
            super(buildMap(object));
        }

        private static Map<String, Object> buildMap(Record record) {
            Objects.requireNonNull(record, "record must not be null");
            Map<String, Object> map = new HashMap<>();

            for (Field field : record.getClass().getDeclaredFields()) {
                try {
                    String name = field.getName();
                    Object value = field.get(record);
                    map.put(name, value);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }

            return map;
        }
    }

    record Person(String name, String phone, String address) {}

    public static void main(String... args) {
        var binding = Map.of(
                "name", "Joan Smith",
                "phone", "555-123-4567",
                "address", "1 Maple Drive, Anytown"
                );
        MapMustache mapMustache = new MapMustache(binding);
        System.out.println(mapMustache."""
            {
                "name":    "\{"name"}",
                "phone":   "\{"phone"}",
                "address": "\{"address"}"
            }
            """);

        Person person = new Person("Joan Smith", "555-123-4567", "1 Maple Drive, Anytown");

        RecMustache recMustache = new RecMustache(person);
        System.out.println(recMustache."""
            {
                "name":    "\{"name"}",
                "phone":   "\{"phone"}",
                "address": "\{"address"}"
            }
            """);
    }
}
