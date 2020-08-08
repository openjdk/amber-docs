# Pattern Matching for Java -- Semantics

#### Gavin Bierman and Brian Goetz, September 2018

This document explores a possible direction for supporting _pattern
matching_ in the Java Language.  This is an exploratory document only
and does not constitute a plan for any specific feature in any
specific version of the Java Language.  This document also may
reference other features under exploration; this is purely for
illustrative purposes, and does not constitute any sort of plan or
committment to deliver any of these features.

In [a companion document](pattern-matching-for-java.html), we outline the
motivation for adding pattern matching to Java, the sorts of patterns
that might be supported (constant patterns, type patterns,
deconstruction patterns, etc) and the constructs that could support
patterns (`instanceof`, `switch`, and a pattern-bind statement.)

## Pattern semantics

We first define what it means for a pattern to match a target, and
then we will outline the interaction between pattern matching and
pattern-aware language constructs.  We define several categories of
patterns:

 - The _any_ pattern, denoted by `_`;
 - The _var_ pattern, denoted by `var x`;
 - _Type patterns_, denoted by `T t`;
 - _Nullable type patterns_, denoted by `T? t`;
 - _Constant patterns_, denoted by lexical literals or by names of
   constant variables (JLS 4.12.4) or enum constants;
 - _Deconstruction patterns_ for a type `T`, denoted by `T(P*)`, where
   `P*` is a sequence of nested patterns.

#### Static type checking

In a pattern match, there is an _operand_ (the thing we are trying to
match against the pattern) and a pattern.  The operand is an
expression, so it has both a static and a dynamic type.  Certain
pattern matches can be rejected at compile time based on static types,
such as:

```
String s = "Hello";
if (s instanceof Integer i) { ... }
```

The compiler knows the operand `s` is a `String`, and the type pattern
`Integer i` only matches expressions of type `Integer`, and that both
are final classes.  So we can conclude the match cannot succeed, and
can therefore reject this at compile time on the basis of static type
checking (just as we reject an attempt to cast an `Integer` to a
`String`.)

We define an _applicability_ relation between a pattern and a type,
which determines if the pattern is applicable to an operand of
(static) type `T`.  We define applicability as follows:

 - The any pattern and the `var` pattern are applicable to all types.
 - The `null` constant pattern is applicable to all reference types.
 - A numeric literal constant pattern `n` without a type suffix is
   applicable to any primitive type `P` to which `n` is assignable
   (within the numeric range of `P`), and to `P`'s box type.
 - Other constant patterns of primitive type `P` are applicable to `P`
   and to types `U` which are cast-convertible to `P`'s box type.
 - Constant patterns of reference type `T` are applicable to types
   `U` which are cast-convertible to `T`.
 - The type pattern `P p` for a primitive type `P` is applicable to `P`.
 - The type pattern `T t` and the nullable type pattern `T? t` for a
   reference type `T`, are applicable to types `U` which are
   cast-convertible to `T`.
 - A deconstruction pattern `D(...)`, is applicable to types `U`
   which are cast convertible to `D`.

Generic type patterns are permitted (this is a relaxation of the
current semantics of the `instanceof` operator, which requires that
the type operand is reifiable.)  However, when determining
applicability involves cast conversions, the pattern is not applicable
if the cast conversion would be _unchecked_.  So it is allowable to
say

    List<Integer> list = ...
    if (list instanceof ArrayList<Integer> a) { ... }

but not

    List<?> list = ...
    if (list instanceof ArrayList<String> a) { ... }

as the latter cast conversion would be unchecked.

If a pattern is not applicable to the static type of its operand
(including if a cast conversion required for applicability would be
unchecked), a compilation error results.  For a deconstructor declared
`D(T*)` (where `T*` are the types of the pattern variables), we
further require a deconstruction pattern `D(P*)` have each `Pi` be
applicable to the corresponding `Ti`.

It may appear we are being unnecessarily unfriendly to numeric
constant patterns here, by not being more liberal about widening and
boxing conversions.  However, we wish to avoid situations where an
`Object` operand is matched against a literal constant `0`; one could
mistakenly expect this to match all of `Integer` zero, `Short` zero,
etc.  Instead, when matching against broad operand types (which are
necessarily reference types), we should be explicit and either use
typed constants or use destructuring patterns on the box type, such as
`Integer(0)` or `Short(0)`.  We reserve the numeric literal patterns
for matching primitives and their boxes only where there is no
possible type ambiguity.

