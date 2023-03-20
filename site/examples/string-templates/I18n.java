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
import java.util.*;
import java.text.MessageFormat;
import java.util.stream.*;

public class I18n {
    record GetLocalizedMessage(Locale locale) implements StringProcessor {
        @Override
        public String process(StringTemplate templatedString) {
            ResourceBundle resource = ResourceBundle.getBundle("resources", locale);
			String stencil = String.join("\uFFFC", templatedString.fragments());
            String msgFormat = resource.getString(stencil.replace(' ', '.'));
            return MessageFormat.format(msgFormat, templatedString.values().toArray());
        }
    };

    public static void main(String... args) {
        List<Locale> locales =
                List.of(Locale.of("en", "CA"), Locale.of("zh", "CN"), Locale.of("jp"));
        String symbolKind = "VAR";
        String name = "amount";
        String type = "double";

        for (Locale locale : locales) {
            var glm = new GetLocalizedMessage(locale);
            System.out.println(glm."no suitable \{symbolKind} found for \{name}(\{type})");
        }
    }
}
