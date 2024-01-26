# Towards member patterns
#### Brian Goetz {.author}
#### January 2024 {.date}

Time to check in on where things are in the bigger picture of patterns as class
members.  Note: while this document may have illustrative examples, you should
not take that as a definitive statement of syntax.

We've already dipped our toes in the water with _record patterns_.  A record
pattern looks like:

    case R(p1, p2, ... pn):

where `R` is a record type and `p1..pn` are nested patterns that are matched to
its components.  Because records are defined by their state description, we can
automatically derive record patterns "for free", just as we derive record
constructors, accessors, etc.

There are many other classes that would benefit from being deconstructible with
patterns.  To that end, we will generalize record patterns to _deconstruction
patterns_, where any class can declare an explicit deconstruction pattern and
participate in pattern matching like records do.

Deconstruction patterns are not the end of the user-declared pattern story. Just
as some classes prefer to expose static factories rather than constructors, they
will be able to expose corresponding static patterns.  And there is also a role
for "instance patterns" and "pattern objects" as well.

Looking only at record and deconstruction patterns, it might be tempting to
think that patterns are "just" methods with multiple return.   But this would be
extrapolating from a special case.  Pattern matching is intrinsically
_conditional_; the extraction of values from a target is conditioned on whether
the target _matches_ the pattern.  For the patterns we've seen so far -- type
patterns and record patterns -- matching can be determined entirely by types.
But more sophisticated patterns can also depend on other aspects of object
state.  For example, a pattern corresponding to the static factory
`Optional::of` requires not only that the match candidate be of type `Optional`,
but that the match candidate is an `Optional` that actually holds a value.
Similarly, a pattern corresponding a regular expression requires the match
candidate to not only be a `String`, but to match the regular expression.

## The key intuition around patterns

A key capability of objects is _aggregation_; the combination of component
values into a higher-level composite that incorporates those components.  Java
facilitates a variety of idioms for aggregation, including constructors,
factories, builders, etc.  The dual of aggregation is _destructuring_ or
_decomposition_, which takes an aggregate and attempts to recover its
"ingredients".  However, Java's support for destructuring has historically been
far more ad-hoc, largely limited to "write some getters".  Pattern matching
seeks to put destructuring on the same firm foundation as aggregation.

Deconstruction patterns (such as record patterns) are the dual of construction.
If we construct an object:

    Object o = new Point(x, y);

we can deconstruct it with a deconstruction pattern:

    if (o instanceof Point(var x, var y)) { ... }

> Intuitively, this pattern match asks "could this object have come from
> invoking the constructor `new Point(x, y)` for some `x` and `y`, and if so,
> tell me what they are."

While not all patterns exist in direct correspondence to another constructor or
method, this intuition that a pattern reconstructs the ingredients to an
aggregation operation is central to the design; we'll explore the limitations of
this intuition in greater detail later.

## Use cases for declared patterns

Before turning to how patterns fit into the object model, let's look at some of
the potential use cases for patterns in APIs.

### Recovering construction arguments

Deconstruction patterns are the dual of constructors; where a constructor takes
N arguments and aggregates them into an object, a deconstruction pattern takes
an aggregate and decomposes it into its components.  Constructors are unusual in
that they are instance behavior (they have an implicit `this` argument), but are
not inherited; deconstruction patterns are the same.  For deconstruction
patterns (but not for all instance patterns), the match candidate is always the
receiver.  Tentatively, we've decided that deconstruction patterns are always
unconditional; that a deconstruction pattern for class `Foo` should match any
instance of `Foo`.  At the use site, deconstruction patterns use the same syntax
as record patterns:

    case Point(int x, int y):

Just as constructors can be overloaded, so can deconstruction patterns. However,
the reasons we might overload deconstruction patterns are slightly different
than for constructors, and so it may well be the case that we end up with fewer
overloads of deconstruction patterns than we do of constructors. Constructors
often form _telescoping sets_, both for reasons of syntactic convenience at the
use site (fewer arguments to specify) and to avoid brittleness (clients can let
the class implementation pick the defaults rather than hard-coding them.)  This
motivation is less pronounced for deconstruction patterns (unwanted bindings can
be ignored with `_`), so it is quite possible that authors will choose to have
one deconstruction pattern overload per telescoping constructor _set_, rather
than one per constructor.

There is no requirement for deconstruction patterns to expose the exact same API
as constructors, but we expect this will be common, at least for classes for
which the construction process is effectively an aggregation operation on the
constructor arguments.

### Recovering static factory arguments

