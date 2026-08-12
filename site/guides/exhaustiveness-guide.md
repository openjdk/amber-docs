# Preparing for Change: Safe Switching over Sealed APIs
#### Angelos Bimpoudis, Alex Buckley and Brian Goetz {.author}
#### 2026-08-11 {.date}

## Summary

An exhaustive `switch` on a `sealed` type covers the permitted subtypes the
compiler knows about; it does not cover future additions, which can cause
`MatchException` at run time.  API authors should document the expected
evolution of `sealed` types so that client code can choose how to handle future
additions. It is almost never necessary to use `default` when switching over a
`sealed` type -- and often not the best option.

## `switch` expressions: Exhaustive by design

A `switch` expression must be _exhaustive_, meaning that the `switch` must
produce a result for every possible non-null value of the selector.

For primitive types other than `boolean`, `default` is the only label that
covers all the unlisted values and makes the `switch` exhaustive:

```{.java}
int code = ...;
String fruitName = switch (code) {
    case 0  -> "apple";
    case 1  -> "orange";
    default -> "unknown";
};
```

In contrast, when the selector is a `sealed` type, the `switch` can be
made exhaustive by enumerating the permitted subtypes of the `sealed
type`, `case` by `case`. No `default` is needed. For example:

```{.java}
sealed interface Fruit permits Apple, Orange {}
record Apple(String variety)  implements Fruit { ... }
record Orange(String variety) implements Fruit { ... }

Fruit f = ...;
String description = switch (f) {
    case Apple  a -> "apple: " + a.variety();
    case Orange o -> "orange: " + o.variety();
};
```

However, an exhaustive `switch` is not guaranteed to remain so, because it is
possible to evolve sealed types over time to acquire new permitted subtypes.
This means that switches over a sealed type that were exhaustive when they were written, may no
longer be exhaustive, because they no longer cover all possible cases. The
language provides a variety of static and dynamic tools for confidently
navigating these transitions.

For example, suppose `Fruit` is recompiled to add a `Pear` subtype:

```{.java}
sealed interface Fruit permits Apple, Orange, Pear {}
record Pear(String variety) implements Fruit {}
```

If the `switch` above is also recompiled, the compiler realizes that `Pear` is
not covered by a `case`. Because the `switch` is no longer exhaustive, it no
longer compiles.  While this might at first seem "bad", this is actually
very good!  When the `switch` was written, the developer intent was "I have
covered all the fruits."  The compiler was able to validate the developer
intent, ensuring that all fruits are indeed covered.  And when the world of
fruits changes in a way that invalidates the original developer intent, the
developer is notified of this change so that the code can be adjusted to meet
the new reality.

In this case, adding a `case Pear p` brings the code back into consistency with
its intent -- covering all the fruits.

In the meantime, it is possible (if the `switch` is not recompiled) that a
`Pear` value matches no `case` at run time, causing the `switch` to throw
`MatchException`.  Again, while this may at first seem bad, this is also good!
If the program continues to encounter only fruits it is familiar with,
everything continues to work.  But if a fruit appears that it is unprepared to
deal with, we get feedback at runtime (in the form of an exception) again telling
us that our program's assumptions -- which were valid when it was written, but
which have been undermined by separately-compiled changes -- are no longer valid.

## By default, avoid `default`

It might be tempting to try to "future proof" switches by including a catch-all
`default` label.  However, in most cases, this is the wrong move, because it
sweeps errors, both present and future, under the rug.

If the developer intent is that the switch is exhaustive, adding a `default`
label hides this intent, and thus deprives you of having the compiler (and your
reviewers) double-checking your work and letting you know if you've made a bad
assumption. A `default`-free switch captures this intent so that it can
be verified later, when the world might have changed. In the case of our
`Fruit` example above, had we included a `default` label, we would have been
deprived of the notification that our intent has been invalidated and of the
opportunity to update our program in a timely manner.

In addition, a `default` does not handle a `null` selector. You must either write an explicit
`case null` label or, preferably, use `Objects.requireNonNull` to reject `null` before the `switch`.

Finally, a `default` may interfere with the runtime detection of unexpected values,
which may allow a program to continue but cause it to produce an erroneous result.

In almost all cases, it is better to write `case` labels that cover a `sealed`
type than to use a `default` as a catch-all; we will outline the few situations
where this is appropriate later in this document.

## Sealed types can evolve, and often will

A `sealed` type is an API that describes a hierarchy of permitted subtypes _at a
single point in time_.  As with any API in Java, it can change over time.  In
particular, it is important that adding new permitted subtypes later be a
permissible and compatible evolution.

Adding a new type to an API is generally harmless, but
adding a new permitted subtype to a `sealed` class could cause pre-existing
`switch` expressions to become non-exhaustive.  While at first this seems
disturbing -- such switches may throw `MatchException` at run time and will
encounter compilation errors when recompiled -- this is how we balance safety
with resilience.  It is unreasonable to expect that when a new `Fruit` is
discovered, that all fruit-related code can change instantly.  But also, we
don't want to give up the considerable compile-time and run-time safety benefits
that sealing offers in capturing design intent and enhanced compile-time and
run-time type safety.

