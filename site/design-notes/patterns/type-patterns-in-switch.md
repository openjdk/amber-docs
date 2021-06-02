# Type patterns in switch
#### Brian Goetz, Sept 2020 {.author}

This document describes a possible approach for the next phase of  _pattern
matching_ -- adding type patterns to the `switch` statement.  This builds on the
work of [JEP 375][jep375].  _This is an exploratory document only and does not
constitute a plan for any specific feature in any specific version of the Java
Language._

### Pattern matching documents

- [Pattern Matching For Java](pattern-matching-for-java).  Overview of
  pattern matching concepts, and how they might be surfaced in Java.
- [Pattern Matching For Java -- Semantics](pattern-match-semantics).  More
  detailed notes on type checking, matching, and scoping of patterns and binding
  variables.
- [Extending Switch for Patterns](extending-switch-for-patterns).  An early
  exploration of the issues surrounding extending pattern matching to the
  `switch` statement.
- [Type Patterns in Switch](type-patterns-in-switch)  (this document).  A
  more up-to-date treatment of extending pattern matching to `switch`
  statements, including treatment of nullity and totality.
- [Pattern Matching in the Java Object model](pattern-match-object-model).
  Explores how patterns fit into the Java object model, how they fill a hole we
  may not have realized existed, and how they might affect API design going
  forward.

[JEP 305][jep305] introduced the first phase of [pattern matching][patternmatch]
into the Java language, later refined by [JEP 375][jep375].  It was deliberately
limited, focusing on only one kind of pattern (type test patterns) and one
linguistic context (`instanceof`).  Having introduced the concept to Java
developers, we can now extend both the kinds of patterns and the linguistic
context where patterns are used.

The obvious next context in which to introduce pattern matching is `switch`;  a
switch using patterns as `case` labels can replace `if .. else if` chains with a
more direct way of expressing a multi-way conditional.   In the first iteration,
we will extend `switch` to support type patterns only, before moving on to other
kinds of patterns.  However, this document makes extensive reference to other
kinds of patterns, because where the language is going will have an impact on
the semantics that are sensible to choose.  

Unfortunately, `switch` is one of the most complex, irregular constructs we have
in Java, so we must teach it some new tricks while avoiding some existing traps.
Such tricks and traps may include:

- **Typing.**  Currently, the operand of a `switch` may only be one of the
integral primitive types, the box type of an integral primitive, `String`, or an
`enum` type.  (Further, the type affects the interpretation of case labels;  if
the `switch` operand is an `enum` type, the `case` labels must be _unqualified_
enum constant names.)  Clearly we can relax this restriction to allow other
types and constrain the case labels to only be patterns that are applicable to
that type, but it may leave a seam of "legacy" vs "pattern" switch, especially
if we do not adopt bare constant literals as the denotation of constant
patterns.  

- **Parsing.**  The grammar currently specifies that the operand of a `case` label
is a `CaseConstant`, which casts a wide syntactic net, later narrowed with
post-checks after attribution.  This means that, since parsing is done before we
know the type of the operand, we must be watchful for ambiguities between
patterns and expressions (and possibly refine the production for `case` labels.)
Literals are an obvious source of ambiguity (if we choose to denote constant
patterns with literals); deconstruction patterns with no arguments are another.

- **Nullity.**  In the limited forms where `switch` can be used on reference types
today (enums, strings, and primitive boxes), `switch`  appears to be hostile to
`null`.  However, this null-hostility does not make sense when extended to
richer forms of patterns, so we will need to refine the semantics to support the
legacy cases without polluting the new ones.  (It is always tempting to
preemptively say "sorry, nulls not allowed", but to not refine null handling
here will show up as sharp edges elsewhere, such as refactoring anomalies.)

- **Exhaustiveness.**  For switches over the permitted subtypes of sealed types,
we will want to be able to do exhaustiveness analysis, and for sufficiently
exhaustive pattern sets, allow the user to omit a catch-all case such as
`default`.  (When we get to nested patterns, we will want to do this for nested
patterns too --  if `Circle`  and `Rect` are exhaustive on `Shape`, then
`Box(Circle c)` and `Box(Rect r)` should be exhaustive on `Box<Shape>`.)