Not all classes want to expose their constructors; sometimes classes prefer to
expose static factories instead.  In this case, the class should be able to
expose corresponding static patterns as well.

For a class like `Optional`, which exposes factories `Optional::of` and
`Optional::empty`, the object state incorporates not only the factory arguments,
but which factory was chosen.  Accordingly, it makes sense to deconstruct the
object in the same way:

    switch (optional) {
        case Optional.of(var payload): ...
        case Optional.empty(): ...
    }

Such patterns are necessarily conditional, asking the Pattern Question: "could
this `Optional` have come from the `Optional::of` factory, and if so, with what
argument?"  Static patterns, like static methods, lack a receiver, so `this` is
not defined in the body of a static pattern.  However, we will need a way to
denote the match candidate, so its state can be examined by the pattern body.

Another feature of static methods is that they can be used to put a factory for
a class `C` in _another_ class, whether one in the same maintenance domain (such
as the `Collections`) or in some other package.  This feature is shared by
static patterns.

### Conversions and queries

Another application for static patterns is the dual of static methods for
conversions.  For a static method like `Integer::toString`, which converts an
`int` to its `String` representation, a corresponding static pattern
`Integer::toString` can ask the Pattern Question: "could this `String` have come
from converting an integer to `String`, and if so, what integer".

Some groups of query methods in existing APIs are patterns in disguise.  The
class `java.lang.Class` has a pair of instance methods, `Class::isArray` and
`Class::getComponentType`, that work together to determine if the `Class`
describes an array type, and if so, provide its component type. This question
is much better framed as a single pattern:

    case Class.arrayClass(var componentType):

The two existing methods are made more complicated by their relationship to each
other; `Class::getComponentType` has a precondition (the `Class` must describe
an array type) and therefore has to specify and implement what to do if the
precondition fails, and the relationship between the methods is captured only in
documentation.  By combining them into a single pattern, it become impossible to
misuse (because of the inherent conditionality of patterns) and easier to
understand (because it can all be documented in one place.)

This hypothetical `Class::arrayClass` pattern also has a sensible dual as a
factory method:

    static<T> Class<T[]> arrayClass(Class<T> componentType)

which produces the array `Class` for the array type whose component type is
provided. An API need not provide both directions of a conversion, but if it
does, the two generally strengthen each other.  This method/pattern pair could
be either static or instance members, depending on API design choice.

Another form of "conversion" method / pattern pair, even though both types are
the same, is "power of two".  A `powerOfTwo` method takes an exponent and
returns the resulting power of two; a `powerOfTwo` pattern asks if its match
candidate is a power of two, and if so, binds the base-two logarithm.

### Numeric conversions

As Project Valhalla gives us the ability to declare new numeric types, we will
want to be able to convert these new types to other numeric types.  For
unconditional conversions (such as widening half-float to float), an ordinary
method will suffice:

    float widen(HalfFloat f);

But the reverse is unlikely to be unconditional; narrowing conversions can fail
if the value cannot be represented in the narrower type. This is better
represented as a pattern which asks the Pattern Question: "could this `float`
have come from widening a `HalfFloat`, and if so, tell me what `HalfFloat` that
is."  A widening conversion (or boxing conversion) is best represented by a
_pair_ of members, an ordinary method for the unconditional direction, and a
pattern for the conditional direction.

### Conditional extraction

Some operations, such as matching a string to a regular expression with capture
groups, are pattern matches in disguise.  We should be able to take a regular
expression R and match against it with `instanceof` or `switch`, binding capture
groups (using varargs patterns) if it matches.

## Member patterns in the object model

We currently have three kinds of executable class members: constructors, static
methods, and instance methods.  (Actually constructors are not members, but we
will leave this pedantic detail aside for now.)  As the above examples show,
each of these can be amenable to a dual member which asks the Pattern Question
about it.

Patterns are dual to constructors and methods in two ways: structurally and
semantically.  Structurally, patterns invert the relationship between inputs and
outputs: a method takes N arguments as input and produces a single result, and
the corresponding pattern takes a candidate result (the "match candidate") and
conditionally produces N bindings.  Semantically, patterns ask the Pattern
Question: could this result have originated by some invocation of the dual
operation.

### Patterns as inverse methods and constructors

One way to frame patterns in the object model is as _inverse constructors_ and
_inverse methods_.  For purposes of this document, I will use an illustrative
syntax that directly evokes this duality (but remember, we're not discussing
syntax now):