For numeric literal constants without type suffixes, when attempting
to match against an operand of primitive type `P` or the box type for
a primitive type `P`, then the constant is interpreted as being of
type `P`.  This means that we can speak of every constant pattern
having an unambiguous type.

#### Matching

We define a _matches_ relation between patterns and expressions as
follows.

 - The any pattern and the `var` pattern match anything.
 - The `null` constant pattern matches `e` if `e == null`.
 - A primitive constant pattern `c` of type `P` matches `e : P` if `c`
   is equal to `e`, and matches `e : T` if `e` is an instance of `P`'s
   box type, and `c` equals `unbox(e)`.  Equality is determined by the
   appropriate primitive `==` operator, except for `float` and
   `double`, where equality is determined by the semantics of
   `Float::equals` and `Double::equals`.
 - A reference constant pattern `c` of type `T` matches `e` if
   `c.equals(e)`.
 - A type pattern `T t` matches `e` if `e instanceof T`.
 - A nullable type pattern `T? t` matches `e` if `e == null` or `e
   instanceof T`.
 - A deconstruction pattern `D(Pi...)` matches `e` if `e instanceof T`,
   and for all _i_, `Pi` matches the _i_th component extracted by `D`.

A pattern is _nullable_ if it can match null; the any pattern, `var`
patterns, the `null` constant pattern, and nullable type patterns are
nullable.  We say a pattern is _total_ on a type `T` if it matches all
values of type `T`.

#### Pattern variables

Some patterns define variables which will be bound to components
extracted from the target if the match succeeds.  These variables have
types defined as follows:

 - For a type pattern `T t` or nullable type pattern `T? t`, the
   pattern variable `t` has type `T`.
 - For a pattern `var x`, the type of the pattern variable `x` is
   computed by type inference, where constraints are derived from the
   match operand, and, in the case of a nested `var` pattern, from the
   types declared in the corresponding `extractor` declaration.

In both cases, the pattern variable is initialized to the match
operand (after casting to the appropriate type) when a successful
match is made.  Pattern variables are always `final`.

## Pattern-aware constructs

Several constructs, such as `instanceof`, `switch`, and `__let`, are
pattern-aware.

The syntax of `instanceof` is extended as follows:

    <expression> instanceof <reifiable-type>
    <expression> instanceof <pattern>

The `instanceof` operator evaluates to `false` if the pattern operand
is non-nullable and the expression operand is `null`.

Currently, `switch` only supports a limited range of operand types;
when it becomes pattern aware, it can accept any operand type (but
patterns are type checked for applicability with the operand type),
and patterns may be used as `case` labels.

If none of the patterns in a switch is nullable, then a `switch`
throws `NullPointerException` on entry if the expression operand is
`null.`

A _pattern bind_ statement, which for purposes of exposition we'll
call `__let`, will unconditionally match the expression operand to the
pattern.

    __let <pattern> = <expression>;

    __let <pattern> = <expression>
    else <statement>;

In the simpler form (no `else`), the pattern must be total on the type
of the expression operand (excluding `null` for non-nullable
patterns.)  This allows us to write:

    Point p;
    __let Point(var x, var y) = p;
    // can use x and y here

without having to explicitly write an `else` clause.

In the full form, partial patterns are allowed, and if the expression
operand does not match the pattern, the `else` statement is executed.
The `else` statement must terminate abruptly.

## Scoping of pattern variables

Unlike traditional locals, which have "rectangular" scopes, the scope
of a pattern variable is flow-sensitive.  This ensures that it is in
scope only where it would be definitely assigned.

To define the scoping semantics, we assign to each expression `e` a
"true set" and a "false set" of bindings, denoted `e.T` and `e.F`,
which are always disjoint.  The following table shows the true and
false sets for all expression forms, along with an "include" column,
which tells us which bindings are in scope in which contexts.

