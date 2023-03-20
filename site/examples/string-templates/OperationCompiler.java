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

import java.io.*;
import java.lang.invoke.*;
import java.util.*;

import static java.io.StreamTokenizer.TT_EOF;
import static java.io.StreamTokenizer.TT_WORD;

public class OperationCompiler {
    private static final MethodHandles.Lookup LOOKUP;
    private static final MethodHandle ADD_MH;
    private static final MethodHandle SUBTRACT_MH;
    private static final MethodHandle MULTIPLY_MH;
    private static final MethodHandle DIVIDE_MH;
    private static final Map<String, Operator> OPERATORS;

    record Operator(int precedence, boolean isLeft, MethodHandle mh) {
    }

    static {
        LOOKUP = MethodHandles.publicLookup();
        MethodHandle add_mh = null;
        MethodHandle subtract_mh = null;
        MethodHandle multiply_mh = null;
        MethodHandle divide_mh = null;

        try {
            add_mh = LOOKUP.findVirtual(Operations.class, "add",
                    MethodType.methodType(Object.class, Object.class));
            subtract_mh = LOOKUP.findVirtual(Operations.class, "subtract",
                    MethodType.methodType(Object.class, Object.class));
            multiply_mh = LOOKUP.findVirtual(Operations.class, "multiply",
                    MethodType.methodType(Object.class, Object.class));
            divide_mh = LOOKUP.findVirtual(Operations.class, "divide",
                    MethodType.methodType(Object.class, Object.class));
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }

        MethodType type = MethodType.methodType(Operations.class, Operations.class, Operations.class);

        ADD_MH = add_mh.asType(type);
        SUBTRACT_MH = subtract_mh.asType(type);
        MULTIPLY_MH = multiply_mh.asType(type);
        DIVIDE_MH = divide_mh.asType(type);

        OPERATORS = Map.of(
                "+", new Operator(100, false, ADD_MH),
                "-", new Operator(100, false, SUBTRACT_MH),
                "*", new Operator(200, false, MULTIPLY_MH),
                "/", new Operator(200, false, DIVIDE_MH)
        );
    }

    private final StreamTokenizer tokenizer;
    private int token;

    public OperationCompiler(String statement) {
        this.tokenizer = new StreamTokenizer(new StringReader(statement));
        this.tokenizer.slashSlashComments(true);
        this.tokenizer.eolIsSignificant(false);
        this.tokenizer.ordinaryChar('\uFFFC');
        this.token = 0;
        next();
    }

    private void next() {
        if (token != TT_EOF) {
            try {
                token = tokenizer.nextToken();
            } catch (IOException ex) {
                token = TT_EOF;
            }
        }
    }

    private Operator operator() {
        return OPERATORS.get(token < 0 ? "" : String.valueOf((char)token));
    }

    private int precedence() {
        if (token < 0) {
            return -1;
        } else {
            Operator operator = operator();

            return operator == null ? -1 : operator.precedence();
        }
    }

    private boolean isLeft() {
        if (token < 0) {
            return false;
        } else {
            Operator operator = operator();

            return operator == null ? false : operator.isLeft();
        }
    }

    public MethodHandle compile() {
        return expression();
    }

    private MethodHandle term() {
        if (token == TT_WORD &&
                tokenizer.sval.equals("\uFFFC")) {
            next();

            return MethodHandles.identity(Operations.class);
        } else if (token == '(') {
            next();
            MethodHandle mh = expression();

            if (token == ')') {
                next();
            }

            return mh;
        }

        throw new RuntimeException("unexpected token");
    }

    private MethodHandle expression() {
        return expression(term(), 0);
    }

    private MethodHandle expression(MethodHandle lhs, int minPrecedence) {
        Operator operator = operator();

        if (operator == null) {
            throw new RuntimeException("unexpected token");
        }

        int precedence = operator.precedence();

        while (precedence >= minPrecedence) {
            next();
            MethodHandle rhs = term();
            int nextPrecedence = precedence();

            while (nextPrecedence > precedence || nextPrecedence == precedence && !isLeft()) {
                rhs = expression(rhs, nextPrecedence);
                nextPrecedence = precedence();
            }

            MethodHandle operatorMH = operator.mh();
            operatorMH = MethodHandles.collectArguments(operatorMH, 1, rhs);
            lhs = MethodHandles.collectArguments(operatorMH, 0, lhs);

            precedence = nextPrecedence;
        }

        return lhs;
    }

}
