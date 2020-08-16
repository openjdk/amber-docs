# Type patterns in switch
#### Brian Goetz, Aug 2020

This document describes a possible approach for the next phase of  _pattern
matching_ -- adding type patterns to the `switch` statement.  This builds on the
work of [JEP 375][jep375].  _This is an exploratory document only and does not
constitute a plan for any specific feature in any specific version of the Java
Language._

[JEP 305][jep305] introduced the first phase of [pattern matching][patternmatch]
into the Java language, later refined by [JEP 375][jep375].  It was deliberately
limited, focusing on only one kind of pattern (type test patterns) and one
linguistic context (`instanceof`).  Having introduced the concept to Java
developers, we can now extend both the kinds of patterns and the linguistic
context where patterns are used.

The obvious next context in which to introduce pattern matching is `switch`;  a
switch using patterns as `case` labels can replace `if .. else if` chains with a
more direct way of expressing a multi-way conditional.   Unfortunately, `switch`
is one of the most complex, irregular constructs we have in Java, so we must
teach it some new tricks while avoiding some existing traps.  Such tricks and
traps may include:

**Typing.**  Currently, the operand of a `switch` may only be one of the
integral primitive types, the box type of an integral primitive, `String`, or an
`enum` type.  (Further, if the `switch` operand is an `enum` type, the `case`
labels must be _unqualified_ enum constant names.)  Clearly we can relax this
restriction to allow other types, and constrain the case labels to only be
patterns that are applicable to that type, but it may leave a seam of "legacy"
vs "pattern" switch, especially if we do not adopt bare constant literals as the
denotation of constant patterns.  (We have confronted this issue before with
expression switch, and concluded that it was better to rehabilitate the `switch`
we have rather than create a new construct, and we will make the same choice
here, but the cost of this is often a visible seam.)

**Parsing.**  The grammar currently specifies that the operand of a `case` label
is a `CaseConstant`, which casts a wide syntactic net, later narrowed with
post-checks after attribution.  This means that, since parsing is done before we
know the type of the operand, we must be watchful for ambiguities between
patterns and expressions (and possibly refine the production for `case` labels.)

**Nullity.**  The `switch` construct is currently hostile to `null`, but some
patterns do match `null`, and it may be desirable if nulls can be handled within
a suitably crafted `switch`.  (It is always tempting to say "sorry, nulls not
allowed", but to not refine null handling here will show up elsewhere, such as
an inability to refactor between certain `if-else-if` chains and switches.)

**Exhaustiveness.**  For switches over the permitted subtypes of sealed types,
we will want to be able to do exhaustiveness analysis.  (When we get to  nested
patterns, we will want to do this for nested patterns too --  if `Circle`  and
`Rect` are exhaustive on `Shape`, then `Box(Circle c)` and `Box(Rect r)` are
exhaustive on `Box<Shape>`.)

**Fallthrough.**  Fallthrough is everyone's least favorite feature of `switch`,
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

#### Translation

Switches on primitives and their wrapper types are translated using the
`tableswitch` or `lookupswitch` bytecodes; switches on strings and enums are
lowered in the compiler to switches involving hash codes (for strings) or
ordinals (for enums).

For switches on patterns, we would need a new strategy, one likely built on
`invokedynamic`, where we lower the cases to a densely numbered `int` switch,
and then invoke a classifier function with the operand which tells us the first
case number it matches.  So a switch like:

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

A symbolic description of the patterns is provided as the bootstrap argument
list, which builds a decision tree based on analysis of the patterns and their
target types.

At the same time, we may wish to switch to an `invokedynamic`-based
implementation for `String` and `Enum` switch as well; the static desugaring of
these in the compiler is complex, and a dynamic approach offers the opportunity
for performance improvement in the future without having to recompile code.

#### Guards

No matter how rich our patterns are, it is often the case that we will want
to provide additional filtering on the results of a pattern.  If we do so
in `instanceof`, there's no problem; it is easy to join multiple boolean
expressions with `&&`:

```
if (shape instanceof Cylinder c && c.color() == RED) { ... }
```

