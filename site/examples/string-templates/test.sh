#!/bin/sh

set -e

run.sh > actual.txt
diff -U1 expected.txt actual.txt