- **Fallthrough.**  Fallthrough is everyone's least favorite feature of `switch`,
but it exists for a reason.  (The mistake was making fallthrough the default
behavior, but that ship has sailed.)  In the absence of an OR pattern
combinator, one might find fallthrough in switch useful in conjunction with
patterns:

  ```
  case Box(int x):
  case Bag(int x):
      // use x
  ```

  However, it is likely that we will, at least initially, disallow falling out
of, or into, a case label with binding variables.

As we did with _expression switches_ in Java 12, rather than invent a new
linguistic construct that is like `switch` but different (but would have to
leave the old construct around), we choose to continue down the path of
rehabilitating switch to cover more situations.  This road involves some
difficult choices where the legacy behavior does not provide a clean
extrapolation to the desired generalized behavior; we must choose between the
extrapolating the legacy behavior anyway, or leaving a visible seam or asymmetry
-- neither of which is ideal.  There is no perfect answer here; we aim to
balance minimizing the long-term complexity of the language with compatibility
with past choices.

## Type patterns in switch

Adding type patterns to switch is not the final step in the addition of pattern
matching to Java; we expect pattern assignment statements, deconstruction
patterns, nested patterns, and declared patterns to follow.  However, the design
of this step has been extensively informed by analysis of the steps to follow.
In the explanatory sections below, we will make significant use of future steps
to ensure that the semantics we are choosing now has a clean extension to where
we want to end up.

The current iteration proposes:

 - Allow switches on all types (with the possible temporary exception of the
   three primitive types not currently permitted), not just the current limited
   set of types.
 - Allow `case` labels to specify patterns in addition to constant case labels.
   The specified pattern must be applicable to the static type of the switch
   target.
 - Restrict fallthrough out of cases with bindings and into non-total patterns
   with bindings.
 - Refine the null-handling behavior of `switch`:
   - Support a `case null` constant case label, which matches only `null` (when
     the switch target is a reference type.)
   - For switches on enums, strings, and primitive boxes that do not include an
     explicit `case null`, the compiler inserts an implicit `case null` at the
     beginning which throws `NullPointerException`.
   - In all other switches, `null` is just a value (though, see the treatment of
     totality with remainder.)
 - Support declarative _guards_ on case labels with patterns.
 - For total switches (which includes expression switches and possibly others)
   whose set of `case` patterns is total on the target type with nonempty
   remainder, provide an implicit `default` case that throws on the remainder.

These semantics have been carefully chosen to support a number of goals:

 - Refactoring-friendliness.  We want a simple relationship between a `switch`
   statement over patterns and a corresponding `if-else if` chain, so that the
   two can be freely refactored to each other.  This means that the semantics of
   `instanceof` and `switch` must be suitably aligned, particularly with respect
   to null handling.  Similarly, a switch whose case labels are a series of
   nested patterns `D(P)`, `D(Q)`, `D(R)` must have a simple relationship with a
   `case D d` with a nested switch whose cases are `P`, `Q`, and `R`.

 - Consistent meaning of `var`.  The pattern `var x` and the type pattern `T x`
   where `T` is arrived at by the obvious type inference should be equivalent;
   the choice to use `var` or a manifest type should be purely one of developer
   preference and not affect the semantics.

 - Generalization of `enum` switches to sealed classes.  Expression switches
   over `enum` types get special treatment; if all the constants of the `enum`
   are covered, the compiler will insert a `default` clause that throws on
   unexpected inputs (which can happen if a new constant is added and the client
   code not recompiled.)  We want to generalize this behavior to type patterns
   covering all the permitted subtypes of a sealed class, and eventually when a
   suitably total set of patterns is wrapped in a deconstruction pattern.

 - Rehabilitation of null handling.  Legacy switches are quite limited; they
   only work over a small set of types, and can only have constant case labels.
   Moreover, the set of reference types supported by legacy switches are special
   -- enums, strings, and primitive boxes -- where it is extremely rare that
   `null` is a sensible member of the domain.  As we generalize switch to
   support more types, and generalize case labels to express more complicated
   predicates over those types, this treatment of `null` is quickly seen to be
   arbitrary and problematic, and extending it in the obvious way would
   introduce many sharp edges that would cause surprises and interfere with
   otherwise-sensible refactorings.