On the other hand, in a `case` label, we do not currently have this opportunity.
Worse, the semantics of `switch` mean that once a `case` label is selected,
there is no way to say "oops, forget it, keep trying from the next label".

It is common in languages with pattern matching to support some form of "guard"
expression, which is a boolean expression that conditions whether the case
matches, such as:

```
case Point(var x, var y)
    __where x == y: ...
```

There is a good reason guards are so common in languages with pattern matching;
without them, a 50-way switch for which one arm needs to represent a filter
condition that can't be expressed as a pattern would have to refactor to an
`if-else` chain, which results in less readable and more error-prone code.

Syntactic options (and hazards) for guards abound; users would probably find it
natural to reuse `&&` to attach guards to patterns; `C#` has chosen `when` for
introducing guards; we could use `case P if (e)`, etc.  Whatever surface syntax
we pick here there is a readability risk,  as the more complex guards are, the
harder it is to tell where the case label ends and the "body" begins.  (And
worse if we allow switch expressions inside guards, which we shouldn't do.)
Bindings from the `case` pattern would have to be available in guard
expressions.

An alternative to boolean guards is to allow an imperative `continue` statement
in `switch`, which would mean "keep trying to match from the next label."  Given
the existing semantics of `continue`, this is a natural extension, but since
`continue` does not currently have meaning for switch, some work would have to
be done to disambiguate continue statements in switches enclosed in loops.  This
imperative alternative is strictly more expressive than most reasonable forms of
declarative guards, but users are likely to prefer the declarative version,
which more cleanly separates the dispatch criteria from the consequences.

#### Missing primitive types

The set of primitives we can use today in `switch` is limited to the integral
numeric primitive types; this includes `char` but leaves out `float`, `double`
and `boolean`.  It is understandable why these were left out, as they are not
all that useful, but as `switch` gets more sophisticated, their omission becomes
an impediment to refactoring.  Suppose `P` is a pattern that has one binding,
and `Q` and `R` are patterns that are compatible with the type of that binding.
We would like it always to be the case that we can freely refactor:

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
to do this refactoring.  (Adding these types isn't hard, especially if we are
free to use an `indy`-based translation for these switch types.)  We don't need
to do this immediately, though; this becomes more urgent when we're ready to do
_nested_ patterns.

## Nulls

Almost no language design exercise is complete without some degree of wrestling
with `null`.  As we define more complex patterns than simple type patterns, and
extend constructs such as `switch` (which have existing opinions about nullity)
to support patterns, we need to have a clear understanding of which patterns are
nullable, and separate the nullity behaviors of patterns from the nullity
behaviors of those constructs which use patterns.  For the immediately next
phase -- type patterns in `switch` -- much of this will seem like borrowing
trouble, but if we want to be able to consistently evolve to support
deconstruction and nested patterns (which we do), we have to tackle this now.

Nullity in pattern matching has a number of easily-tangled concerns:

 - **Construct nullability.**  Constructs to which we want to add pattern
   awareness (`instanceof`, `switch`) already have their own opinion about
   nulls.  Currently, `instanceof` always says false when presented with a
   `null`, and `switch` always NPEs.  We may wish to refine these rules in some
   cases, but of course we are constrained by compatibility with code that one
   could write today.
 - **Pattern nullability.**  Some patterns clearly would never match `null`
   (such as deconstruction patterns), whereas others (an "any" pattern, and
   surely a `null` constant pattern) might make sense to match null, and it
   might even be quite surprising if they didn't.
 - **Refactoring friendliness.**  There are a number of cases that we would like
   to freely refactor back and forth, such as certain chains of `if ... else if`
   with switches, or switches with nested patterns to nested switches.
 - **Nesting vs top-level.**  The "obvious" thing to do at the top level of a
   construct is not always the "obvious" thing to do in a nested construct.
 - **Totality vs partiality.**  When a pattern is partial on the operand type
   (e.g., `case String` when the operand of switch is `Object`), it is almost
   never the case we want to match null (except in the case of a `null` constant
   pattern), whereas when a pattern is total on the operand type (e.g., `case
   Object` in the same example), it is more justifiable to match null.
 - **Inference.**  A `var` pattern should be understandable as simply being
   inference for a type pattern, rather than some possibly-non-denotable union.

As a starting example, consider:

```
record Box(Object o) { }

Box box = ...
switch (box) {
    case Box(Chocolate c):
    case Box(Frog f):
    case Box(var o):
}
```

The `Box` class expresses no opinions about what it holds; `null` is as good a
value as any.  And this has to be OK; a pattern matching construct should not
express an opinion about whether bindings can be null or not.

Should any of these cases match `Box(null)`?  It would surely be confusing and
error-prone for either of the _first two_ patterns to do so.  Given that
`Chocolate` and `Frog` have no type relation, it should be perfectly safe to
reorder the two, and users will surely assume that this is a safe refactoring
(as it should be).  On the other hand, the last pattern does seem obviously
total on boxes.  In fact, it would be terrible if there were _no_ way to say
"Match any `Box`, even if it contains `null`.  (While one might initially think
this could be repaired with OR patterns, imagine that destructuring a `Box`
yielded _n_ bindings -- we'd need to OR together _2^n_ patterns, with complex
merging, to express all the possible combinations of nullity.)  It is hard to
escape the conclusion that the last pattern is intended to match any `Box` --
including those that contain `null`.