+-----------------+---------------------+---------------------+---------------+
|Expression form  |T                    |F                    |Include        |
|                 |                     |                     |               |
+=================+=====================+=====================+===============+
|x instanceof P   |bindings(P)          |(empty)              |               |
+-----------------+---------------------+---------------------+---------------+
|x && y           |union(x.T,y.T)       |intersect(x.F,y.F)   |x.T in y       |
|                 |                     |                     |               |
+-----------------+---------------------+---------------------+---------------+
|x || y           |intersect(x.T,y.T)   |union(x.F,y.F)       |x.F in y       |
|                 |                     |                     |               |
+-----------------+---------------------+---------------------+---------------+
|( x )            |x.T                  |x.F                  |               |
+-----------------+---------------------+---------------------+---------------+
|!x               |x.F                  |x.T                  |               |
+-----------------+---------------------+---------------------+---------------+
|x ? y : z        |union(               |union(               |x.T in y<br>   |
|                 |  intersect(y.T,z.T),|  intersect(y.F,z.F),|x.F in z       |
|                 |  intersect(x.T,z.T),|  intersect(x.T,z.F),|               |
|                 |  intersect(x.F,y.T))|  intersect(x.F,y.F))|               |
+-----------------+---------------------+---------------------+---------------+
|others           |empty                |empty                |               |
+-----------------+---------------------+---------------------+---------------+

In this table, union is a _disjoint union_ (it is an error if the sets
being unioned contain variables with the same name).  For
intersection, it is an error if any variables in the sets being
intersected contain the same name but have different types.

For example, in the following:

    if (x instanceof Foo(int y) && y > 0) { ... }
    if (!(x instanceof Foo(int y)) || y > 0) { ... }

the pattern variable `y` is in scope and DA where it is used, but in

    if (x instanceof Foo(int y) || y > 0) { ... }
    if (!(x instanceof Foo(int y)) && y > 0) { ... }

the pattern variable `y` is not in scope, and hence this is an error.
The rule about `&&` is also what allows us to express `equals()` in
terms of a pattern match:

```
public boolean equals(Object o) {
    return (o instanceof Point p)
        && p.x == x    // p in scope here
        && p.y == y;    // p in scope here
}
```

Why would we declare a pattern variable to be not in scope where it is
not defined, rather than simply defining it to be DU?  This is so that
binding names can be _reused_.  Consider the following:

```
if (x instanceof Point p && p.x == 0) { ... }
else if (x instanceof Point p && p.x == p.y) { ... }
```

If `p` were in scope for the whole of the statement containing the
first `instanceof`, as would be implied by a traditional scope, then
the second arm of the `if-else` chain would have to pick a different
name for its pattern variable.  With a simple `if..else` like this
one, this might not seem like a big deal, but in a `switch` statement
with dozens of clauses, this would indeed get annoying.

Further, we want to support _merging_ of pattern bindings, as in:

```
if ((x instanceof BlueBox(int height)
    || x instanceof RedBox(int height)) && height > 10) { ... }
```

Since exactly one of the two bindings for `height` are in scope and DA
in the last clause, we can merge these bindings.  If we could not, we
would have to restructure the code, likely with significant
duplication, to achieve the same effect.

#### Scoping and statements

We have defined which expressions produce bindings, but we have not
yet tied their scopes to statements.  The obvious extension of the
above rules to `if` statements would yield:

```
if (x instanceof Foo f) {
    // f is in scope here
}
else {
    // if is not in scope here
}
```

But, what about this:

```
if (!(x instanceof Foo(var y, var z)))
    throw new NotFooException();
// Are y and z in scope here?
```

Because the body of the `if` always completes abruptly, it is as if
the remainder of the scope was an implicit `else` block, and it would
be desirable for `y` and `z` to be in scope (and DA) in the remainder
of the scope.  On the other hand, in this example:

```
if (!(x instanceof Foo(var y, var z)))
    System.out.println("not a Foo, saddenz");
// Are y and z in scope here?
```

If `y` and `z` were in scope after the `if`, they would definitely not
be DA.  And, as per the argument above about reuse, we only want
bindings to be in scope where they are DA, so we can reuse the names
elsewhere.  We can accomplish this by incorporating additional results
from the existing flow analysis into the scoping rules for pattern
variables.

