# Extending switch for pattern matching

#### Gavin Bierman and Brian Goetz, April 2017

This document explores a possible direction for enhancements to
`switch` in the Java language, motivated by the desire to
support [_pattern matching_][pattern-match].  _This is an exploratory
document only and does not constitute a plan for any specific feature
in any specific version of the Java Language._

#### Pattern matching documents

- [Pattern Matching For Java](patterns/pattern-matching-for-java).  Overview of
  pattern matching concepts, and how they might be surfaced in Java.
- [Pattern Matching For Java -- Semantics](patterns/pattern-match-semantics).  More
  detailed notes on type checking, matching, and scoping of patterns and binding
  variables.
- [Extending Switch for Patterns](extending-switch-for-patterns) (this
  document).  An early exploration of the issues surrounding extending pattern
  matching to the `switch` statement.
- [Type Patterns in Switch](patterns/type-patterns-in-switch).  A more up-to-date
  treatment of extending pattern matching to `switch` statements, including
  treatment of nullity and totality.
- [Pattern Matching in the Java Object model](patterns/pattern-match-object-model).
  Explores how patterns fit into the Java object model, how they fill a hole we
  may not have realized existed, and how they might affect API design going
  forward.

## Background

Java inherited its `switch` construct nearly wholesale from C.  It was
designed as a limited mechanism for limited situations; one can only
switch on a small set of types, and one can only have case labels that
exactly match literal constants.  While its range was extended several
times (switching on enums in Java 5, and strings in Java 7), the basic
facility is largely unchanged from C.

As we consider extending `switch` to support a wider variety of types,
and `case` labels to support patterns, it raises some new questions,
such as:

 - What is the scope of binding variables introduced in pattern `case`
   labels?
 - Does fallthrough need to be restricted to make sense with pattern
   `case` labels?
 - Can `switch` be smoothly extended to an expression, and if so, what
   changes need to be made?
 - Do we need additional control flow constructs, like `break` or
   `continue`?
 - Do we need "guard" conditions on patterns?
 - Under what conditions might a `switch` expression without a
   `default` clause be considered exhaustive?

It should be noted that there is a duality between `switch` statements
and a chain of `if-else` statements.  We can use this duality as a
lens through which to evaluate the regularity of extensions to
`switch`.

## Scoping

A `switch` statement today is one big scope; the "arms" of a `switch`
do not constitute individual scopes, unless scoping constructs (such
as introducing a new block) are explicitly used by the author.

The situation of having variable declarations arise from expressions
is new, so it is a reasonable question to ask "What is the scope of of
a binding variable of a pattern match?"  There's also an obvious
answer -- the scope of the statement that encloses the pattern match;
we can just hoist variables into the scope which includes the
statement which includes the match expression:

    if (x matches String s) { ... }

becomes

    String s;
    if (x matches String s) { ... }

However, this seems like one of those "obvious but wrong" answers;
there are going to be places in that scope where the variable is still
not usable (because it is not definitely assigned), and it is likely
that users will want to reuse the same variable name for multiple
bindings in the same scope:

    if (x matches Integer n) { ... }
    else if (x matches Float n) { ... }
    else if (x matches Double n) { ... }

(or the equivalent in a `switch` statement.)  Having to come up with a
unique name for each binding variable, just because the variable has
been hoisted into a broader scope, will be unpopular (and as it turns
out, unnecessary.)

#### Natural scoping for binding variables

The following example illustrates the that "natural" scope of a
binding variables is complex and not necessarily contiguous:

    if (x matches Foo(var y)) { .. y .. }                 // OK
    if (x matches Foo(var y)) { ... } else { .. y .. }    // not OK
    if (x matches Foo(var y) && .. y ..) { ... }          // OK
    if (x matches Foo(var y) || .. y ..) { ... }          // not OK
    if (!(x matches Foo(var y)) && .. y .. ) { ... }      // not OK
    if (!(x matches Foo(var y)) || .. y .. ) { ... }      // OK
    if (!(x matches Foo(var y))) { ... } else { .. y .. } // OK
    if (!(x matches Foo(var y))) { y } else { ... }       // not OK