Scala and C# took the approach of saying that `var` patterns are not just type
inference, they are "any" patterns -- so `Box(Object o)` matches boxes
containing a non-null payload, where `Box(var o)` matches all boxes.  This
means, unfortunately, that `var` is not mere type inference -- which complicates
the role of `var` in the language considerably.  Users should not have to choose
between the semantics they want and being explicit about types; these should be
orthogonal choices.  The above `switch` should be equivalent to:

```
Box box = ...
switch (box) {
    case Box(Chocolate c):
    case Box(Frog f):
    case Box(Object o):
}
```

and the choice to use `Object` or `var` should be solely one of whether the
manifest types are deemed to improve or impair readability.

#### Construct and pattern nullability

Currently, `instanceof` always says `false` on `null`, and `switch` always
throws on `null`.  Whatever null opinions a construct has, these are applied
before we even test any patterns.  We need to keep the notion of "what does a
construct do with null" separate from "does a pattern match null."

The above examples about `Box(var x)` and `Box(Object o)` lead pretty squarely
to the conclusion that `Box(Object)` should also match any box.  But if `P(Q)`
is merely a shorthand for `P(var x) && x matches Q`, then this leads us to the
conclusion that `Object o` also must match null.

We can formalize the intuition outlined above as: type patterns that are _total_
on their target operand (`T t` on an operand of type `U`, where `U <: T`) match
null, and non-total type patterns do not, and `var` patterns infer to type
patterns based on the context in which they are used.  If our pattern `Box`
above yields a binding of type `Object`, then in `Box(var x)`, the `var` pattern
is inferred to be `Object x`.  (Equivalently, we can frame this as `var`
patterns are "any" patterns, and a type pattern that is total on its operand
type is also an "any" pattern.)  In our `Box` example, this means that the last
case (whether written as `Box(var o)` or `Box(Object o)`) matches all boxes,
including those containing null (because the nested pattern is total on the
nested operand), but the first two cases do not.

Additionally, we may want a `null` constant pattern that matches only null.  As
we will see, the "any" pattern and the null constant pattern are the _only_
nullable patterns.

For a `case` label with a pattern, it doesn't matter whether the pattern matches
null or not if the switch is going to throw before we test any patterns.  But,
if we retain the current absolute hostility of `switch` to nulls, we can't
trivially refactor from

```
switch (o) {
    case Box(Chocolate c):
    case Box(Frog f):
    case Box(var o):
}
```
to

```
switch (o) {
    case Box(var contents):
        switch (contents) {
            case Chocolate c:
            case Frog f:
            case Object o:
        }
    }
}
```

because the inner `switch(contents)` would NPE before we tried to match any of
the patterns it contains.  Instead, the user would explicitly have to do an `if
(contents == null)` test, and, if the intent was to handle `null` in the same
way as the `Object o` case, some duplication of code would be needed.  We can
address this sharp corner by slightly (but compatibly) relaxing the
null-hostility of `switch` as described below.