```
class Point {
    final int x, y;

    // Constructor
    Point(int x, int y) { ... }

    // Deconstruction pattern
    inverse Point(int x, int y) { ... }
}

class Optional<T> {
    // Static factories
    static<T> Optional<T> of(T t) { ... }
    static<T> Optional<T> empty() { ... }

    // Static patterns
    static<T> inverse Optional<T> of(T t) { ... }
    static<T> inverse Optional<T> empty() { ... }
}
```

`Point` has a constructor and an inverse constructor (deconstruction pattern) for
the external representation `(int x, int y)`; in an inverse constructor, the
binding list appears where the parameter list does in the constructor.
`Optional<T>` has static factories and corresponding patterns for `empty` and
`of`.  As with inverse constructors, the binding list of a pattern appears in
the position that the parameters appear in a method declaration; additionally,
the _match candidate type_ appears in the position that the return value appears
in a method declaration.  In both cases, the declaration site and use site of
the pattern uses the same syntax.

In the body of an inverse constructor or method, we need to be able to talk
about the match candidate.  In this model, the match candidate has a type
determined by the declaration (for an inverse constructor, the class; for an
inverse method, the type specified in the "return position" of the inverse
method declaration), and there is a predefined context variable (e.g., `that`)
that refers to the match candidate.  For inverse constructors, the receiver
(`this`) is aliased to the match candidate (`that`), but not necessarily so for
inverse methods.

### Do all methods potentially have inverses?

We've seen examples of constructors, static methods, and instance methods that
have sensible inverses, but not all methods do.  For example, methods that
operate primarily by side effects (such as mutative methods like setters or
`List::add`) are not suitable candidates for inverses.  Similarly, pure
functions that "co-mingle" their arguments (such as arithmetic operators) are
also not suitable candidates for inverses, because the ingredients to the
operation typically can't be recovered from the result (i.e., `4` could be the
result of `plus(2, 2)` or `plus(1, 3)`).

Intuitively, the methods that are invertible are the ones that are
_aggregative_.  The constructor of a (well-behaved) record is aggregative, since
all the information passed to the constructor is preserved in the result.
Factories like `Optional::of` are similarly aggregative, as are non-lossy
conversions such as widening or boxing conversions.

Ideally, an aggregation operation and its corresponding inverse form an
_embedding projection pair_ between the aggregate and a component space.
Intuitively, an embedding-projection pair is an algebraic structure defined by a
pair of functions between two sets such that composing in one direction
(embed-then-project) is an identity, and composing in the other direction
(project-then-embed) is a well-behaved approximation.

### Conversions

Conversion methods are a frequent candidate for inversion.  We already have

    // Integer.java
    static String toString(int i) { ... }

to which the obvious inverse is

    static inverse String toString(int i) { ... }

and we can inspect a string to see if it is the string representation of an integer with

    if (s instanceof Integer.toString(int i)) { ... }

This composes nicely with deconstruction patterns; if we have a `Box<String>`
and want to ask whether the contained string is really the string representation
of an integer, we can ask:

    case Box(Integer.toString(int i)):

which conveniently looks just like the composition of constructors or factories
used to create such an instance (`new Box(Integer.toString(3))`).

When it comes to user-definable numeric conversions, the most likely strategy
involves combining related operators in a single _witness_ object.  For example,
numeric conversion might be modeled as:

```
interface NumericConversion<FROM, TO> {
    TO convert(FROM from);
    inverse TO convert(FROM from);
}
```

which reflects the fact that conversion is total in one direction (widening,
boxing) and conditional in the other (narrowing, unboxing.)

### Regular expression matching

Regular expressions are a form of ad-hoc pattern; a given string might match a
given regex, or not, and if it does, it might product multiple bindings (the
capture groups.)  It would be nice to be able to express regular expression
matches as ordinary pattern matches.

Conveniently, we already have an object representation of regular expressions --
`java.util.Pattern`.  Which is an ideal place to put an instance pattern:

```
// varargs pattern
public inverse String match(String... groups) {
    Matcher m = matcher(that);    // *that* is the match candidate
    if (m.matches())              // receiver for matcher() is the Pattern
        __yield IntStream.range(1, m.groupCount())
                            .map(m::group)
                            .toArray(String[]::new);
}
```

And now, we want to express "does string s match any of these regular
expressions":

```
static final Pattern As = Pattern.compile("([aA]*)");
static final Pattern Bs = Pattern.compile("([bB]*)");
static final Pattern Cs = Pattern.compile("([cC]*)");

...

switch (aString) {
    case As.match(String as) -> ...
    case Bs.match(String bs) -> ...
    case Cs.match(String cs) -> ...
    ...
}
```