We define several additional predicates on statements: `AA(x)` is true
when `x` always completes abruptly; `NB(x)` is true when `x` never
completes abruptly because of `break`; `MF(x)` is true when `x` may
"fall through" into the following case label.  (These are already
computed by existing flow analysis.)  We can now add in the effects of
nonlocal control flow to our scoping rules:

+------------------------------+-----------------------------------------+
|Statement                     |Include                                  |
+==============================+=========================================+
|if (x) y else z; s;           |x.T in y<br>                             |
|                              |x.F in z<br>                             |
|                              |AA(y) && !AA(z) ?                        |
|                              |x.F in s<br>                             |
|                              |AA(z) && !AA(y) ? x.T in s<br>           |
|                              |                                         |
+------------------------------+-----------------------------------------+
|while (x) y; s;               |                                         |
|                              |x.T in y<br>                             |
|                              |NB(y) ? x.F in s                         |
+------------------------------+-----------------------------------------+
|do { x } while (y); s;        |NB(x) ? y.F in s                         |
+------------------------------+-----------------------------------------+
|for (a; b; c) d; s;           |b.T in c<br>                             |
|                              |b.T in d<br>                             |
|                              |NB(d) ? b.F in s                         |
+------------------------------+-----------------------------------------+
|switch (e) {<br>              |bindings(P) in a<br> MF(a)<br> ?         |
|  case P: a;<br>              |intersection(bindings(P), bindings(Q)) in|
|  case Q: b;<br>              |b<br>                                    |
|}                             |     : bindings(Q) in b;                 |
|                              |                                         |
+------------------------------+-----------------------------------------+
|let P = e;<br>                |e.T in t                                 |
|else s;<br>                   |                                         |
|t;                            |                                         |
|                              |                                         |
|                              |                                         |
+------------------------------+-----------------------------------------+

With these rules, we are able to get the full desired scoping with
awareness of whether we throw out of `if` blocks, `break` out of
`while` loops, or fall out of case groups..

As mentioned already, the motivation for flow-sensitive scoping is so
we can reuse pattern variable names when they are not in scope:

```
switch (e) {
    case RedBox(int height) -> System.out.printf("Red(%d)", height);
    case BlueBox(int height) -> System.out.printf("Blue(%d)", height);
}
```

And we can even even merge pattern variables in switch fallthrough:

```
switch (e) {
    case RedBox(int height):
        System.out.println("It's red");
        // fall through
    case Box(int height):
        System.out.println("It's a box of height: " height);
}
```

While these rules may look complicated at first, the rules are derived
strictly from the flow analysis rules (such as DA/DU) already in the
language.  So the result is that a pattern variable is in scope
wherever it would be DA, and not in scope wherever it would not be DA.
(In informal focus-testing with experienced Java programmers, we asked
"is it reasonable to be able to use variable `x` here" for various
examples, and there were no surprises, because they were already
familiar with when a variable is guaranteed to have a value, and when
not.)

#### Shadowing

Because the scoping of pattern bindings is not exactly the same as for
local variables, we must describe the interaction between pattern
bindings and other kinds of variables (locals, fields.)

To avoid confusion, it makes sense to adopt a strict "no shadowing"
rule: pattern variables may not shadow local variables, fields, or
other pattern variables, and similarly locals cannot shadow
pattern variables.  This avoids problems like:

```
class Swiss {
    String s;

    void cheese(Object o) {
        // pattern variable s "declared" here
        if (!(o instanceof String s)) {
            // But s not in scope here!
            // So s here would refer to the field
        }
        else {
            // And s here would refer to the pattern variable
        }
    }
```

Because pattern variable names are strictly local, we can always
choose names that do not conflict with locals or fields in scope.

## Nullability