A similar sharp corner emerges in the decomposition of a nested pattern `P(Q)`
into `P(alpha) & alpha instanceof Q`; while this is intended to be a universally
valid transformation, if P's 1st binding variable might be null and Q is total,
this transformation would not be valid because of the existing (mild)
null-hostility of `instanceof`.  Again, we may be able to address this by
adjusting the rules surrounding `instanceof` slightly.

## Generalizing switch

The refactoring examples above motivate why we might want to relax the
null-handling behavior of `switch`.  On the other hand, the one thing the
current behavior has going for it is that at least the current behavior is easy
to reason about; it always throws when confronted with a `null`.  Any relaxed
behavior would be more complex; some switches would still have to throw (for
compatibility with existing semantics), and some (which can't be expressed
today) might accept nulls.  This is a tricky balance to achieve, but I think we
have found a good one.

A starting point is that we don't want to require readers to do an _O(n)_
analysis of each of the `case` labels just to determine whether a given switch
accepts `null` or not; this should be an _O(1)_ analysis.  (We also do not want
to introduce a new flavor of `switch`, such as `switch-nullable`; this might
seem to fix the proximate problem but would surely create others.  As we've done
with expression switch and patterns, we'd rather rehabilitate `switch` than
create an almost-but-not-quite-the-same variant.)

Let's start with the null pattern, which we'll spell for sake of exposition
`case null`.  What if you were allowed to say `case null` in a switch, and the
switch would do the obvious thing?

```
switch (o) {
    case null -> System.out.println("Ugh, null");
    case String s -> System.out.println("Yay, non-null: " + s);
}
```

Given that the `case null` appears so close to the `switch`, it does not seem
confusing that this switch would match `null`; the existence of `case null` at
the top of the switch makes it pretty clear that this is intended behavior.  (We
could further restrict the null pattern to being the first pattern in a switch,
to make this clearer.)

Now, let's look at the other end of the switch -- the last case.  What if the
last pattern is a total pattern?  (Note that if any `case` has a total pattern,
it _must_ be the last one, otherwise the cases after that would be dead, which
would be an error.)  Is it also reasonable for that to match null?  After all,
we're saying "everything":

```
switch (o) {
    case String s: ...
    case Object o: ...
}
```

Under this interpretation, the switch-refactoring anomaly above goes away.

The direction we're going here is that if we can localize the null-acceptance of
switches in the first (is it `case null`?) and last (is it total?) cases, then
the incremental complexity of allowing _some_ switches to accept null might be
outweighed by the incremental benefit of treating `null` more uniformly (and
thus eliminating the refactoring anomalies).  Note also that there is no actual
code compatibility issue; this is all mental-model compatibility.

So far, we're suggesting:

 - A switch with a constant `null` case will accept nulls;
 - If present, a constant `null` case must go first;
 - A switch with a total (any) case also accepts nulls;
 - If present, a total (any) case must go last.

#### What about `default`?

We haven't yet talked about the role of `default` in pattern switches.  Is it an
"any" pattern?  Compatibility constrains us to treat `default` as not matching
`null`, so we cannot treat `default` as an "any" pattern.  (The `default` case
also has the irritating historical anomaly that it can appear anywhere in the
switch, not last.)

One could view the proposed changes as not changing the behavior of `switch`,
but of the `default` case of `switch`.  We can equally well interpret the
current behavior as:

 - `switch` always accepts `null`, but matching the `default` case of a `switch`
   throws `NullPointerException`;
 - any `switch` without a `default` case has an implicit do-nothing `default`
   case.

If we adopt this change of perspective, then `default`, not `switch`, is in
control of the null-rejection behavior -- and we can view these changes as
adjusting the behavior of `default`.  So we can recast the proposed changes as:

  - Switches accept null;
  - A constant `null` case will match nulls, and must go first;
  - A total switch (a switch with a total `case`) cannot have a `default` case;
  - A non-total switch without a `default` case gets an implicit do-nothing
    `default` case;
  - Matching the (implicit or explicit) `default` case with a `null` operand
    always throws NPE.