The above cases are derived from a standard application of _definite
assignment_ rules; we'd like for a binding variable to be in scope
wherever it is definitely assigned, to not be in scope wherever it is
not definitely assigned, and for a binding variable to always be
definitely unassigned at the point of its declaration.

We can construct a set of rules for the natural scope of these
variables.  To start with, we say that each expression _e_ gives rise
to two sets of binding variables, `e.T` and `e.F`, along with rules
for when one or the other of these sets are included in the scope of a
statement or expression, over all the expression forms.  If not
otherwise defined, `e.T` = `e.F` = `{}` -- most expressions (including
all current expression forms) make available no new bindings.  We also
define a set of binding variables to additionally be in scope for
certain expressions or statements via the "include in" clauses below.

If _e_ is `x matches P`:

    e.T = { binding variables from P }
    e.F = { }

If e is `x && y`:

    e.T = union(x.T, y.T)
    e.F = intersection(x.F, y.F)
    include x.T in y

If e is `x || y`:

    e.T = intersection(x.T, y.T)
    e.F = union(x.F, y.F)
    include x.F in y

If e is `x ? y : z`:

    e.T = union(intersect(y.T, z.T), intersect(x.T, z.T), intersect(x.F, y.T))
    e.F = union(intersect(y.F, z.F), intersect(x.T, z.F), intersect(x.F, y.F))
    include x.T in y
    include x.F in z

If e is `(x)`:

    e.T = x.T
    e.F = x.F

If e is `!x`:

    e.T = x.F
    e.F = x.T

We can do the same for statement forms:

For `if (x) y else z`:

    include x.T in y
    include x.F in z

For `if (x) return/throw; z`

    include x.T in return/throw
    include x.F in z

For `while (x) y`:

    include x.T in y

For `for (a; b; c) d`:

    include b.T in c
    include b.T in d

For `switch (x) { ... case P: y; case Q: ... }`

    include binding variables from P in y

Further, union and intersection should be limited to avoid conflicts.
The `union` function should be a disjoint union: it is an error if any
binding varible is present in both sets -- otherwise, expressions like
`x matches Foo(var x) && y matches Bar(var x)` would include two
different variables called `x` in the same scope.  Similarly, for
`intersect`, it is an error if the same binding variable is present in
both sets but with different types.

## Fallthrough and OR patterns

While one could make an argument that fallthrough in `switch` was the
wrong default, the problem fallthrough aims to solve -- treating
multiple items similarly without duplicating the code -- were real,
and are still relevant when our `case` labels get richer.

Patterns that generate binding variables require some refinements to the
above scoping rules.  For example:

    case Foo(int x):
    case Bar(float x):
        s;

would be an error, just as `x matches Foo(int x) || x.matches
Bar(float x)` would be.

However, there's no reason why we can't make this work, with `x` in
scope in `s`:

    case Foo(int x):
    case Bar(int x):
        s;

This is analogous to the disjunction `y matches Foo(int x) || y
matches Bar(int x)`.

Similarly, in:

    case Foo(int x, int y):
    case Bar(int x):
        s;

the binding variable `y` would not be available in `s`, because we
can't rely on it having a value on all control paths, but `x` can
still be available in `s`.  These restrictions are a straightforward
refinement of the scoping rules presented earlier.

A more limited form of fallthrough is OR patterns:

    case P1 || P2:
        s;

Which is equivalent to:

    case P1:
    case P2:
        s;

We might consider prohibiting fallthrough but allowing OR patterns (in
which case we'd probably require that all OR patterns declare exactly
the same set of binding variables.)

#### Guards, compound patterns, and continue

Nested patterns, such as:

    case Point(0, 0):