Essentially, `j.u.r.Pattern` becomes a _pattern object_, where the state of the
object is used to determine whether or not it matches any given input.  (There
is nothing stopping a class from having multiple patterns, just as it can have
multiple methods.)

## Pattern resolution

When we invoke a method, sometimes we are able to refer to the method with an
_unqualified_ name (e.g., `m(3)`), and sometimes the method must be _qualified_
with a type name, package name, or a receiver object.  The same is true for
declared patterns.

Constructors for classes that are in the same package, or have been imported,
can be referred to with an unqualified name; constructors can also be qualified
with a package name.  The same is true for deconstruction patterns:

```
case Foo(int x, int y):         // unqualified
case com.foo.Bar(int x, int y): // qualified by package
```

Static methods that are declared in the current class or an enclosing class, or
are statically imported, can be referred to with an unqualified name; static
methods can also be qualified with a type name.  The same is true for static
patterns:

```
case powerOfTwo(int exp):  // unqualified
case Optional.of(var e):   // qualified by class
```

Instance methods invoked on the current object can be referred to with an
unqualified name; instance methods can also be qualified by a receiver object.
The same is true for instance patterns:

```
case match(String s):    // unqualified
case As.match(String s): // qualified by receiver
```

In a qualified pattern `x.y`, `x` might be a package name, a class name, or a
(effectively final) receiver variable; we use the same rules for choosing how to
interpret a qualifier for patterns as we do for method invocations.

## Benefits of explicit duality

Declaring method-pattern pairs whose structure and name are the same yields many
benefits.  It means that we take things apart using the same abstractions used to put them together, which makes code more readable and less error-prone.

Referring to a _inverse pair_ of operations by a single name is simpler than
having separate names for each direction; not only don't we need to come up with
a name for the other direction, we also don't need to teach clients that "these
two names are inverses", because the inverses have the same name already. What
we know about the method `Integer::toString` immediately carries over to its
inverse.

Further, thinking about a method-pattern pair provides a normalizing force to
actually ensuring the two are inverses; if we just had two related methods
`xToY` and `yToX`, they might diverge subtly because the connection between the
two members is not very strong.

Finally, this gives the language permission to treat the _pair_ of members as a
thing in some cases, such as the use of ctor-dtor pairs in "withers" or
serialization.

The explicit duality takes a little time to get used to.   We have many years of
experience of naming a method for its directionality, so people's first reaction
is often "the pattern should be called `Integer.fromString`, not
`Integer.toString`". So people will initially bristle at giving both directions
the same name, especially when one implies a directionality such as `toString`.
(In these cases, we can fall back on a convention that says that we should name
it for the total direction.)

## Pattern lambdas, pattern objects, pattern references

Interfaces with a single abstract method (SAM) are called _functional
interfaces_ and we support a conversion (historically called SAM conversion)
from lambdas to functional interfaces.  Interfaces with a single abstract
pattern can benefit from a similar conversion (call this "SAP" conversion.)

In the early days of Streams, people complained about processing a stream using
instanceof and cast:

```
Stream<Object> objects = ...
Stream<String> strings = objects.filter(x -> x instanceof String)
                                .map(x -> (String) x);
```

This locution is disappointing both for its verbosity (saying the same thing in
two different ways) and its efficiency (doing the same work basically twice.)  Later, it became possible to slightly simplify this using `mapMulti`:

```
objects.mapMulti((x, sink) -> { if (x instanceof String s) sink.accept(s); })
```

But, ultimately this stream pipeline is a pattern match; we want to match the
elements to the pattern `String s`, and get a stream of the matched string
bindings.  We are now in a position to expose this more directly. Suppose we
had the following SAP interface:

```
interface Match<T, U> {
    inverse U match(T t);
}
```

then `Stream` could expose a `match` method:

```
<U> Stream<T> match(Match<T, U> pattern);
```

We can SAP-convert a lambda whose yielded bindings are compatible with the sole
abstract pattern in the SAP interface::

```
Match<Object, String> m = o -> { if (o instanceof String s) __yield(s); };
... stream.match(m) ...
```

And we can do the same with _pattern references_ to existing patterns that are
compatible with the sole pattern in a SAP interface.   As a special case, we can
also support a conversion from type patterns to a compatible SAP type with an
`instanceof` pattern reference (analogous to a `new` method reference):

```
objects.match(String::instanceof)
```

where `String::instanceof` means the same as the previous lambda example.  This
means that APIs like `Stream` can abstract over conditional behavior as well as
unconditional.
