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

import java.util.*;

public final class Complex implements Operations<Complex> {
    final double real;
    final double imaginary;

    public Complex(double real, double imaginary) {
        this.real = real;
        this.imaginary = imaginary;
    }

    public double real() {
        return real;
    }

    public double imaginary() {
        return imaginary;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Complex complex) {
            return Double.compare(complex.real, real) == 0 &&
                   Double.compare(complex.imaginary, imaginary) == 0;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(real) ^ Double.hashCode(imaginary);
    }

    @Override
    public String toString() {
        return imaginary < 0.0 ? STR."(\{real}\{imaginary}i)" :
                                 STR."(\{real}+\{imaginary}i)";
    }

    @Override
    public Complex add(Complex y) {
        Objects.requireNonNull(y);

        return new Complex(real + y.real, imaginary + y.imaginary);
    }

    @Override
    public Complex subtract(Complex y) {
        Objects.requireNonNull(y);

        return new Complex(real - y.real, imaginary - y.imaginary);
    }

    @Override
    public Complex multiply(Complex y) {
        Objects.requireNonNull(y);

        return new Complex(real * y.real - imaginary * y.imaginary,
                       real * y.imaginary + imaginary * y.real);
    }

    public Complex reciprocal() {
        double scale = real * real + imaginary * imaginary;

        return new Complex(real / scale, -imaginary / scale);
    }

    @Override
    public Complex divide(Complex y) {
        Objects.requireNonNull(y);

        return multiply(y.reciprocal());
    }
}
