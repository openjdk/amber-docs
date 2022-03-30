# Project Amber

The goal of Project Amber is to explore and incubate smaller,
productivity-oriented Java language features that have been accepted
as candidate JEPs in
the [OpenJDK JEP Process](https://openjdk.java.net/jeps/1). This
Project is sponsored by
the [Compiler Group](https://openjdk.java.net/groups/compiler).

Most Project Amber features go through at least two rounds
of [_preview_](https://openjdk.java.net/jeps/12) before becoming an
official part of the Java Platform.  For a given feature, there are separate
JEPs for each round of preview and for final standardization.  This
page links only to the most recent JEP for a feature. Such JEPs may
have links to earlier JEPs for the feature, as appropriate.

## Status of JEPs

Currently in progress:

  - [405: Record Patterns and Array Patterns (Preview)](https://openjdk.java.net/jeps/405)

<p>Delivered:</p>

  - [420: Pattern Matching for <code>switch</code> (Second Preview)](https://openjdk.java.net/jeps/420)
  - [409: Sealed Classes](https://openjdk.java.net/jeps/409)
  - [406: Pattern Matching for <code>switch</code> (Preview)](https://openjdk.java.net/jeps/406)
  - [395: Records](https://openjdk.java.net/jeps/395)
  - [394: Pattern Matching for <code>instanceof</code>](https://openjdk.java.net/jeps/394)
  - [378: Text Blocks](https://openjdk.java.net/jeps/378)
    - [Programmer's Guide](guides/text-blocks-guide)
  - [361: Switch Expressions](https://openjdk.java.net/jeps/361)
  - [323: Local-Variable Syntax for Lambda Parameters](https://openjdk.java.net/jeps/323)
  - [286: Local-Variable Type Inference (<code>var</code>)](https://openjdk.java.net/jeps/286)
    - [Style Guidelines](guides/lvti-style-guide)
    - [FAQ](guides/lvti-faq)

On hold:

 - [301: Enhanced Enums](https://openjdk.java.net/jeps/301) (see [here](https://mail.openjdk.java.net/pipermail/amber-spec-experts/2017-May/000041.html) for explanation)
 - [302: Lambda Leftovers](https://openjdk.java.net/jeps/302)
 - [348: Java Compiler Intrinsics for JDK APIs](https://openjdk.java.net/jeps/348)

Withdrawn:

 - [326: Raw String Literals](https://openjdk.java.net/jeps/326), dropped in favor of Text Blocks (see [here](https://mail.openjdk.java.net/pipermail/jdk-dev/2018-December/002402.html) for explanation)

## Documents

  - Guides
    - [Local Variable Type Inference Style Guide](guides/lvti-style-guide) (March 2018)
    - [Local Variable Type Inference FAQ](guides/lvti-faq) (Oct 2018)
    - [Programmer's Guide to Text Blocks](guides/text-blocks-guide) (Aug 2019)

  - Design notes
    - [Symbolic References for Constants](design-notes/constables) (March 2018)
    - [Data Classes and Sealed Types for Java](design-notes/records-and-sealed-classes) (February 2019)
    - [Towards Better Serialization](design-notes/towards-better-serialization) (June 2019)
    - Pattern matching
      - [Pattern Matching for Java](design-notes/patterns/pattern-matching-for-java) (September 2018)
      - [Pattern Matching in the Java Object Model](design-notes/patterns/pattern-match-object-model) (December 2020)
      - [Pattern Matching for Java -- Semantics](design-notes/patterns/pattern-match-semantics) (August 2020)
      - [Pattern Matching for Java -- Runtime and Translation](design-notes/patterns/pattern-match-translation) (June 2017)
      - [Extending `switch` for Pattern Matching](design-notes/patterns/extending-switch-for-patterns) (April 2017)
      - [Type Patterns in `switch`](design-notes/patterns/type-patterns-in-switch) (September 2020)
    - [String Tapas Redux: Beyond Mere String Interpolation](design-notes/templated-strings) (September 2021)

  - Historical notes
    - [Data Classes for Java](design-notes/data-classes-historical-1) (October 2017)
    - [Data Classes for Java](design-notes/data-classes-historical-2) (February 2018)

## Community

  - [Members](https://openjdk.java.net/census#amber)
  - Mailing Lists
    - [amber-dev](https://mail.openjdk.java.net/mailman/listinfo/amber-dev) --- For technical discussion related to Project Amber
    - [amber-spec-experts](https://mail.openjdk.java.net/mailman/listinfo/amber-spec-experts) --- For Expert Group members only
    - [amber-spec-observers](https://mail.openjdk.java.net/mailman/listinfo/amber-spec-observers) --- A read-only clone of amber-spec-experts
    - [amber-spec-comments](https://mail.openjdk.java.net/mailman/listinfo/amber-spec-comments) --- For submitting comments on the official specs