There are two primary reasons why one might declare a type `sealed`:

- To signal to clients that this type models a closed-end domain, such as when
   `Fruit` is defined to be an `Apple` or an `Orange` but nothing else.
- To control all of the _implementations_ of the type, but not model a specific
   closed-end domain.

It is helpful when authors of sealed classes explicitly document the goal of
sealing, and how they expect the class to evolve in the future.  A sealed class
that models a `Boolean` domain is unlikely to acquire subclasses that correspond
to anything other than `True` and `False`, because the domain is extremely
stable; on the other hand, a sealed class that models the different kinds of AST
nodes describing a program is highly likely to change over time, either because
the language being modeled has acquired new features, or because of changes in
how programs are modeled.

When a sealed class is encapsulated within a single maintenance domain, it is
reasonable to expect that changes to the sealed class will immediately be
reflected at all the uses of the class; when a sealed class is published across
maintenance domains, we should expect there will be some time lag between the
update of the sealed class and properly updating its clients.  We should program
with idioms that do not unnecessarily "sweep changes under the floor" to avoid
silently ignoring feedback that might portend actionable changes.

## Guidelines for switches over sealed types

Switches over sealed types should heed the following guidelines:

1.  A `default` label is almost never the right answer, for reasons discussed
    already.
2.  Where practical, the best approach is to handle each permitted subtype with
    its own `case`.
3.  If it is impractical to handle each permitted subtype with its own `case`, we
    should use the narrowest possible type pattern (e.g., `Fruit f`) to render
    the switch exhaustive. We call this a match-all case. 
    
    In the examples above that switch over a fruit, the match-all case would be `case Fruit f`. It is not recommended to use a match-all case with a broader type pattern than the selector type, such as `case Object o`.

There are several reasons why it may not be practical to handle each permitted
subtype with its own `case`:

 - Some of the permitted subtypes are inaccessible to the client.  This can
   arise when the author of the sealed class used sealing primarily to achieve
   control over implementations, not to model a closed-end domain of well-known
   alternatives.

   _(The idiom of an inaccessible subtype is most likely to occur in widely distributed APIs
   such as the JDK, e.g., the [Attribute](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/classfile/Attribute.html) interface of the [Class-File API](https://openjdk.org/jeps/484).
   The javadoc for `Attribute` omits inaccessible subtypes, triggering the "(not exhaustive)" note.)_

 - The sealed class has many permitted subtypes, and the client wants to handle
   only a small subset of these.  In this situation, listing all the permitted
   subtypes would likely be low-value ceremony, while picking off a small number
   of subtypes and ignoring the rest is analogous to an unbalanced `if`
   statement. This is best seen when a sealed type has many subtypes: `sealed interface A permits A1, A2, A3, A4, ... A100 { }` -- a match-all `case A` is reasonable.

Further, even if all subtypes are handled by their own `case`, it may sometimes be
necessary to use a match-all `case` anyway, to provide custom error handling in the
event of unexpected change.  However, we should be aware that this is giving up
much of the compile-time type safety that sealed types afford.

There is one unusual situation where a match-all `case` is needed to make the `switch` exhaustive:
when the `sealed` class itself is concrete and therefore can be instantiated.

```{.java}
sealed /*not abstract*/ class Fruit permits Apple, Orange {}
final class Apple  extends Fruit { ... }
final class Orange extends Fruit { ... }

Fruit f = new Fruit();
Fruit normalized = switch (f) {
    case Apple  a     -> new Apple(a.variety().strip());
    case Orange o     -> new Orange(o.variety().strip());
    case Fruit  other -> other;
};
```

There are some situations where a narrower type pattern than the selector type may
render the switch exhaustive. This happens when a `sealed` type has multiple branches:

```{.java}
sealed interface Food permits Fruit, Dessert {}

sealed interface Fruit extends Food permits Apple, Orange {}
record Apple()  implements Fruit {}
record Orange() implements Fruit {}

sealed interface Dessert extends Food permits Cake, Pie {}
record Cake() implements Dessert {}
record Pie()  implements Dessert {}
```

While it is possible to "totalize" a `Food` selector type with a match-all, `case Food`, it is preferable to
be more restrained and totalize each branch explicitly:

```{.java}
Food fd = ...;
String description = switch (fd) {
    case Apple  _ -> "apple";
    case Orange _ -> "orange";

    case Cake _ -> "cake";
    case Pie  _ -> "pie";

    case Fruit _, Dessert _ -> "other food";
};
```

When a sealed type has many permitted subtypes, it is again preferable to totalize on a per-branch basis.

```{.java}
sealed interface AB permits A, B { ... }
sealed interface A extends AB permits A1, A2, A3, A4, ... A100 { }
sealed interface B extends AB permits B1, B2, B3 { ... }
```

For a `switch` with selector type `AB`, it may be practical to cover the three `B` options but not the 100 `A` options. It is best to render the `switch` exhaustive by totalizing with a `case A _`, rather than a `case AB _`;
this preserves the ability for exhaustiveness checking for the `B` options,
whereas using the broader type would give up all exhaustiveness checking.
