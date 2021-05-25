# Pattern Matching for Java -- Semantics
#### Gavin Bierman and Brian Goetz (Updated August 2020)

This document explores a possible direction for supporting _pattern
matching_ in the Java Language.  _This is an exploratory document only
and does not constitute a plan for any specific feature in any
specific version of the Java Language._  This document also may
reference other features under exploration; this is purely for
illustrative purposes, and does not constitute any sort of plan or
commitment to deliver any of these features.

#### Pattern matching documents

 - [Pattern Matching For Java](pattern-matching-for-java).  Overview of
   pattern matching concepts, and how they might be surfaced in Java.
 - [Pattern Matching For Java -- Semantics](pattern-match-semantics)  (this
   document).  More detailed notes on type checking, matching, and scoping of
   patterns and binding variables.
 - [Extending Switch for Patterns](extending-switch-for-patterns).  An early
   exploration of the issues surrounding extending pattern matching to the
   `switch` statement.
 - [Type Patterns in Switch](type-patterns-in-switch).  A more up-to-date
   treatment of extending pattern matching to `switch` statements, including
   treatment of nullity and totality.
 - [Pattern Matching in the Java Object model](pattern-match-object-model).
   Explores how patterns fit into the Java object model, how they fill a hole we
   may not have realized existed, and how they might affect API design going
   forward.


## Types of patterns

There are multiple types of patterns.  We will use a denotation for purposes of
exposition in this document, but the final denotation in the language, and which
of these pattern types are eventually supported, may be different than presented
here.

 - _Type patterns_, denoted by `T t`;
 - The _var_ pattern, denoted by `var x`;
 - The _ignore_ pattern, denoted by `_`;
 - _Constant patterns_ (including the _null constant pattern_) denoted by
   lexical literals or by names of constant variables (JLS 4.12.4) or enum
   constants;
 - _Deconstruction patterns_ for a type `T`, denoted by `T(P*)`, where `P*` is a
   sequence of nested patterns;
 - _Declared patterns_, denoted by `id(P*)` or `C.id(P*)`.  

In any pattern match, there is always a pattern and a _match target_.  For
`instanceof` the match target is the LHS operand; for `switch` it is the operand
in the switch header.  The match target has both a static and dynamic type; both
may be used in determining whether the pattern matches.

#### Type checking

Certain pattern matches can be rejected at compile time based strictly on static
checks, such as:

```
String s = "Hello";
if (s instanceof Integer i) { ... }
```

The target `s` is a `String` and the pattern `Integer i` only matches
expressions of type `Integer`.  Since both are final classes,  we know
statically that the match cannot succeed and it is rejected, just as we would
reject an attempt to cast an `Integer` to a `String`.

