#!/bin/sh

set -e

rm -rf `find . -name '*.class'`

javac --enable-preview -source 21 *.java
mkdir -p example
mv Mailer*.class example

java --enable-preview AsTemplatedString
java --enable-preview Basic
java --enable-preview ComplexExpressions
java --enable-preview Processor
java --enable-preview Concat
java --enable-preview Format
java --enable-preview Formatting
java --enable-preview I18n
java --enable-preview JSONExample
java --enable-preview Log
java --enable-preview example.Mailer
java --enable-preview Mustache
java --enable-preview Null
java --enable-preview Mapping
java --enable-preview PseudoTemplate
java --enable-preview Reflection
java --enable-preview Simple

rm -rf `find . -name '*.class'`