Nullability is a complex topic, and one fraught with tradeoffs.  We
start with existing constraints: `switch` throws
`NullPointerException` on entry if its operand is `null`; `instanceof`
treats `null` as not an instance of anything but does not throw.
Source compatibility prevents us from changing these for code that is
currently valid, but we also want to extend the semantics of these
constructs in a non-surprising way.  Along the way, we will encounter
strong and diverse opinions about how `null` should be handled
(ranging from "null is just another value" to "kill it dead, now,
dead, now.")  Our approach is to avoid picking winners and losers
here, and to provide a set of primitives that can equally well support
null-avoiding and null-tolerant coding styles.

#### Constraints

Unfortunately, a more nuanced story for null handling is needed than
what we have now, in part because the current story scales poorly to
_nested patterns_.  If we have a class:

```
class Box<T> {
    private final T t;

    public Box(T t) { this.t = t; }
    public extractor Box(T t) { t = this.t; }
}
```

The author of the class has decided that `new Box(null)` is an
entirely reasonable value for this class; the language shouldn't
second-guess this design choice.  So it would be unreasonable to
prevent `Box(_)` from matching `Box(null)`, for example; if we're
matching "any box", then we should match any box.

We also have some constraints that come from the desire to have
unsurprising semantics for control constructs.  For example, for a
switch that is free of "weird" control flow (i.e., fallthrough):

```
switch (e) {
    case P: A;
    case Q: B;
    default: C;
}
```

this should be (to the extent possible) equivalent to, and therefore
mechanically refactorable back and forth between, an `if-else` chain:

```
if (e instanceof P) { A }
else if (e instanceof Q) { B }
else { C }
```

Similarly, a switch on nested patterns:

```
switch (e) {
   case Foo(P): ...
   case Foo(Q): ...
   case Foo(_): ...
}
```

should be "unrollable" to:

```
switch (e) {
    case Foo(var x):
        switch (x) {
            case P: ...
            case Q: ...
            case _: ...
        }
}
```

Taken together, this means that there are at least some cases where it
is reasonable to expect `switch` to deal with null operands without
throwing NPE.  (If `Foo(_)` should match `Foo(null)`, then
unrollability demands `_` should match `null` -- which means a
`switch` containing a `_` pattern should _not_ throw on entry when the
operand is `null`.)

#### Nulls and individual patterns

Our path for determining the semantics of various patterns with
respect to null has been a fairly winding one.  While its impractical
to rehash the entire journey, let's look at some specific examples.

We initially liked the idea that a type pattern `T t` would match
anything that is assignment-compatible to `T`, including `null`.  But
this runs into a few problems.

First, it means that refactoring between `switch` and `instanceof` is
painful, because `instanceof T t` would not be consistent with
`instanceof T`; this was a warning sign.  (Some might assume the
problem here is that we're trying to generalize `instanceof`, but
having a `matches T t` that behaves similarly but subtly differently
from `instanceof T` is no better, as it has a similar cognitive load
for users to deal with.)

Additionally, if `T t` were to match nulls, this would likely lead to
unexpected NPEs.  For example, it would be easy to forget that one
can't safely use `s` in the following example:

```
if (x instanceof String s)
    printf("String of length %d%n", s.length());
```

If the type pattern `String s` matched `null`, this code would NPE in
the body of the `if`, since we'd be dereferencing a null `String`
reference.  This would be a sharp edge that cuts over and over.

Further, having type patterns match nulls would result in surprising
order dependency.  If we have:

```
switch (box) {
    case Box(String s): ...
    case Box(Integer i): ...
    case Box(_): ...
}
```

and type patterns matched null, the nulls would fall into the first
`case`.  Not only is this surprising, but its even more surprising
that if we reordered the first two cases -- which surely look disjoint
-- it would subtly change the behavior of the program, because they
both match `Box(null)`.

So the conclusion is: type patterns `T t` should be have the same
semantics as `instanceof T`.

On the other hand, there _must_ be some way to match and destructure
all boxes; asking users to partition boxes into null-containing and
non-null-containing ones would be unworkable.

Intuitively, we'd like `Box(var x)` to match all boxes, even if the
box holds a `null`, but this also runs afoul of another intuition --
that `var` patterns should simply be type-inferred type patterns, so
that `var x` is merely a shorthand for writing some other type
pattern.

Another candidate, that also is a near-miss, is to treat _total_
patterns specially in a nested context; to have `Box(Object o)` match
all boxes, even those that contain a null.  This seems attractive when
you look at typical switches over nested patterns; there are often
some more specific patterns first, and then we fall into the most
general `Box` pattern, `Box(Object o)`.  But, this falls afoul of
desiring that a `switch` with nested patterns neatly unroll into a
nest of switches with non-nested patterns, and creates an "action at a
distance" effect.

#### Nullable type patterns

The root cause of our wanderings here is that sometimes we want
`Object` to mean non-null objects (as in `instanceof`), and other
times we want to use it as a catch-all that means "everything".  The
standard move in this situation is to split it into two locutions, so
people can say what they mean explicitly.

To accomplish this, we introduce a _nullable type pattern_, `T? t`,
which matches instances of `T` as well as `null`.  (This does not mean
we're introducing nullable types, but also doesn't foreclose on our
ability to do so later.)  So our inclusive chain of box-matching is
now:

```
switch (box) {
    case Box(String s): ...
    case Box(Integer i): ...
    case Box(Object? o): ...
}
```

and it is clear from the source that `o` might be null.  We can think
of `Box(var x)` as using type inference to find the maximal type that
is permitted based on the pattern signature, and then inferring a
nullable type pattern.  So if given:

```
class StringBox {
    StringBox(String s) { ... }
    extractor StringBox(String s) { ... }
}
```

then the pattern `StringBox(var x)` will be equivalent, after
inference, to `StringBox(String? x)`.

## Pattern dominance

We can impose a partial ordering on patterns, called dominance, that
means that any value matched by a dominated pattern is also matched by
the dominating pattern.  We can use this ordering to reject dead
switch arms (as we reject dead `catch` arms today.)  Dominance is
reflexive; `P` always dominates itself (or patterns equivalent to
itself).  Any subtyping conditions used in computing dominance is
computed on raw types; type patterns for `List<?>` and `List<String>`
are considered equivalent.

Examples of dominance include:

 - A constant pattern of type `T` is dominated by a type pattern for
   `T`.
 - A type pattern for `T` is dominated by the nullable type pattern
   for `T`.
 - If `T <: U`, then a type pattern for `T` is dominated by a type
   pattern for `U`.
 - A deconstruction pattern `T(P)` is dominated by a type pattern for
   `T`.  If `T(P)` is total on `T`, then the type pattern `T t` is
   also dominated by `T(P)`.
 - If `T <: U`, then a total deconstruction pattern `T(P)` is
   dominated by a total deconstruction pattern `U(Q)`.
 - If `P` is dominated by `Q`, then `T(P)` is dominated by `T(Q)`.
 - `null` is dominated by any nullable type pattern.
 - All patterns are dominated by the "any" pattern `_` and by `var`
   patterns.

It is a compile-time error to have a `case` label in a `switch` that
cannot match any values.  This includes patterns that are dominated by
prior `case` labels, as well as `case` labels that are dominated by
combinations of prior case labels -- such as a `T?` pattern that
follows a nullable pattern and a `T` pattern.

The `default` case is special.  For switches with reference operands,
`default` effectively means `case Object`, in that it matches
everything but `null`, and for switches with primitive operands, it
effectively means `case _`.  For existing switches, the `default`
clause need not be the last case (in fact, you can even fall _out_ of
a default into a labeled case!), but once we start enforcing dominance
order, this will be confusing.  So for switches that are not "classic"
switches (operand is one of the currently supported types, and all
cases are constant labels), `default` will be treated as either `case
Object` or `case _`, and the dominance order enforced.  (For those who
want `default` to match anything _including_ `null`, that's easy: have a
`case null` arm that falls into `default`, which renders the switch
nullable, or have an explicit `case Object? o` or `case _` arm.)

## Open issues

Some open issues include:

 - **Pattern declaration**.  This will be covered in a separate
document.
 - **Static and instance patterns.** The matching semantics of static
   and instance patterns have been left out largely for simplicity of
   exposition, as they are likely to arrive in a later delivery.
   These raise some questions for name resolution that must be dealt
   with.
 - **Value type patterns.** Similarly, the semantics of type patterns
   for value types have also been left out.
 - **Laziness**.  Laziness is a valuable optimization for some
   patterns, such as deconstruction patterns, but is not applicable to
   all patterns.
 - **Continue**.  It may be desirable to provide semantics to
   `continue` in `switch`, so that one can express the effect of
   guards (and more).


