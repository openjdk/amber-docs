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

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

public class SimpleJavac {
    /**
     * The java compiler tool.
     */
    private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

    /**
     * Name of main class being compiled.
     */
    private final String className;

    /**
     * Diagnostics from compilation.
     */
    private final DiagnosticCollector<JavaFileObject> diagnostics;

    /**
     * String source file.
     */
    private final SimpleJavacSource source;

    /**
     * Target temporary directory.
     */
    private final File directory;

    /**
     * Target temporary directory as URL.
     */
    private final URL directoryURL;

    /**
     * true if there are errors.
     */
    private boolean hasErrors;

    /**
     * true if there are warnings.
     */
    private boolean hasWarnings;

    /**
     * Compiled and loaded main class.
     */
    private Class<?> mainClass;

    /**
     * String source javac source.
     */
    private static class SimpleJavacSource extends SimpleJavaFileObject {
        /**
         * Source code.
         */
        final String code;


        /**
         * Constructor.
         *
         * @param name  artifical name of source
         * @param code  source code
         */
        SimpleJavacSource(String name, String code) {
            super(URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension),Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * Diagnostic from SimpleJavac.
     */
    private static final class SimpleJavacDiagnostic implements Diagnostic<SimpleJavacSource> {
        /**
         * String source javac source.
         */
        final SimpleJavacSource source;

        /**
         * Simple error message.
         */
        final String message;

        /**
         * Constructor.
         *
         * @param source   string source javac source
         * @param message  simple error message
         */
        SimpleJavacDiagnostic(SimpleJavacSource source, String message) {
            this.source = source;
            this.message = message;
        }

        @Override
        public Kind getKind() {
            return ERROR;
        }

        @Override
        public SimpleJavacSource getSource() {
            return source;
        }

        @Override
        public long getPosition() {
            return 0;
        }

        @Override
        public long getStartPosition() {
            return 0;
        }

        @Override
        public long getEndPosition() {
            return 0;
        }

        @Override
        public long getLineNumber() {
            return 0;
        }

        @Override
        public long getColumnNumber() {
            return 0;
        }

        @Override
        public String getCode() {
            return (String)source.getCharContent(true);
        }

        @Override
        public String getMessage(Locale locale) {
            return message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    /**
     * Issue an error message.
     *
     * @param message  simple error message.
     */
    private void error(String message) {
        SimpleJavacDiagnostic diagnostic = new SimpleJavacDiagnostic(source, message);
        diagnostics.report(diagnostic);
        hasErrors = true;
    }

    /**
     * Locate the main class name in the source code.
     *
     * @param code  source code
     *
     * @return  name of main class or null if not found.
     */
    private String findClassName(String code) {
        Matcher matcher = Pattern.compile("(class|interface|enum) (.\\w+)").matcher(code);

        if (matcher.find()) {
            return matcher.group(2);
        }

        return null;
    }

    /**
     * Check diagnostics for errors and warnings.
     */
    private void checkDiagnostics() {
        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            Diagnostic.Kind kind = diagnostic.getKind();

            if (kind == ERROR) {
                hasErrors = true;
            } else if (kind == WARNING) {
                hasWarnings = true;
            }
        }
    }

    /**
     * Compile the source code.
     */
    private void compile() {
        if (!hasErrors) {
            List<JavaFileObject> compilationUnits = Arrays.asList(source);
            List<String> options = List.of("-d", directory.toString());
            JavaCompiler.CompilationTask task = COMPILER.getTask(null, null, diagnostics, options, null, compilationUnits);

            if (!task.call()) {
                error("Compilation failed");
            }

            checkDiagnostics();

            if (!hasErrors) {
                try {
                    URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{directoryURL});
                    mainClass = Class.forName(className, true, classLoader);
                } catch (ClassNotFoundException e) {
                    error("Compilation failed");
                }

            }
        }
    }

    /**
     * Constructor.
     *
     * @param code  source code.
     */
    public SimpleJavac(String code) {
        this.className = findClassName(code);
        boolean goodName = this.className != null;
        this.source = new SimpleJavacSource(goodName ? className : "<error>", code);
        this.diagnostics = new DiagnosticCollector<JavaFileObject>();

        if (!goodName) {
            error("Can not locate class name");
        }

        try {
            this.directory = Files.createTempDirectory(Path.of("."), "").toFile();
            this.directoryURL = this.directory.toURI().toURL();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        compile();
    }

    /**
     * Delete temporary directory when finished with classes.
     */
    public void deleteDirectory() {
        for(String name : directory.list()) {
            new File(directory, name).delete();
        }
        directory.delete();
    }

    /**
     * Get main class name.
     *
     * @return  main class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Get main class.
     *
     * @return  main class
     */
    public Class<?> getMainClass() {
        return mainClass;
    }

    /**
     * Get all declared methods from main class.
     *
     * @return  declared methods from main class
     */
    public List<Method> getMethods() {
        if (mainClass != null) {
            return Arrays.asList(mainClass.getDeclaredMethods());
        }

        return List.of();
    }

    /**
     * Get all declared methods from main class as {@link MethodHandles}.
     *
     * @return  declared methods from main class as MethodHandles
     */
    public List<MethodHandle> getMethodHandles() {
        if (mainClass != null) {
            Method[] methods = mainClass.getDeclaredMethods();

            return Arrays.stream(methods)
                    .map(method -> {
                        try {
                            return MethodHandles.lookup().unreflect(method);
                        } catch (IllegalAccessException e) {
                            return null;
                        }
                    })
                    .filter(mh -> mh != null)
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    /**
     * Return true if there were errors.
     *
     * @return  true if there were errors
     */
    public boolean hasErrors() {
        return hasErrors;
    }

    /**
     * Return true if there were warnings.
     *
     * @return  true if there were warnings
     */
    public boolean hasWarnings() {
        return hasWarnings;
    }

    /**
     * Collect diagnostic messages as a string.
     *
     * @return  diagnostic messages as a string
     */
    public String getMessages() {
        StringBuilder sb = new StringBuilder();

        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            sb.append(diagnostic.toString());
            sb.append('\n');
        }

        return sb.toString();
    }
}
