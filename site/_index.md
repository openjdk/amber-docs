# Project Amber

The goal of Project Amber is to explore and incubate smaller,
productivity-oriented Java language features that have been accepted
as candidate JEPs under
the [OpenJDK JEP Process](http://openjdk.java.net/jeps/1). This
Project is sponsored by
the [Compiler Group](http://openjdk.java.net/groups/compiler).

Most Project Amber features go through at least two rounds
of [_Preview_](http://openjdk.java.net/jeps/12) before becoming an
official part of Java SE.  For a given feature, there are separate
JEPs for each round of preview and for final standardization.  This
page links only to the most recent JEP for a feature. Such JEPs may
have links to earlier JEPs for the feature, as appropriate.

## Status of JEPs

Currently in progress:

  - [409: Sealed Classes](http://openjdk.java.net/jeps/409)
  - [406: Pattern Matching for <code>switch</code> (Preview)](http://openjdk.java.net/jeps/406)
  - [405: Record Patterns and Array Patterns (Preview)](http://openjdk.java.net/jeps/405)
</ul>

<p>Delivered:</p>

  - [395: Records](http://openjdk.java.net/jeps/395)
  - [394: Pattern Matching for <code>instanceof</code>](http://openjdk.java.net/jeps/394)
  - [378: Text Blocks](http://openjdk.java.net/jeps/378)
    - [Style Guidelines](guides/lvti-style-guide)
    - [FAQ](guides/lvti-faq)
  - [361: Switch Expressions](http://openjdk.java.net/jeps/361)
  - [323: Local-Variable Syntax for Lambda Parameters](http://openjdk.java.net/jeps/323)
  - [286: Local-Variable Type Inference (<code>var</code>)](http://openjdk.java.net/jeps/286)

On hold:

 - [301: Enhanced Enums](http://openjdk.java.net/jeps/301) (see [here](http://mail.openjdk.java.net/pipermail/amber-spec-experts/2017-May/000041.html) for explanation)
 - [302: Lambda Leftovers](http://openjdk.java.net/jeps/302)
 - [348: Java Compiler Intrinsics for JDK APIs](http://openjdk.java.net/jeps/348)

Withdrawn:

 - [326: Raw String Literals](http://openjdk.java.net/jeps/326), dropped in favor of Text Blocks (see [here](https://mail.openjdk.java.net/pipermail/jdk-dev/2018-December/002402.html) for explanation)

## Documents

 - [Local Variable Type Inference Style Guide](guides/lvti-style-guide) (March 2018)
 - [Local Variable Type Inference FAQ](guides/lvti-faq) (Oct 2018)
 - [Programmer's Guide to Text Blocks](guides/text-blocks-guide) (Aug 2019)
 - [Pattern Matching for Java](design-notes/patterns/pattern-matching-for-java) (Sept 2018)
 - [Pattern Matching for Java -- Semantics](design-notes/patterns/pattern-match-semantics) (Sept 2018)
 - [Data Classes and Sealed Types for Java](design-notes/records-and-sealed-classes) (Feb 2019)
 - [Towards Better Serialization](design-notes/towards-better-serialization) (June 2019)

## Community

  - [Members](http://openjdk.java.net/census#amber)
  - Mailing Lists
    - [amber-dev](http://mail.openjdk.java.net/mailman/listinfo/amber-dev) -- For technical discussion related to Project Amber
    - [amber-spec-experts](http://mail.openjdk.java.net/mailman/listinfo/amber-spec-experts) -- For Expert Group members only
    - [amber-spec-observers](http://mail.openjdk.java.net/mailman/listinfo/amber-spec-observers) -- A read-only clone of amber-spec-experts
    - [amber-spec-comments](http://mail.openjdk.java.net/mailman/listinfo/amber-spec-comments) -- For submitting comments on the official specs