Because of the complexity of the `switch` statement, and its history, there  are
still sure to be some rough edges and potholes.  (Unfortunately, these rough
edges are often more apparent than the benefits, since the costs can be imagined
relative to the code we write today, whereas the benefits often accrue more to
the code we will write tomorrow.)  For example, when extending `switch` to be an
expression or a statement, one of these costs was the asymmetry with respect to
totality between expression switches and statement switches.  In the current
round, these costs include having special legacy behavior for string, box, and
enum switches to match the legacy null-hostility, but not extending this
null-hostility to new reference-type switches, and possibly having an explicit
way for statement switches to opt into the same totality treatment that
expression switches get, rather than a single uniform rule.

### Translation

Switches on primitives and their wrapper types are translated using the
`tableswitch` or `lookupswitch` bytecodes; switches on strings and enums are
lowered in the compiler to switches involving string hash codes or enum
ordinals.

For switches on patterns we will need a new strategy.  A sensible candidate is
to lower the cases to a densely numbered `int` switch, and then invoke a
classifier function using `invokedynamic` (whose static argument list includes a
description of the patterns in order), whose arguments include the switch
operand and whose result tells us the first case number it matches.  So a switch
like:

```
switch (o) {
    case P: A
    case Q: B
}
```

is lowered to:

```
int target = indy[BSM=PatternSwitch, args=[P,Q]](o)
switch (target) {
    case 0: A
    case 1: B
}
```

A suitable symbolic description of the patterns is provided as the bootstrap
argument list, which builds a decision tree based on analysis of the patterns
and their target types.

At the same time, we may wish to switch to an `invokedynamic`-based
implementation for `String` and `Enum` switch as well; the static desugaring of
these in the compiler is complex, and the dynamic approach offers the
opportunity for performance improvement in the future without having to
recompile code.

### Guards

No matter how rich our patterns are, it is often the case that we will want to
provide additional filtering on the results of a pattern.  If we are doing so in
`instanceof`, there's no problem; it is easy to join multiple boolean
expressions with `&&`:

```
if (shape instanceof Cylinder c && c.color() == RED) { ... }
```

and the scoping rules already defined ensure that `c` is in scope in the second
clause of the conditional.   But in a `case` label, we do not currently have
this opportunity.  Worse, the semantics of `switch` mean that once a `case`
label is selected, there is no way to say "oops, my mistake, keep trying from
the next label".

It is common in languages with pattern matching to support some form of "guard"
expression, which is a boolean expression that conditions whether the case
matches, such as:

```
case Point(var x, var y)
    __where x == y: ...
```

There is a good reason guards are so common in languages with pattern matching;
without them, a 50-way switch for which one arm needs to represent a filter
condition that can't be expressed as a pattern would have to refactor to a
50-element long `if-else` chain, which results in less readable and more
error-prone code.