express _compound conditions_; we're testing that the target is a
`Point`, and that both its `x` and `y` components match the constant
pattern `0`.  While nested patterns are powerful, they have their
limits; we can't easily test for whether a point is, say, on the
diagonal.  We could express this with a _guard_:

    case Point(var x, var y) && x == y:

Alternately, we could express compound conditions by pushing the
subordinate test into the body, and permitting the `continue` control
flow construct in switches, which would indicate we want to break out
of the existing `case` arm, and resume matching at the next `case`
label:

    case Point(var x, var y):
        if (x != y)
            continue;

(Note that nested patterns desugar to guards, and guards desugar to
`continue`, so we are likely to have to implement all these mechanisms
internally anyway.)

#### Dead code

In some cases, the compiler may be able to prove that a case is
unreachable, such as:

    switch (x) {
        case Comparable c: ... break;
        case Integer i: // can't reach this
    }

In these cases, the compiler will issue an error (just as with
unreachable `catch` clauses.)

## Switch expressions

The other major direction in which we would like to extend `switch` is
to given it an expression form:

    float overtimeFactor = switch (day) {
        case SATURDAY -> 1.5;
        case SUNDAY -> 2;
        default -> 1;
    }

While statement switches need not be exhaustive (just as `if`
statements need not have an `else`), expression switches must be (as
the expression must evaluate to something.)  Exhaustiveness can always
be provided via a `default` arm, but sometimes we may want to do
better.  The compiler can use class hierarchy information, as well as
sealing information, to prove exhaustiveness.  (Since the type
hiearchy can change between compile and run time, the compiler will
still want to insert a catch-all throwing `default` even if it deems
the analysis exhaustive.)

Unrestricted fallthrough makes less sense in an expression `switch`,
but OR patterns still do:

    int days = switch (month) {
        case JANUARY
             || MARCH
             || MAY
             || JULY
             || AUGUST
             || OCTOBER
             || DECEMBER -> 31;
         case FEBRUARY -> 28;
         case APRIL
             || JUNE
             || SEPTEMBER
             || NOVEMBER -> 30;
    };

A switch expression is a _poly expression_, and pushes its target type
down into the switch arms (just as we do with conditional
expressions.)

#### Mixing statements and expressions

While the common case with a switch expression is that the RHS of a
case label is a single expression, occasionally the result may not be
constructible in this way (or construction of the result might require
side-effects, such as debugging output).  Other languages usually
handle this with _block expressions_; we can construct a limited form
of block expression for use in expression `switch` by coopting the
`break` keyword, as in these examples:

    case String s -> {
        System.out.println("It's a string!");
        break s.toUpperCase();
    }

    case Flooble f -> {
        FloobleDescriptor fd = new FloobleDescriptor();
        fd.setFlooble(f);
        break fd;
    }

There is some potential ambiguity between label-break and result-break
here, but working these out is practical.

#### Throw expressions

It is not uncommon that one or more arms of a switch expression will
result in a transfer-of-control operation, such as:

    int size = switch (x) {
        case Collection c -> c.size();
        case String s -> s.length();
        default -> throw new IllegalArgumentException(...);
    }

Even though `throw` is a statement, not an expression, the intent here
is clear, so we want to allow `throw` (and possibly other
transfer-of-control operations) in this context.

#### Targetless switch

In the theme of elevating `switch` as the generalization of the
ternary conditional operator, we may also wish to allow a simplified
form of `switch` where there is no switch target, and all case labels
are boolean expressions:

    String fizzbuzz(int n) {
        boolean byThree = n % 3 == 0;
        boolean byFive = n % 5 == 0;
        return switch {
            case byThree && byFive -> "fizzbuzz";
            case byThree -> "fizz";
            case byFive -> "buzz";
            default -> Integer.toString(n);
        }
    }



[pattern-match]: patterns/pattern-matching-for-java