The main casualty here is that the `default` case does not mean the same thing
as `case var x` or `case Object o`.  We can't deprecate `default`, but for
pattern switches, it becomes much less useful.  (We probably should further
require that, in pattern switches, the `default` case comes last if it is
present -- though we may wish to go farther and only allow `default` on switches
that contain no patterns, to make it clear that it is a legacy construct.)

#### Deconstruction patterns never match null

So far, we've declared all patterns, except the `null` constant pattern and the
total (any) pattern, to not match `null`.  What about patterns that are
explicitly declared in code?  We don't have these yet, but we plan to.  It turns
out we can rule out these matching `null` fairly easily.

We anticipate having a distinguished form of _deconstruction pattern_ (dual to a
constructor), and also possibly additional patterns that are declared more like
methods (such as `Optional.of(var x)`.)  For deconstruction  patterns, the match
target becomes the receiver; method bodies are never expected to deal with the
case where `this == null`, so it is reasonable to say that deconstruction
patterns do not match null.

For static method-like patterns, it is conceivable that they could match `null`,
but this would put a fairly serious burden on writers of such patterns to check
for `null` -- which they would invariably forget, and many more NPEs would
ensue.  (Think about writing the pattern for `Optional.of(T t)` -- it would be
overwhelmingly likely we'd forget to check the target for nullity.)  So there is
a strong argument to simply say "declared patterns never match null", to not put
writers of such patterns in this situation.

So, only the top and bottom patterns in a switch could match null; if the top
pattern is not `case null`, and the bottom pattern is not total, then the switch
throws NPE on null, otherwise it accepts null.

#### Constant patterns

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
or an expression?), at least for readers if not for parsers.  And, constant
patterns, outside of their top-level use in `switch`, are just not that useful;
we can always express these with guards:

```
case Box(var x) when x == 1: ...
case Box(var x) when x == 2: ...
```

At this point, constant patterns do not seem to carry their weight.  We can
instead interpret a `case` label as carrying a compatible literal or pattern,
but not make literals into actual patterns.

#### Adjusting instanceof

The remaining anomaly we had was about unrolling nested patterns when the inner
pattern is total.  We can plug this by simply outlawing total patterns in
`instanceof`.

This may seem like a cheap trick, but it makes sense on its own.  If the
following statement was allowed:

```
if (e instanceof var x) { X }
```

it would simply be confusing; on the one hand, it looks like it should always
match, but on the other, `instanceof` is historically null-hostile.  And, if the
pattern always matches, then the `if` statement is silly; it should be replaced
with:

```
var x = e;
X
```

since there's nothing conditional about it.  So by banning "any" patterns on the
RHS of `instanceof`, we both avoid a confusion about what is going to happen,
and we prevent the unrolling anomaly.

For reasons of compatibility, we will have to continue to allow

```
if (e instanceof Object) { ... }
```

which succeeds on all non-null operands, but since `Object o` is an "any"
pattern, we would ban

```
if (e instanceof Object o) { ... }
```

in the same way as with `var x`.

To clarify the distinction between "any" and "total"; in

```
Point p;
if (p instanceof Point(var x, var y)) { }
```

the pattern `Point(var x, var y)` is total on `Point`, but not an "any" pattern
-- it still doesn't match on `p == null`.  Whereas on a target of type `Point`,
`Point p` is both total and an "any" pattern.

On the theory that an "any" pattern in `instanceof` is silly, we may also want
to ban other "silly" patterns in `instanceof`, such as constant patterns, since
all of the following have simpler forms:

```
if (x instanceof null) { ... }
if (x instanceof "") { ... }
if (i instanceof 3) { ... }
```

In the first round (type patterns in `instanceof`), we mostly didn't confront
this issue, saying that `instanceof T t` matched in all the cases where
`instanceof T` would match.  But given that the solution for `switch` relies
on "any" patterns matching null, we may wish to adjust the behavior of
`instanceof` before it exits preview.


[jep305]: https://openjdk.java.net/jeps/305
[jep375]: https://openjdk.java.net/jeps/375
[patternmatch]: pattern-matching-for-java.html
