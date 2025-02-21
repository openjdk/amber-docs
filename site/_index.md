# Project Amber

The goal of Project Amber is to explore and incubate smaller,
productivity-oriented Java language features that have been accepted
as candidate JEPs in
the [OpenJDK JEP Process](https://openjdk.org/jeps/1). This
Project is sponsored by
the [Compiler Group](https://openjdk.org/groups/compiler).

Most Project Amber features go through at least two rounds
of [_preview_](https://openjdk.org/jeps/12) before becoming an
official part of the Java Platform.  For a given feature, there are separate
JEPs for each round of preview and for final standardization.
<!--
This page links only to the most recent JEP for a feature. Such JEPs may
have links to earlier JEPs for the feature, as appropriate.
-->

## Status of JEPs

Currently in progress:

-   [468: Derived Record Creation (Preview)](https://openjdk.org/jeps/468)

Currently in preview:

-   [495: Simple Source Files and Instance Main Methods (Fourth Preview)](https://openjdk.org/jeps/495) (previous: [445](https://openjdk.org/jeps/445), [463](https://openjdk.org/jeps/463), [477](https://openjdk.org/jeps/477))
-   [494: Module Import Declarations (Second Preview)](https://openjdk.org/jeps/494) (previous: [476](https://openjdk.org/jeps/476))
-   [492: Flexible Constructor Bodies (Third Preview)](https://openjdk.org/jeps/492) (previous: [447](https://openjdk.org/jeps/447), [482](https://openjdk.org/jeps/482))
-   [488: Primitive Types in Patterns, <code>instanceof</code>, and <code>switch</code> (Second Preview)](https://openjdk.org/jeps/488) (previous: [455](https://openjdk.org/jeps/455))

Delivered:

-   [458: Launch Multi-File Source-Code Programs](https://openjdk.org/jeps/458)
-   [456: Unnamed Variables and Patterns](https://openjdk.org/jeps/456) (preview: [433](https://openjdk.org/jeps/433))
-   [441: Pattern Matching for <code>switch</code>](https://openjdk.org/jeps/441) (previews: [406](https://openjdk.org/jeps/406), [420](https://openjdk.org/jeps/420), [427](https://openjdk.org/jeps/427), [433](https://openjdk.org/jeps/433))
-   [440: Record Patterns](https://openjdk.org/jeps/440) (previews: [405](https://openjdk.org/jeps/405), [432](https://openjdk.org/jeps/432))
-   [409: Sealed Classes](https://openjdk.org/jeps/409) (previews: [360](https://openjdk.org/jeps/360), [397](https://openjdk.org/jeps/397))
-   [395: Records](https://openjdk.org/jeps/395) (previews: [359](https://openjdk.org/jeps/359), [384](https://openjdk.org/jeps/384))
-   [394: Pattern Matching for <code>instanceof</code>](https://openjdk.org/jeps/394) (previews: [305](https://openjdk.org/jeps/305), [375](https://openjdk.org/jeps/375))
-   [378: Text Blocks](https://openjdk.org/jeps/378) (previews: [355](https://openjdk.org/jeps/355), [368](https://openjdk.org/jeps/368))
    -   [Programmer's Guide](guides/text-blocks-guide)
-   [361: Switch Expressions](https://openjdk.org/jeps/361) (previews: [325](https://openjdk.org/jeps/325), [354](https://openjdk.org/jeps/354))
-   [323: Local-Variable Syntax for Lambda Parameters](https://openjdk.org/jeps/323)
-   [286: Local-Variable Type Inference (<code>var</code>)](https://openjdk.org/jeps/286)
    -   [Style Guidelines](guides/lvti-style-guide)
    -   [FAQ](guides/lvti-faq)

On hold:

-   [301: Enhanced Enums](https://openjdk.org/jeps/301) (see [here](https://mail.openjdk.org/pipermail/amber-spec-experts/2017-May/000041.html) for explanation)
-   [302: Lambda Leftovers](https://openjdk.org/jeps/302)
-   [348: Java Compiler Intrinsics for JDK APIs](https://openjdk.org/jeps/348)

Withdrawn:

-   [465: String Templates (Third Preview)](https://openjdk.org/jeps/465) (see [here](https://mail.openjdk.org/pipermail/amber-spec-experts/2024-April/004106.html) for explanation)
-   [326: Raw String Literals](https://openjdk.org/jeps/326), dropped in favor of Text Blocks (see [here](https://mail.openjdk.org/pipermail/jdk-dev/2018-December/002402.html) for explanation)

## Documents

-   Guides
    -   [Programmer's Guide to Text Blocks](guides/text-blocks-guide) (Aug 2019)
    -   [Local Variable Type Inference FAQ](guides/lvti-faq) (Oct 2018)  
    - [Local Variable Type Inference Style Guide](guides/lvti-style-guide) (March 2018)

-   Design notes
    -   [String Tapas Redux: Beyond Mere String Interpolation](design-notes/templated-strings) (September 2021)
    -   [Towards Better Serialization](design-notes/towards-better-serialization) (June 2019) 
    -   [Data Classes and Sealed Types for Java](design-notes/records-and-sealed-classes) (February 2019)
    -   [Symbolic References for Constants](design-notes/constables) (March 2018)
    -   Pattern matching
        -   [Towards Member Patterns](design-notes/patterns/towards-member-patterns) (January 2024)
        -   [Patterns: Exhaustiveness, Unconditionality, and Remainder](design-notes/patterns/exhaustiveness) (May 2023)
        -   [Pattern Matching in the Java Object Model](design-notes/patterns/pattern-match-object-model) (December 2020)
        -   [Type Patterns in `switch`](design-notes/patterns/type-patterns-in-switch) (September 2020)
        -   [Pattern Matching for Java -- Semantics](design-notes/patterns/pattern-match-semantics) (August 2020)
        -   [Pattern Matching for Java](design-notes/patterns/pattern-matching-for-java) (September 2018)
        -   [Pattern Matching for Java -- Runtime and Translation](design-notes/patterns/pattern-match-translation) (June 2017)
        -   [Extending `switch` for Pattern Matching](design-notes/patterns/extending-switch-for-patterns) (April 2017)

-   Historical notes
    -   [Data Classes for Java](design-notes/data-classes-historical-2) (February 2018)
    -   [Data Classes for Java](design-notes/data-classes-historical-1) (October 2017)
    

## Community

-   [Members](https://openjdk.org/census#amber)
-   Mailing Lists
    -   [amber-dev](https://mail.openjdk.org/mailman/listinfo/amber-dev) --- For technical discussion related to Project Amber
    -   [amber-spec-experts](https://mail.openjdk.org/mailman/listinfo/amber-spec-experts) --- For Expert Group members only
    -   [amber-spec-observers](https://mail.openjdk.org/mailman/listinfo/amber-spec-observers) --- A read-only clone of amber-spec-experts
    -   [amber-spec-comments](https://mail.openjdk.org/mailman/listinfo/amber-spec-comments) --- For submitting comments on the official specs
-   Other Resources
    -   [Inside.java](https://inside.java/tag/amber)
    -   [Dev.java](https://dev.java/learn/)