Syntactic options (and hazards) for guards abound; users would probably find it
natural to reuse `&&` to attach guards to patterns; `C#` has chosen `when` for
introducing guards; we could use `case P only-if (e)`, etc.  Whatever surface
syntax we pick here there is a readability risk,  as the more complex guards
are, the harder it is to tell where the case label ends and the "body" begins.
(And worse if we allow switch expressions inside guards, which we shouldn't do.)
Bindings from the `case` pattern would be available in guard expressions.

An alternative to boolean guards is to allow an imperative `continue` (or
`next-case`) statement in `switch`, which would mean "keep trying to match from
the next label."  Given the existing semantics of `continue`, this is a natural
extension, but since `continue` does not currently have meaning for switch, some
work would have to be done to disambiguate continue statements in switches
enclosed in loops.  This imperative alternative is strictly more expressive than
most reasonable forms of declarative guards, but users are likely to prefer the
declarative version, which more cleanly separates the dispatch criteria from the
consequences.

### Do we need constant patterns?

Originally, we envisioned denoting constant patterns with literals, since
existing case labels use literals:

```
switch (i) {
    case 1: ...
    case 2: ...
}
```

It seemed natural to say that `1` and `2` are constant patterns, which means we
could nest them: `case Box(0)`.  However, using literals as patterns creates
additional ambiguities between patterns and expressions (is `Box(0)` a pattern
match or an invocation of a method?), at least for humans if not for parsers.
And, constant patterns, outside of their top-level use in `switch`, are just not
that useful; we can always express these more flexibly with guards:

```
case Box(int x) when x == 1: ...
case Box(int x) when x == 2: ...
```

At this point, constant patterns do not seem to carry their weight.  We can
instead interpret a `case` label as carrying a compatible literal or pattern,
but not make literals into actual patterns.  (This is the same move we made with
`instanceof`; the RHS can be either a type or a pattern, but bare type names are
not patterns.)  We can consider adding them later, perhaps with a different
syntax (such as `Box(const 0)` or `Box(== 0)`) to distinguish them from
expressions.

### Missing primitive types

The set of primitives we can use today in `switch` is limited to the integral
numeric primitive types; this includes `char` but leaves out `float`, `double`
and `boolean`.  It is understandable why these were left out, as they are not
all that useful, but as `switch` gets more sophisticated their omission becomes
an impediment to refactoring.  For example, if `P` has one binding, and `Q` and
`R` are compatible with the type of that binding, we would like to be able to
refactor:

```
switch (o) {
    case P(Q): A
    case P(R): B
    ...
}
```

into

```
switch (o) {
    case P(var x):
        switch (x) {
            case Q: A
            case R: B
        }
    ...
}
```

However, if `x` were of one of the missing primitive types, we would not be able
to express this refactoring.  (Adding these types isn't hard, especially if we
are free to use an `indy`-based translation for these switch types.)  We don't
need to do this immediately, since the above motivation only applies when we
have nested patterns.

## Nullity and totality

Almost no language design exercise is complete without some degree of wrestling
with `null`.  (There is an inevitable temptation, when adding new aspects to the
language, to try to exclude null from these new aspects.  This temptation,
however natural, usually turns out to be a mistake, because these new aspects
typically have some connection to existing aspects.)  As we define more complex
patterns than simple type patterns, and extend constructs such as `switch`
(which may have existing opinions about nullity) to support patterns, we need to
have a clear understanding of which patterns match null, and separate the
nullity behaviors of _patterns_ from the nullity behaviors of _constructs which
use patterns_.  For the current phase -- type patterns in `switch` -- much of
this may seem like borrowing trouble as it is largely motivated by future steps,
but if we want to be able to consistently evolve to support deconstruction and
nested patterns, we have to tackle this now.

Pattern matching with type patterns and `instanceof` have a lot in common;  this
is one reason we choice to extend the semantics of `instanceof` to support
patterns, rather than create a similar-but-different alternate construct like
`matches`.  It is a tempting, but unfortunately wrong, initial thought to define
the semantics of type patterns purely in terms of the historical `instanceof
<type>` construct; this leads us to a wrong intuition about how to think about
nulls in pattern matching.  We will define what it means for a pattern to
_match_ a target, and then define the behavior of pattern-aware constructs in
terms of matching.  

A pattern is _total_ on a type if it matches all possible values of that type --
including null (for reference types).  The "any" pattern (`var x`) is total on
all types; the type pattern `T t` is total on all types `U <: T`.  If a pattern
is total on its target type, no dynamic test is required; we already know it
matches.  Other than the constant pattern `null`, total patterns are the only
patterns that match `null`.

If `D(T)` is a deconstruction pattern with a binding of type `T`, then the
nested pattern `D(Q)` matches `x` if and only if `D(var t)` matches `x` and `Q`
matches `t`.  Deconstruction patterns themselves can never match `null` (a
deconstructor is an instance member, like a constructor, and we cannot invoke an
instance member with a null receiver), but if `D(var t)` matches its target,
then `t` may well be null.

### Switch totality

Just as some patterns are total on some types, some switches are also total on
some types.  For a `switch`, totality means that some action is taken for every
member of the value set of the target type -- no values "leak through" silently.
Expression switches must be total (because expressions must be total); the
compiler will enforce that the set of cases, in the aggregate, cover all the
possible values.  Currently there are two ways to get totality; a `default`
clause, or, for a switch over an `enum` type, specifying `case` clauses for all
the known constants of that type.  

Even today, this notion of totality is still somewhat "leaky".  If we have an
enum class:

```
enum Color { RED, GREEN, BLUE }
```

and a switch expression:

```
Color c = ...;
int n = switch (c) {
    case RED -> 1;
    case GREEN -> 2;
    case BLUE -> 3;
}
```

there are still two sorts of values of `c` for which an explicit `case` is not
taken: `null` and novel `Color` constants that may have been introduced (via
separate compilation) since this client was compiled.  In these cases, the
compiler ensures that an exception is thrown rather than the value being
silently ignored.  Let's call these unwelcome values the _remainder_ of the
specified set of patterns on the target type.

Why do we accept this switch as total, when we know it is not really?  Because
the values in the remainder are, in some sense, "silly" values.  It would be
annoying and pedantic for the compiler to require that we provide a `case null`
to catch null (which would probably just throw NPE anyway) and a `default` to
catch constants that may arrive from the future (which would probably just throw
ICCE or ISE anyway.)  So we allow this switch to be accepted as "total enough",
and the compiler inserts code to handle the silly values.  (If in some situation
we think they are not silly, we are free to add explicit `case null` or
`default` clauses to handle them.)

### Refining totality

With this notion of remainder, we can define some rules for when a set of
patterns is total on a type, and characterize the remainder.  We will define
when a set of patterns `P*` is total on a type `T` with remainder `R*`; the
remainder is a _set of patterns_ that characterizes the remainder.  The
intuition is that, if `P*` is total on `T` with remainder `R*`, the values
matched by `R*` but not by `P*` are deemed to be "silly" values and a language
construct like switch can (a) consider `P*` sufficient to establish totality and
(b) can insert synthetic tests for each of the patterns in `R*` that throw.  We
start with the obvious base cases:

 - `{ T t }` is total on any `U <: T` with empty remainder.
 - `{ var t }` is total on all types `T` with empty remainder.
 - The `default` case corresponds to a pattern that is total on all types `T`
   with empty remainder.

Now, let `D(T)` be a deconstruction pattern with a single binding of type `T`.
Then:

 - If `{ Q }` is total on `T` with remainder `R*`, then { `D(Q)` } is total on
   `D` with remainder `{ null }` &cup; `{ D(R) : R in R* }`

By this rule, we see that, `Box(Bag(var s))` is total on `Box<Bag<String>>` with
remainder `{ null, Box(null) }`.  

We can recast our rule about enums more explicitly.  Suppose `E` is an enum
class with constants `C1..Cn`:

 - The set of constant patterns `{ C1, ... Cn }` is total on E with remainder
   `{ null, E e}`.

At first, this rule might look useless -- since the remainder pattern is the
entirety of what we're matching.  But what this means is that, after matching
all the provided cases, the only thing left are "silly" values, and the silly
values are completely characterized by the set of patterns `{ null, E e }`.  

There is an obvious analogue of this rule for sealed classes.  Suppose `S` is an
abstract sealed class or interface, with permitted direct subtypes `C0..Cn`, and
`P*` is a set of patterns.

 - If for each `Cn` there exists a subset `P(Cn)` of `P*` that is total on `Cn`
   with remainder `Rn`, then `P*` is total on `S` with remainder
   `{ null, S s } ` &cup; ` { R0 } ` &cup; ` ... { Rn }`.

If `S` is a concrete class, we can amend the above rule to add `S` explicitly
into the list `C0..Cn` (because if `S` is concrete, we need to cover it
explicitly.)

Finally, if we have a total set of patterns, we can generalize lifting
deconstruction over them.  If `D(T)` is a deconstructor, and `P*` is total on
`T` with remainder `R*`, then:

 - `{ D(P) : P in P* }` is total on `D` with remainder `{ null }` &cup;
   `{ D(R) : R in R* }`.  

If we need to cover union types, there is also a simple rule for that:

 - If `P*` is total on `A` with remainder `R*`, and `Q*` is total on `B` with
   remainder `S*`, then `P*` &cup; `Q*` is total on `A|B` with remainder  `R*`
   &cup; `S*`.

This construction (which is simplified in that it only covers deconstruction
patterns of arity 1) both provides a basis to determine whether a set of
patterns is total on a type, but also constructs the remainder.   The motivation
for all these rules is the same as outlined above for enums -- the remainder
values are often enough "silly" values that the cost of expecting users to spell
them out would be excessive.

We can now simply say that for a total switch on a target type `T` (which
currently includes only expression switches), the set of patterns named by the
cases must be total on `T` with some remainder according to these rules, and the
compiler can insert synthetic cases to throw on the remainder.  If the switch
contains _more_ patterns than are required for totality, the only effect is that
some of these synthetic cases may never be reached.

Guarded patterns should be ignored entirely for purposes of computing totality.

### Patching the legacy holes

We have so far described a `switch` construct where `null` is just an ordinary
value and where some patterns match `null`.  For total switches where `null` is
in the remainder of the pattern set, the above construction covers throwing
`NPE`.  But for partial switches (such as statement switches), null would be
ignored like any other non-matched value.  This is inconsistent with the current
treatment of `null` in the few reference-targeted switches that we currently
have, so to accomodate this, we add the following rule for compatibility:

 - For a switch on `String`, a primitive box type, or an enum type, if there is
   no explicit `case null`, we insert an implicit `case null` at the beginning
   of the `switch` that throws `NPE`.

This means switches on these types continue to be preemptively null-hostile as
before, but we add the ability to handle the null explicitly, and for switches
on other types, the more general rules outlined here apply.

### Looking ahead: pattern assignment

We used totality of pattern sets to determine whether a switch was total.  We
can use the same machinery to unify pattern assignment with local variable
declaration.

We anticipate a pattern assignment statement:

```
P = e;
```

In the case of a `var x` or type pattern, this looks like just like a local
variable declaration and has the same semantics.  But we can extend it to  any
pattern that is total (possibly with some remainder) on the static type of `e`;
the compiler then generates code to throw on the remainder.  Deconstruction
patterns are a prime example; we would like to be able to say

```
Point(var x, var y) = aPoint;
```

and treat `null` as dynamically rejected remainder.

### Total patterns in instanceof

Our decision to use `instanceof` for pattern matching rather than creating a new
`match` operator leaves us with one sharp edge.  There are two forms for
`instanceof`:

```
x instanceof Type
x instanceof Pattern
```

In the former case, the semantics are fixed; it is true if and only if `x` is a
_non-null_ instance of that type.  But in the latter case, a total pattern like
`var x` matches null.  It would be weird if

```
x instanceof Object
```

and

```
x instanceof Object o`
```

had different semantics; this would be a sharp edge.  It might seem "obvious"
that this is evidence of an error somewhere, but really, this is just collateral
damage from rehabilitating `instanceof` rather than creating a parallel
`matches` construct with subtly different semantics.  

In any case, there is an obvious and sensible fix here: disallow patterns that
are total with no remainder in `instanceof`.  It is sensible because `x
instanceof <total pattern>` is in some sense a silly question, in that it will
always be true and there's a simpler way (local variable assignment) to express
the same thing.  (More generally, we are saying that `instanceof` should always
be asking a question.)  If the question is a silly one to ask, and the ability
to ask it creates confusion, then outlawing it solves the problem.

### Refactoring switches

These semantics allow us to freely refactor between switch statements and chains
of  `if (x instanceof P) ... else if (x instanceof Q)` without fear of subtle
semantic change.  Ignoring remainder handling (there is currently no way to ask
for remainder handling in statement switches), a switch with patterns:

```
switch (t) {
    case A: X
    case B: Y
    case C: Z
}
```

is equivalent to

```
if (x instanceof A) { X }
else if (x instanceof B) { Y }
else { Z }
```

if `C` is total with no remainder, and

```
if (x instanceof A) { X }
else if (x instanceof B) { Y }
else if (x instanceof C) { Z }
```

otherwise.  Further, a switch with nested patterns on `D(T)`:

```
case D(P): A
case D(Q): B
case D(R): C
```

where `{ P, Q, R }` are total (with no remainder) on `T` can be refactored to:

```
case D(var x):
    switch (x) {
        case P: A
        case Q: B
        case R: C
    }
```

### Some intuitions about totality and nullity

The interaction of totality and nullity, and the minor divergence between what
it means to match and what `instanceof` does, may be surprising at first.

The first thing to realize is that this new `switch` is a much more powerful
construct than its prior self, and the assumptions that made sense with legacy
switch do not necessarily scale to a more powerful construct.  Previously,
`switch` was restricted to types whose values were enumerable with literals --
integers (and their boxes), characters, strings, and enums.  These are very
constrained domains, and given the context in which reference switches got off
the ground (in Java 1.0, there were not even any switches over reference types;
switches over enums and boxes was added in Java 5, and over strings in Java 7),
it seemed a pragmatic consideration at the time to just outlaw null, since null
primitive boxes and null enum values were considered "silly".  But as switch
becomes more general and case labels become more powerful, this assumption
itself starts to turn silly.

As a motivating example, consider:

```
record Box(Object o) { }

Box box = ...
switch (box) {
    case Box(var x):
}
```

We'll further posit that the `Box` class expresses no opinions about what it
holds; `null` is as good a value as any.  That means that it is OK to construct:

```
Box bnull = new Box(null);
```

So the first question is, should `Box(var x)` match `bnull`, and assign `null`
to `x`?  Really, there's no way it could do anything else, because of what
deconstruction is -- it is the dual of construction.  If I can put a `null`  in
a box with the constructor, it would be absurd if I could not take a `null` out
of the box with the corresponding deconstructor.  It would be like having a
`List` that you could put `null` elements into, but couldn't get them out; this
would be a terrible `List` implementation.  (And, if the `Box` constructor
rejects nulls, it doesn't matter, because the case where the deconstructor might
serve up a `null` would never come up.)

The `var x` pattern is the most general pattern we have that can nest in the
`Box` deconstruction pattern; it matches everything.  If it didn't match `null`,
then the nested pattern `Box(var x)` wouldn't match `Box(null)` -- which is
silly because  `Box(null)` is a totally valid member of the value set of `Box`.

What if we used `Box(Object x)` instead of `Box(var x)`?  If we want `var` to be
mere type inference -- rather than something subtly different -- then the
logical type to infer here is `Object`, based on the declaration of the
deconstructor in `Box`.  This means that `Box(Object o)` also matches
`Box(null)`.  And since `x` matches `Box(Object o)` if and only if `x` matches
`Box(var t)` and `t` matches `Object o`, that suggests that `null` matches
`Object o` too.

So, does it mean that type patterns always match null?  That would be
extrapolating from too few data points.  If we had:

```
Box b = ...;
switch (b) {
    case Box(Chocolate c):
}
```

one might think that, by the above argument, it should also match `bnull`.  But
the sensible outcome is more subtle; this is a nested pattern with a _dynamic_
type test.  We only match the target if it matches `Box(var t)`, and further if
`t` matches `Chocolate c`.  In the cases where there is actually a dynamic test
going on, the semantics of `instanceof` is what we want -- the above pattern
should match on (non-null) `Box` instances whose contents are non-null
`Chocolate` instances.  

This gets more obvious when we have multiple partial cases:

```
switch (box) {
    case Box(Chocolate c):
    case Box(Frog f):
}
```

The types `Chocolate` and `Frog` are presumed unrelated, so we should be free to
reorder the two cases, as the sets of values they match seem disjoint, right?
But, if either of these matched `Box(null)`, then the value sets would not be
disjoint, and we would not be able to freely reorder these cases.

At first, the conjunction of these examples may seem strange, because on the one
hand, it looks like `Box(var x)` -- which is just type inference for `Box(Object
x)` -- should match `Box(null)`, but `Box(Frog f)` should not.  

So what is the difference?  The difference is _totality_.  The pattern `Box(Frog
f)` on an arbitrary `Box` encodes a dynamic test which only matches certain
boxes, with a conditional destructuring should the test succeed.  On the other
hand, there is nothing conditional about matching `Box(var x)` to a `Box` -- it
is just pure unconditional destructuring.  This becomes more obvious when we put
them together:

```
Box box = ...
switch (box) {
    case Box(Chocolate c):
    case Box(Frog f):
    case Box(var o):
}
```

What's happening here is that we are handling the special cases first, and the
last case is a catch-all that handles "all the rest of the boxes"; it is like a
`default` case, but is richer because it also destructures the target.  This
pattern is exceedingly common in languages with similar constructs; use the
conditionality of pattern matching for the distinguished cases, and use the
deconstructuring of pattern matching for the catch-all case.  (This idiom should
be familiar to Java developers in another context: `try-catch` chains, where the
specific exception types come before the catch-all handler.)

We used similar reasoning earlier, with the corner case of total patterns in
`instanceof` -- that it is significant whether a pattern is asking a nontrivial
question or not.  A total pattern not asking a question, and that is
significant.

It is conceivably possible that in some situation, that what the author meant by
the last case was "All boxes, except those that contain null", but in reality
this is extremely unlikely; if `null` is in the domain of `Box` contents, then
typically you want to treat the domain uniformly.  (And if `null` is not in the
domain, it doesn't make a difference.)  

These rules are sound, but may feel a little uncomfortable at first because they
are unfamiliar.  There are other rules we might be tempted to adopt, but they
have much sharper edges, and those sharp edges would persist long after we
achieved familiarity.

Some have even suggested that `var x` not match `null`.  But it would be
terrible if there were _no_ way to say "Match any `Box`, even if it contains
`null`.  One might be initially tempted to patch this with OR patterns, where we
ORed together `Box(var x)` and `Box(null)`, but this quickly falls apart when
`Box` has multiple bindings; if destructuring a `Box` yielded _n_ bindings,
we'd need to OR together _2^n_ patterns, with complex merging, to express all
the possible combinations of nullity, and this would be both cumbersome and
error-prone.  It is hard to escape the conclusion that the last pattern is
intended to match any `Box` -- including those that contain `null`.

Scala and C# took a different road, where `var` patterns are not just type
inference, they are "any" patterns -- so `Box(Object o)` matches boxes
containing a non-null payload, where `Box(var o)` matches all boxes.  This
aligns the meaning of the `Object o` pattern with that of `instanceof`, but at a
serious cost: that `var` is no longer mere type inference.  Users should not
have to choose between the semantics they want and being explicit about types;
these should be orthogonal choices, and the choice to leave types implicit
should be solely one of whether the manifest types are deemed to improve or
impair readability.


[jep305]: https://openjdk.java.net/jeps/305
[jep375]: https://openjdk.java.net/jeps/375
[patternmatch]: pattern-matching-for-java
