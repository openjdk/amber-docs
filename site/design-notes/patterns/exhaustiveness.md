# Patterns: Exhaustiveness, Unconditionality, and Remainder
#### Brian Goetz and Gavin Bierman {.author}
#### 2023-05-23 {.date}

As the `switch` construct has been made steadily more expressive (first to
support [`switch` expressions](https://openjdk.org/jeps/361), and later to
support [patterns in `switch`](https://openjdk.org/jeps/441)), it has become
important to provide compile-time checking for whether a particular `switch` is
_exhaustive_ for its selector type.  All `switch` expressions, and any `switch`
statement that uses a pattern label, must be exhaustive, or a compilation error
will occur.  For example, given:

```
enum Color { RED, YELLOW, GREEN }

int numLetters = switch (color) {  // Error - not exhaustive
    case RED -> 3;
    case GREEN -> 5;
}
```

we would like to get a compile-time error that tells us that this switch is not
exhaustive, because the anticipated input `YELLOW` is not covered.  Which raises
the question: what does "exhaustive" mean?

A switch with a _match-all_ label (a `default` label, or a `case null, default`
label, or a `case` label with a type pattern that matches every value of the
selector expression) is clearly exhaustive; for every possible value of the
selector, one of the labels will definitely be selected - the match-all label!
It is tempting to try to define exhaustiveness for a `switch` without a
match-all label as meaning "if a match-all label were added, it would never be
selected" As it turns out, this definition is too strong, and even if we were to
adopt this definition, we probably wouldn't enjoy programming in the resulting
language.

## Switching over enums

Many of the tensions in defining exhaustiveness are visible even in the simple
case of exhaustive switch expressions over enum types.  If we complete our
switch over colors by handling all the enum constants, is it exhaustive?

```
int numLetters = switch (color) { // Exhaustive!
    case RED -> 3;
    case GREEN -> 5;
    case YELLOW -> 6;
}
```

We would surely like for this `switch` to be exhaustive -- for multiple reasons.
It would definitely be cumbersome to have to write a match-all clause which
probably just throws an exception, since we have already handled all the cases:

```
int numLetters = switch (color) {
    case RED -> 3;
    case GREEN -> 5;
    case YELLOW -> 6;
    default -> throw new ArghThisIsIrritatingException(color.toString());
}
```

Manually writing a `default` clause in this situation is not only irritating but
actually pernicious, since the compiler can do a better job of checking
exhaustiveness without one. (The same is true of any other match-all clause such
as `case null, default`, or an unconditional type pattern.) If we omit the
`default` clause, then we will discover at compile time if we have forgotten a
`case` label, rather than finding out at run time — and maybe not even then.

More importantly, what happens if someone later adds another constant to the
`Color` enum?  If we have an explicit match-all clause then we will only
discover the new constant value if it shows up at run time.  But if we code the
`switch` to cover all the constants known at compile time and omit the match-all
clause, then we will find out about this change the next time we recompile the
class containing the `switch` -- and can then choose how to handle it. A
match-all clause risks sweeping exhaustiveness errors under the rug.

In conclusion: An exhaustive `switch` without a match-all clause is better than
an exhaustive `switch` with one, when possible.

Looking to run time, what happens if a new `Color` constant is added, and the
class containing the `switch` is not recompiled?  There is a risk that the new
constant will be exposed to our `switch`.  Because this risk is always present
with enums, if an exhaustive enum `switch` does not have a match-all clause,
then the compiler will actually synthesize a `default` clause that throws an
exception.  This guarantees that the `switch` cannot complete normally without
selecting one of the clauses.  (Given that the compiler will insert a synthetic
`default` clause, it is tempting to ask whether we should _outlaw_ an explicit
match-all clause in an otherwise exhaustive switch.  But, this would be taking
it too far, as the user may want to provide customized error handling code.)

<!-- This is not quite true. We outlaw it if we have an unconditional pattern. -->

## Unconditionality, exhaustiveness, and remainder

Even the simple case of switching over enums illustrates that exhaustiveness is
more subtle than it first appears.  We want to say that having `case` labels for
`RED`, `YELLOW`, and `GREEN` means that the switch is exhaustive for `Color`,
but it isn't really, or at least not completely; there are possible run time
values of `Color` that are not matched by any of these labels.  This isn't a bug
in our definition of exhaustiveness; "strengthening" the compile-time checking
(which, in this case, would mean requiring a match-all clause) would be both
inconvenient for users and result in worse type checking (and therefore less
reliable programs).  The reality is that the compile-time notion of "exhaustive
enough" and true run time exhaustiveness are not the same thing. Similarly, we
would not want to weaken the run time checking by omitting the synthetic
`default`, as this could create surprising results.  So we need both concepts,
which we call _unconditionality_ (for the strong run time version) and
_exhaustiveness_ (for the compile-time version.)

A pattern is _unconditional_ for a candidate type if we can prove at compile
time that it will always match _all_ possible run time values of that type.  (An
unconditional pattern thus requires no run time checks.)  Unconditionality is a
strong condition; the only patterns currently supported that are unconditional
are inferred type patterns (`var x`), unnamed patterns (`_`, which is sugar for
`var _`), and type patterns where the type of the match candidate is a subtype
of the type named in the pattern (e.g. the type pattern `CharSequence cs` is
unconditional for the type `CharSequence` and also for `String` but not for the
type `Object`.)

Exhaustiveness for a type is a property not of a single pattern, but more
properly of a _set of patterns_ (or `case` labels).  A set of enum `case` labels
is exhaustive for the corresponding enum type if the set of `case` labels
contains all of the enum constants of that type.  As we will see, there are
other ways for a set of patterns to be exhaustive as well.

If a set of patterns is exhaustive for a type, we call the run time values that
are not matched by any pattern in the set the _remainder_ of the set.  In our
enum switch example, the remainder includes any novel enum constants, as well as
`null`.  (That `null` is part of the remainder for our enum switch is less
obvious, because the innate null-hostility of switches hides this in the simple
cases, but this will become important when we get to nested patterns.)

## Sealed types

Switching over a selector whose type names a `sealed` class is similar to
switching over enums, just lifted from the term level to the type level.  If all
of the permitted subtypes of the sealed type are handled by the cases, we would
like to be able to omit a match-all clause just as with enum switches.

```
sealed interface Container<T> permits Box, Bag { }

switch (container) {
    case Box<T> box: ...
    case Bag<T> bag: ...
}
```

For the same reasons as with enum switches, we would like for this switch to be
considered exhaustive on `Container<T>`.  And, just as with enum switches, it is
possible for a novel subtype of `Container` to show up at run time, so the
compiler similarly inserts a synthetic `default` clause that throws.  A set of
patterns is exhaustive on a sealed type if it is exhaustive on every permitted
subtype, and for an exhaustive set of patterns on a sealed type, any novel
subtypes are considered part of the remainder.  As with enum switches, for an
exhaustive set of patterns on a sealed type, the remainder contains the `null`
value.

### Record (and deconstruction) patterns

Given a record:

```
record IntBox(int i) { }
```

should the following switch be considered exhaustive?

```
IntBox ib = ...
switch (ib) {
    case IntBox(int i): ...
}
```

The pattern `IntBox(int i)` is not unconditional on `IntBox`, because it doesn't
match `null` (which is a valid run time value of `IntBox`).  But the above switch
certainly should be considered exhaustive; requiring a match-all clause, or even
a `case null` label here
would help no one.  Given a record class `R` with an `x` component, the record
pattern `R(p)` matches a value if the value is `instanceof R` and (recursively)
if the record value's `x` component value matches the pattern `p`. The pattern
`R(p)` is considered exhaustive for `R` if `p` is exhaustive for the type of the
`x` component of `R`.

The pattern `IntBox(int i)` cannot be considered to match `null`, even though
`null` can be cast to `IntBox`.  This is because, in general, to extract a
record value's component value, after casting the value to the record type, we
invoke the component accessor method on the resulting reference; if the
candidate is `null`, this invocation will fail. Just as with the earlier cases,
the remainder for any record pattern always includes `null`.


### Nested patterns

Nested patterns are also a source of remainder.  Say we have:

```
record Box<T>(T t) { }
```

and a switch:

```
Box<Box<String>> bbs = ...
switch (bbs) {
    case Box(Box(String s)): ...
}
```

First, is this switch exhaustive?  By the above definition, `Box(Box(String s))`
is exhaustive for the type `Box<Box<String>>` if the nested pattern `Box(String
s)` is exhaustive for `Box<String>`; by the same rule, this is exhaustive if
`String s` is exhaustive for `String`, which it is, by virtue of being
unconditional.  So the switch is exhaustive.

So, what is the remainder?  This is where it gets interesting, and why we've
bothered to include `null` in the remainder for the previous examples even when
it might appear to be irrelevant because of the `null` hostility of switch.  The
remainder here clearly includes `null`, but it also includes a `Box` value with
a `null` component value! More generally, for a record pattern `R(p)` where `r`
is in the remainder of `p` for the component type of `R`, the remainder includes
an `R` value with `r` as its component value.

### Nested patterns and sealed types

Let's put together nested patterns with sealed types.

```
sealed interface Fruit permits Apple, Orange { }
final class Apple  implements Fruit { }
final class Orange implements Fruit { }

Box<Fruit> bf = ...
switch (bf) {
    case Box(Apple a): ...
    case Box(Orange o): ...
}
```

Is this switch exhaustive on `Box<Fruit>`?  Together, the patterns `Apple a` and
`Orange o` are exhaustive on `Fruit`, with remainder containing `null` and any
novel subtype of `Fruit`.  So `Box(Apple a)` and `Box(Orange o)` _are_
exhaustive on `Box<Fruit>`.  The remainder includes a `Box` value with a `null`
component value, and also `Box` values whose component value's type is a novel
subtype of `Fruit`.  (The rules get more complicated when the record has more
than one component.)

## It's complicated, but at its heart it's simple

This may all seem complicated but, at its heart, it derives from some
straightforward rules about how patterns are exhaustive for a type:

-   A set of patterns containing a type pattern `T t` is exhaustive for `T` and
    any of its subtypes;
-   A set of patterns is exhaustive for a `sealed` type if it is exhaustive for
    its permitted direct subtypes;
-   A set of patterns containing a record pattern `R(P)` is exhaustive for `R`
    if `P` is exhaustive for the component type of `R`; and
-   A set of patterns containing the record patterns `R(P0) .. R(Pn)` is
    exhaustive for `R` if the set of patterns `P0..Pn` is exhaustive for `R`'s
    component type.

The remainder of an exhaustive set of patterns is the set of values that do not
match any pattern in the set. These are the 'weird' values that might
appear at run time, but for which it would be unreasonable or even
counter-productive to require that they be explicitly handled every time in
every `switch`.