We can define an _applicability_ relation between a pattern and a static type,
which determines if the pattern is applicable to a target of that type.  (In all
the rules that follow, "castable" means "cast-convertible without an unchecked
warning.")

 - The ignore pattern and the `var` pattern are applicable to all types.
 - The `null` constant pattern is applicable to all reference types.
 - A numeric literal constant pattern for `n` without a type suffix is
   applicable to any primitive type `P` to which `n` is assignable (within the
   numeric range of `P`), and to `P`'s box type, and its supertypes.
 - Other constant patterns of primitive type `P` are applicable to `P` and to
   types `U` which are castable to `P`'s box type, and its supertypes.
 - Constant patterns of reference type `T` are applicable to types `U` which are
   castable to `T`.
 - The type pattern `P p` for a primitive type `P` is applicable to `P`.
 - The type pattern `T t` for a reference type `T` is applicable to types `U`
   which are castable to `T`.
 - A deconstruction pattern `D(P*) [d]`, is applicable to types `U` which are
   castable to `D`.
 - A declared pattern `p(P*)` is applicable to types `U` which are castable to
   the target type of `p`.  (An example of a declared pattern is
   `Optional.of(var x)`, which would be declared in `Optional` and has a target
   type of `Optional<T>`.)

If a pattern is not applicable to the target type, a compilation error results.
Generic type patterns are permitted; this is a relaxation of the current
semantics of the `instanceof` operator, which requires that the type operand is
reifiable.  However, when determining applicability involves cast conversions,
the pattern is not applicable if the cast conversion would be _unchecked_.  So
it is allowable to say

```
List<Integer> list = ...
if (list instanceof ArrayList<Integer> a) { ... }
```
but not

```
List<?> list = ...
if (list instanceof ArrayList<String> a) { ... }
```
as a cast conversion from `List<?>` to `ArrayList<String>` would be unchecked.

For deconstruction and declared patterns, which contain nested sub-patterns, we
further required that the nested sub-patterns are applicable to the static type
of the corresponding binding variables of the enclosing pattern.  So if class
`D` has a deconstructor `D(String s, int y)`, a pattern `D(P, Q)` is only
applicable if `P` is applicable to `String` and `Q` is applicable to `int`.

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

Some patterns are _total_ on certain target types, which means no dynamic type
test is required to determine matching.  The "ignore" and "var" patterns
are total on all types.  A type pattern `T t` is total on any type `U <: T`.  A
deconstruction `D(P*)` is total on `U` if `U <: T` and each `Pi` is total on the
type of the corresponding binding variable of `D`.

We define a _matches_ relation between patterns and expressions as follows.

 - The "ignore" and `var` patterns match anything, including null.
 - A type pattern `T t` matches `e` if `e instanceof T`, and additionally
   matches `null` if the type pattern is total on the target type.
 - The `null` constant pattern matches `e` if `e == null`.
 - A primitive constant pattern `c` of type `P` matches `e : P` if `c` is equal
   to `e`, and matches `e : T` if `e` is an instance of `P`'s box type, and `c`
   equals `unbox(e)`.  Equality is determined by the appropriate primitive `==`
   operator, except for `float` and `double`, where equality is determined by
   the semantics of `Float::equals` and `Double::equals`.
 - A reference constant pattern `c` of type `T` matches `e` if `c.equals(e)`.
 - A deconstruction pattern `D(Pi...)` matches `e` if `e instanceof T`, and for
   all _i_, `Pi` matches the _i_'th component extracted by `D`.

The only patterns that are nullable are the null constant pattern, the "ignore"
and `var` patterns, and total type patterns.  (The latter three are actually
equivalent -- they are all variants of "any" patterns.)

Deconstruction (and declared) patterns can contain nested sub-patterns.  If a
deconstructor `D` has a single binding variables of type `T`, then `x` matches
`D(P)` if and only if `x` matches `D(var alpha)` and `alpha` matches `P`.  In
such a match, the target of `D(P)` is `x`, but the target of `P` is the
synthetic variable `alpha`.

#### Binding variables

Some patterns define variables which will be bound to components extracted from
the target if the match succeeds.  These variables have types defined as
follows:

 - For a type pattern `T t`, the binding variable `t` has type `T`.
 - For a deconstruction pattern `D(P*) d`, the binding variable `d` has type
   `D`.
 - For a pattern `var x` on a target of type `U`, the binding variable `x` has
   type `U`.  (Patterns in nested context get their target types from the type
   of the corresponding binding variable in the declaration of the enclosing
   pattern.)

In each of these cases, the pattern variable is initialized to the match target,
after casting or converting to the target type, when a successful match is made.

## Pattern-aware constructs

Several constructs, such as `instanceof` and `switch`, will be made
pattern-aware.  The syntax of `instanceof` is extended as follows:

```
<expression> instanceof <reifiable-type>
<expression> instanceof <pattern>
```

The `instanceof` operator evaluates to `false` if the pattern is non-nullable
and the expression operand is `null`.  Because the only nullable patterns are
the `null` constant pattern and the total patterns (ignore, `var`, total type
patterns), all of which are somewhat silly to use in `instanceof`, we may
restrict the pattern operand of `instanceof` to exclude nullable patterns, to
avoid confusion with the current behavior of "`instanceof` which is always false
on `null`.

Currently, `switch` only supports a limited range of operand types; when it
becomes pattern aware, it can accept any operand type (but patterns are type
checked for applicability with the operand type), and patterns may be used as
`case` labels.  

A _pattern bind_ statement is a generalization of a local variable declaration,
and is written

```
P = e
```

The pattern in a pattern bind statement must have at least one binding variable,
and must be total on the type of `e`.  If `P` is not nullable, a pattern bind
may throw `NullPointerException` when `e==null`.  A pattern bind statement
with a type or `var` pattern is equivalent to a local variable declaration;
additionally pattern bind statements may use deconstruction patterns:

```
Point(var x, var y) = component.getCenter();
```

which declares new locals `x` and `y`.

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

Further, we may eventually want to support _merging_ of pattern bindings, as in:

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
|P = e;<br>                    |e.T in S                                 |
|S;                            |                                         |
|                              |                                         |
|                              |                                         |
+------------------------------+-----------------------------------------+

With these rules, we are able to get the full desired scoping with
awareness of whether we throw out of `if` blocks, `break` out of
`while` loops, or fall out of case groups.

As mentioned already, the motivation for flow-sensitive scoping is so
we can reuse pattern variable names when they are not in scope:

```
switch (e) {
    case RedBox(int height) -> System.out.printf("Red(%d)", height);
    case BlueBox(int height) -> System.out.printf("Blue(%d)", height);
}
```

And we can even (if we want to) merge pattern variables in switch fallthrough:

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

Because the scoping of pattern bindings is not exactly the same as for local
variables, we must describe the interaction between pattern bindings and other
kinds of variables (locals, fields.)  We adopt the same rules for shadowing as
we do for locals -- binding variables may not shadow other binding variables or
locals (or vice-versa), but they may shadow fields.  The unusual shape of the
scopes of binding variables may occasionally lead to scoping confusion such as
the following, but it was deemed that "curing" this "problem" was probably worse
than the disease.

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

Nullability is a complex topic fraught with tradeoffs.  The  existing constructs
have pre-existing notions about nullability; currently `instanceof` always says
`false` on null, and `switch` always throws on `null`.

These were the right defaults given the role of these constructs in the language
as originally designed -- `instanceof` was solely a dynamic type test (matching
the behavior of the `INSTANCEOF` bytecode), and `switch` only allows us to
compare for equality with a constant, on a limited number of types.  However, as
these constructs become dramatically richer when we upgrade them to support
patterns, we may need to (compatibly) refine these behaviors.

Source compatibility prevents us from changing these for code that is currently
valid, but we also want to avoid extending the semantics of these constructs in
a too-surprising way.  Along the way, we have encountered strong and diverse
opinions about how `null` should be handled (ranging from "null is just another
value" to "kill it dead, now, dead, now.")  Our approach is to avoid picking
winners and losers here, and to provide a set of primitives that can equally
well support null-avoiding and null-tolerant coding styles.

The "obvious" choice is to simply continue with the null semantics we have now.
But the current story scales poorly to _nested patterns_.  If we have a class:

```
class Box<T> {
    private final T t;

    public Box(T t) { this.t = t; }
    public deconstructor Box(T t) { t = this.t; }
}
```

The author of the class has decided that `new Box(null)` is an entirely
reasonable value for this class; the language should not be second-guessing this
design choice.  So it would be unreasonable to prevent `Box(_)` from matching
`Box(null)`, for example; if we're matching "any box", then we should match any
box.  The "ignore" pattern (if we support it at all) doesn't let us bind to its
target, so we might also want to use a nested `var` or type pattern here.  And
the same argument applies to `Box(var x)` as to `Box(_)`; we're saying "any box,
and please bind `x` to whatever it is a box of."   And we can extend this
argument to a nested total type pattern as well -- in fact, we sort of have to,
or we risk undermining the claim that `var` is "just type inference."   As with
`var` in variable declarations, we want the choice of inference or not to be
made on the basis of what the author finds most readable -- and so the pattern
`var x` must just be type inference for some type pattern `T x`, and the
sensible type to infer is the target type (being the narrowest type that is
total on the target.)  So we conclude that total type patterns and `var`
patterns are equivalent, and therefore are both nullable, at least in a nested
context.

But, having different semantics for nested vs top-level context would be even
more complicated; it is simplest to think about nesting when it is  mere
"unrolling".  So the natural thing to do is say that these patterns just match
null.  Which leaves us with how to resolve this with the fact that `instanceof`
and `switch` have pre-existing opinions about nulls.

One thing we could do here is nothing; just let these behaviors continue.  And
for `instanceof`, we probably should do that -- but to accomplish that, we have
to ban nullable patterns in `instanceof` (that prevents `instanceof Object` and
`instanceof Object o` from meaning different things, which would be confusing).
Which is probably fine, since `e instanceof var x` is kind of a silly way to
write `var x = e`.

For `switch`, though, we have a harder choice.  We could let `switch` keep
throwing -- but this has consequences too.  For example, it means giving up the
ability to refactor a switch of nested patterns:

```
switch (o) {
    case D(P): A
    case D(Q): B
}
```

to the obvious nested switch:

```
switch (o) {
    case D(var x):
       switch (x) {
           case P: A
           case Q: B
       }
}
```

because doing so would cause NPE if `x==null`.  Alternately, we can refine the
semantics of `switch` to only throw on `null` when no nullable patterns -- which
means no `case null` (which must always be first) and no total pattern (which
must always be last).  Similarly, we would like to be able to refactor chains
of `instanceof` and `switch` to each other.  

#### Nullity -- some false starts

Our path for determining the semantics of various patterns with respect to null
has been a fairly winding one.  While it is impractical to rehash the entire
journey, let's look at some specific examples.

We initially liked the idea that a type pattern `T t` would match anything that
is assignment-compatible to `T`, including `null`.  But this runs into a few
problems.

First, it means that refactoring between `switch` and `instanceof` is painful,
because `instanceof T t` would not be consistent with `instanceof T` for any
`T`.  (Some might assume the problem here is that we're trying to reuse
`instanceof`, but having a `matches T t` that behaves similarly but subtly
differently from `instanceof T` is no better.)

Further, having type patterns match nulls would result in surprising order
dependency.  If we have:

```
switch (box) {
    case Box(String s): ...
    case Box(Integer i): ...
    case Box(_): ...
}
```

and type patterns matched null, the nulls would fall into the first `case`.  Not
only is this surprising and arbitrary, but its even more surprising that if we
reordered the first two cases -- which surely look disjoint and therefore safely
reorderable -- it would subtly change the behavior of the program, because now
`Box(Integer)` would get the nulls.

Additionally, if `T t` were to match nulls for arbitrary `T`, this would likely
lead to many unexpected NPEs.  For example, it would be easy to forget that one
can't safely use `s` in the following example:

```
if (x instanceof String s)
    printf("String of length %d%n", s.length());
```

If the type pattern `String s` matched `null`, this code would NPE in the body
of the `if`, since we'd be dereferencing a null `String` reference.  This would
be a sharp edge that cuts over and over, because the intent of the code above
is to perform a dynamic type test.  

At this point, readers should ask: why would it be different for total patterns?
And the reason is: we don't use total patterns in `instanceof` at all (so it
doesn't matter there), and when we use them in `switch`, we are not using them
as dynamic type tests -- we are using them as catch-alls.

Another idea that didn't work out is to say `var x` is nullable but `Object x`
never is.  This is doable, but it has a big cost -- it violates the  sensible
intuition that `var` is "just" type inference, not a semantically different
thing.  We want the rules for nullity to be robust to a variety of refactorings
that seem like they should be the same thing -- so we want them to be the same
thing:

 - That `var x` is just type inference for some type pattern `T t`;
 - A chain of `if (x instanceof P)` ... `else if (x instanceof Q)` can be
   refactored to or from a switch with cases P and Q;
 - A sequence of switch cases `P(Q)`, `P(R)` can be refactored to a single case
   `P p` with a nested switch on `p` with cases `Q` and `R`.

Each of the ideas discussed and rejected in this section would have fallen
afoul of one of these goals.

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

 - A constant pattern for a constant of type `T` is dominated by a type pattern
   for `T`.
 - If `T <: U`, then a type pattern for `T` is dominated by a type pattern for
   `U`.
 - A deconstruction pattern `T(P*)` is dominated by a type pattern for `T`.  If
   `T(P*)` is total on `T`, then the type pattern `T t` is also dominated by
   `T(P*)`.
 - If `T <: U`, then a total deconstruction pattern `T(P*)` is dominated by a
   total deconstruction pattern `U(Q*)`.
 - If `P` is dominated by `Q`, then `T(P)` is dominated by `T(Q)`.
 - `null` is dominated by any nullable type pattern.
 - A guarded pattern `P when g` is dominated by `P`.  
 - All patterns are dominated by the "ignore" pattern `_` and `var` patterns.

It is a compile-time error to have a `case` label in a `switch` that cannot
match any values.  This includes patterns that are dominated by prior `case`
labels, as well as `case` labels that are dominated by combinations of prior
case labels (which can arised from type patterns involving sealed classes.)

The `default` case is special, and to some extent legacy.  It matches everything
but `null` that is not matched by some other case (before or after).  For
existing switches, the `default` clause need not be the last case (in fact, you
can even fall _out_ of a default into a labeled case!), but once we start
enforcing dominance order, this will be confusing, since we'd like for switches
to be seen as equivalent to an if-else chain (with potentially optimized
dispatch).  So for switches that are not "classic" switches (operand is one of
the currently supported types, and all cases are constant labels), `default`
must come last, and continues to mean "everything but null."  In reality,
though, `default` is far less useful in pattern switches because it doesn't
support a binding variable, so we will probably prefer type patterns instead.
