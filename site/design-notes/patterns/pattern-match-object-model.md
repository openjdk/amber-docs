# Pattern Matching in the Java Object Model

#### Brian Goetz and Gavin Bierman {.author}
#### December 2020 {.date}

This document describes a possible approach for how _pattern matching_ might be
integrated more deeply into the language.  _This is an exploratory document only
and does not constitute a plan for any specific feature in any specific version
of the Java Language._

### Pattern matching documents

- [Pattern Matching For Java](pattern-matching-for-java).  Overview of
  pattern matching concepts, and how they might be surfaced in Java.
- [Pattern Matching For Java -- Semantics](pattern-match-semantics).  More
  detailed notes on type checking, matching, and scoping of patterns and binding
  variables.
- [Extending Switch for Patterns](extending-switch-for-patterns).  An early
  exploration of the issues surrounding extending pattern matching to the
  `switch` statement.
- [Type Patterns in Switch](type-patterns-in-switch).  A more up-to-date
  treatment of extending pattern matching to `switch` statements, including
  treatment of nullity and totality.
- [Pattern Matching in the Java Object model](pattern-match-object-model)
  (this document).  Explores how patterns fit into the Java object model, how
  they fill a hole we may not have realized existed, and how they might affect
  API design going forward.


## Why pattern matching?

The related documents offered numerous examples of how pattern matching makes
common code constructs simpler and less error-prone.  These may be enough reason
on their own to want to add pattern matching to Java, but we believe there is
also a deeper reason to go there: that it fills in a hole in the object model,
and one we might not have even realized we were missing.

### Recap -- what is pattern matching?

Readers who are unfamiliar with pattern matching should first read the
above-referenced documents, but to summarize: pattern matching fuses three
operations that are commonly done together: an _applicability test_, zero or
more _conditional extractions_ if the test succeeds, and _binding_ the extracted
results to fresh variables.  A pattern match like:

```
if (p instanceof Point(int x, int y)) { ... }
```

expresses all three of these things in one go -- it asks if the target is a
`Point`; if it is, casts it to `Point` and extracts its `x` and `y` coordinates;
and binds these to the variables `x` and `y`.

### Aggregation and destructuring

Object-oriented languages provide many facilities for _aggregation_; the [Gang
of Four][gof] book defines several patterns for object creation (e.g., Factory,
Singleton, Builder, Prototype).  Object creation is so important that languages
frequently include specific features corresponding to these patterns.
Aggregation allows us to abstract data from the specific to the general, but
recovering the specifics when we need them is often difficult and ad-hoc.  For
example, it is common to provide factory methods for particular specific
configurations of aggregates, and these factories typically return an abstract
type, such as:

```
static Shape redBall(int radius) { ... }
static Shape redCube(int edge) { ... }
```

At the invocation site, it is obvious what kind of shape is being created, but
once we start passing it around as a `Shape`, it can be harder to recover its
properties.  Sometimes we reflect some of the properties in the type system
(such as concrete subtypes of `Shape` for `Ball` and `Cube`), but this is not
always practical (especially when state is mutable).  Other times, APIs  expose
accessors for these properties (such as a `shapeKind()` method on `Shape`), but
attempting to distill a least-common-denominator set of properties on an
abstract type is often a messy modeling exercise.  (For example, should a
`size()` method correspond to the edge size for a cube and the radius of a ball,
or should it attempt to normalize sizes somehow?  Or, should we have partial
methods like `ballRadius()` which fail when you apply them to a cube?) In this
approach, clients must be aware of how factories map their parameters to
abstract properties, which is complex and error-prone.  _Destructuring_ provides
this missing ability to recover the specific from the abstract more directly.

> _Destructuring is the dual of aggregation._

Java provides direct linguistic support for aggregation, in the form of
_constructors_ that take a description of an object's initial state and
aggregates it into an instance, but does not directly provided the reverse.
Instead, we leave that problem to APIs, which may expose accessors for
individual state components, and those accessors may or may not map to the
arguments presented to constructors or factories.  But this is a poor
approximation for all but the simplest classes, because the code for aggregation
and for destructuring often operate at different levels of granularity, and look
structurally different -- and these differences provide places for bugs to hide.
Whatever tools the language offers us for aggregation (e.g., constructors,
factories, builders), it should also offer us complementary destructuring,
ideally that is syntactically similar in declaration and use, and operate at the
same level of abstraction.  For example, if our `Shape` library provided
factories like the above, it could provide destructuring patterns that are
similar in name and structure:

```
switch (shape) {
    case Shape.redBall(var radius): ...
    case Shape.redCube(var edge): ...
}
```

> _Enabling API designers to provide complementary destructuring operations for
their aggregation operations enables more reversible APIs while still allowing
the API to mediate access to encapsulated state._

Of course, there may be some aggregates that don't want to give up their state,
and that's fine.  The goal is not to force transparency on all classes, but
instead to give API designers the tools with which to expose destructuring
operations that are structurally similar to its aggregation operations, should
they so desire.  Many, but not all, APIs would benefit from this.

### Object creation in Java

The most common idioms for creating objects in Java are constructors, factories,
and builders.  Constructors are a language feature; factories and builders are
design patterns implemented by libraries.  A specific API typically prefers one
of these idioms; either it exposes constructors, or hides the constructors and
provides factories, or hides the constructors and provides builders.

A sufficiently rich pattern matching facility would allow API designers to
provide complementary destructuring facilities for each of these idioms, which
leads to more readable and less error-prone client code.  If an object is
created with a constructor:

```
Object x = new Foo(a, b);
```

ideally, it should be destructurable via a "deconstruction pattern":

```
case Foo(var a, var b): ...
```

The syntactic similarity between the construction and deconstruction is not
accidental; appealing to a constructor-like syntax allows the user to see this
as asking "if x could have come into existence by invoking the `Foo(a, b)`
constructor, what values of `a` and `b` would have been provided?"

Similarly, if an object is created with a static factory:

```
Object x = Foo.of(a, b);
```

ideally, it should be destructurable via a "static pattern":

```
case Foo.of(var a, var b): ...
```

As a more concrete example, we construct `Optional` instances with:

```
o = Optional.of(x);
o = Optional.empty();
```

so we should be able to discriminate between `Optional` instances via static
patterns:

```
case Optional.empty(): ...
case Optional.of(var x): ...
```

(with bonus points if we can make the combination of these two patterns _total_
on non-null instances of `Optional`.)

### Composition

Another aspect in which we would like destructuring to mirror aggregation is in
_composition_.  Suppose, in our `Shape` example, we put a unit-sized red ball in
an `Optional`:

    Optional<Shape> os = Optional.of(Shape.redBall(1));

The creational idioms we use -- here, static factories -- compose, allowing
us to express this compound creation clearly and concisely.  

To determine if an `Optional<Shape>` could have derived from the above action,
using the sorts of APIs we have today, we would have to do something like:

```
Shape s = os.orElse(null);
boolean isRedUnitBall = s != null
                       && s.isBall()
                       && (s.color() == RED)
                       && s.size() == 1;
if (isRedUnitBall) { ... }
```

The code to take apart the `Optional<Shape>` looks nothing like the code to
create it, and is significantly more complicated -- which means it is harder to
read and harder to get right.  (As evidence of this, the first draft of this
example had a subtle mistake, which wasn't caught until review!)  And, as we
compose more deeply, these differences can multiply.  On the other hand, if
`Optional` and `Shape` provided complementary destructuring APIs to their
creation APIs, we could compose these just as we did with aggregation:

```
if (os instanceof Optional.of(Shape.redBall(var size))
    && size == 1) { ... }
```

This will match an `Optional` that contains a red ball of unit radius,
regardless of how it was created.  

> _First-class destructuring leads to composable APIs._

Method invocations compose from the inside out; pattern matching, which works
like a method invocation in reverse, composes from the outside in.  A nested
pattern

```
if (os instanceof Optional.of(Shape.redBall(var size)) { ... }
```

simply means

```
if (os instanceof Optional.of(var alpha)
    && alpha instanceof Shape.redBall(var size)) { ... }
```

where `alpha` is a synthetic variable.  We apply the outer pattern, and, if
it matches, we apply the inner patterns to the resulting bindings.  

We might still like to get rid of the boolean expression `size == 1`; this can
be handled as a nested _constant pattern_, should we decide to support them.
This might look like (illustrative syntax only):

```
if (os instanceof Optional.of(Shape.ofRedBall(== 1))) { ... }
```

where `== c` is a constant pattern that matches the constant `c`.  

### Isn't this just multiple return?

It may appear at first that patterns -- which can "return" multiple values to
their "callers" -- are really just methods with multiple return values, and
that if we had multiple return (or tuples, or "out" parameters), we wouldn't
need patterns.  But bundling return values is only half of the story.  

What's going on here is more subtle; the production of the multiple "return
values" is _conditional_ on some other calculation, and this conditional
relationship -- that the bindings are only valid if the match succeeds -- is
understood by the language (and can be incorporated into control flow analysis
through definite assignment.)

The conditionality of pattern bindings enables a more sophisticated scoping of
pattern variables, which in turn enables patterns to compose more cleanly than
simple multi-return method calls.  The "unit red ball" example above illustrates
what happens when the language can't reason for us about under what conditions
a variable is assigned; we would have to make up the difference with explicit
control flow logic (e.g., `if`), that grows more complex and error-prone the
more deeply we try to combine conditions.

### Patterns as API points

One does not need to look very far to see the amount of work we expend working
around the lack of a first-class destructuring mechanism; we're so used to doing
it that we don't even notice.  Consider the following two methods from
`java.lang.Class`:

```
boolean isArray() { ... }
Class<?> getComponentType() { ... }
```

The latter method is partial, and should only be called when the former method
returns `true`; this means that the author has to specify the precondition,
specify what happens if the precondition is not met, check for the precondition
in the implementation, and take the corresponding failure action (return `null`,
throw an exception, etc.)  Similarly, the client has to make two calls to get
the desired quantity, and therefore has more chance to get it wrong (to say
nothing of race conditions).  This is the worst of all worlds -- more work for
the library author, more work for the client, and more risk of subtle bugs.
These two methods really should be one operation, which simplifies life for both
the library and client:

```
if (c instanceof Class.arrayClass(var componentType)) { ... }
```

Now the library need expose only one API point, the client can't misuse it, and
the act-before-check bug is impossible.

> _First-class destructuring leads to APIs that are harder to misuse._

### Data-driven polymorphism

Java offers several mechanisms for polymorphism; _parametric polymorphism_
(generics), where we can share code across a family of instantiations that vary
only by type, and _inclusion polymorphism_ (subtyping),  where we can share an
_API_ across a family of instantiations that  differ more broadly.  These are
effective tools for modeling a lot of things, but sometimes we want to express
commonality between entities with some similar property, without needing to
reflect it in the type system.  Pattern matching allows us to easily express
_ad-hoc_, or _data-driven_ polymorphism.  One can easily pattern match over a
number of unrelated types or structures if needed.

Supporting ad-hoc polymorphism with pattern matching doesn't mean that
inheritance hierarchies and virtual methods are wrong -- it's just that this is
not the only useful way to attack a problem.  Sometimes, using the hierarchy is
the natural way to express what we mean, but sometimes it is not, and sometimes
it is not even possible, such as when an endpoint listens for a variety of
messages, and not all message types have a common supertype (or even come from
the same library.)  In these cases, pattern matching offers clean and simple
data-driven polymorphism.

## Patterns as class members

In languages with structural aggregate types (e.g., tuples and maps),
aggregation and destructuring are simple, well-defined, built-in operations --
because these aggregate types are just transparent wrappers for their data.  In
object-oriented languages like Java, aggregation operations are expressed in
imperative code, which can use arbitrary logic to validate the arguments and
construct the aggregate from the inputs.

For certain well-behaved classes -- those whose representation is sufficiently
coupled to their API -- it may be possible to derive deconstruction logic
without explicit imperative code.  One category of classes whose state is
trivially and transparently coupled to its API are [_records_][records], and
these will acquire destructuring patterns automatically, just as they do with
constructors.

In other cases, someone is going to have to write some code to describe how to
map the representation to the external deconstruction API, just as constructors
imperatively map the invocation arguments to the representation.  So there is
going to be some code somewhere which captures the applicability test and the
mapping between target state and the pattern's binding variables -- and the
natural place to put that code is in the same class as the constructor or
factory.

> _Patterns are executable class members._

### Anatomy of a pattern

In a pattern match like:

```
if (p instanceof Point(int x, int y)) { ... }
```

the pattern has a _name_ (`Point`), a _target operand_ (`p`), and zero or more
_binding variables_ (`x` and `y`).  For a deconstruction pattern like this, the
applicability test is simple and transparent --  is the target an instance of
the class `Point`.  But we can have patterns with more sophisticated
applicability tests, such as "does this `Optional` contain a value" or "does
this `Map` contain a mapping for a given key."

### Deconstruction patterns

A simple form of pattern is the dual of construction.  A constructor takes zero
or more state elements and produces an aggregate; the reverse starts with an
aggregate and produces corresponding state elements.  (If the term "destructor"
were not already burdened by its resource-release association from `C++`, we'd
likely call it that; instead we'll describe this form of pattern as a
_deconstructor_ or _deconstruction pattern_.)

For a deconstruction pattern, there is an obvious way to describe and reference
the target operand -- the receiver.  And there is an obvious way to declare the
arity, type, and order of the binding variables -- as a parameter list.  This
_might_ look something like:

```
class Point {
    int x, y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public deconstructor(int x, int y) {
        x = this.x;
        y = this.y;
    }
}
```

The duality between constructor and deconstructor is manifest in multiple ways:

  - **Use site syntax.**  Invoking a constructor, and invoking a deconstructor
    through a pattern match, bear a deliberate syntactic similarity: we
    construct a `Point` via its constructor `new Point(x, y)`, and we
    deconstruct a `Point` with the deconstruction pattern `Point(int x, int y)`.

  - **Invocation shape.**  The constructor arguments and deconstructor bindings
    have the same names and types.  The argument list for the constructor
    describes its inputs, and the binding variable list for the deconstructor
    describes its outputs -- and they are the same, describing the _external_
    state of the object.

  - **Behavior and body.**  The constructor copies values from the arguments to
    the object state, and the deconstructor copies values from the object state
    to the binding variables.  And, just as the constructor is free to perform
    defensive copying on the inputs, the deconstructor may wish to do the same
    with the outputs (perhaps delegating to a getter, if one is available.)

  - **Invocation and inheritance.**  Constructors are an unusual hybrid of
    instance and static behavior -- they are instance members, but are invoked
    statically and not inherited.  Deconstructors are the same.

### Method patterns

Since patterns are class members, let's enumerate some of the degrees of freedom
that methods have, and ask if they make sense for patterns as well.  (The final
design may or may not incorporate all of these aspects, but the intent of this
section is to show that the tools we use for modeling existing class members
extend equally well to patterns.)

- **Does it make sense to have accessibility modifiers on pattern declarations?**
Yes!  For example, we routinely declare protected constructors for use by
subclasses in implementing their own constructors.  In exactly the same way,
protected deconstructors can be used by subclasses in implementing their own
deconstructors.  And, just as we use private or package-private constructors to
restrict who can instantiate an object, we can use private or package-private
deconstructors to restrict who can take an instance apart and access its state.

- **Does pattern overloading make sense?**  Yes!  Just as we may want to provide
multiple overloads of a constructor, each with different descriptions of an
object's state, we may want to provide corresponding overloads of deconstructors
to mirror the state descriptions accepted by the various constructors.

- **Do static patterns make sense?**  Yes!  Just as some APIs expose static
factories rather than constructors, it also makes sense to expose static
patterns as the dual of these static factories.  For example, the factory method
`Optional.of(t)` takes a `T` and wraps it in an `Optional`; a corresponding
static pattern would take an `Optional<T>` and deconstruct it, conditionally,
giving up the contained value, and fail to match when the target `Optional` is
empty.

  Static methods have another advantage, which is that they can be declared
outside of the relevant class -- and the same is true of static patterns.  Just
as a class can expose a static factory for an unrelated class, a class can also
expose a static pattern for another class (as long as the relevant state is
accessible to the implementation of the pattern).  So, if the `Optional` class
failed to provide suitable patterns for deconstructing its instances, these
could still be provided by an unrelated library.  Similarly, APIs that use
"sidecar" classes like `Collections` to hold factories, can put patterns there
as well.

- **Do generic patterns make sense?**  Yes!  Static factories are often generic
methods, such as `Optional::of`; these generic type parameters can be used to
express type constraints on the signature.  The same is true for patterns.

- **Do instance patterns make sense?**  Yes!  Just as instance methods allow a
supertype to define the API and for subtypes to define the implementation, the
same can be done with patterns.  Like deconstruction patterns, instance patterns
implicitly use the receiver as the target to be matched.  A pattern on `Map` for
"does the map contain this key" would be an instance pattern, for example -- and
each `Map` implementation might want to provide its own implementation for this.

  An API idiom that might make use of instance patterns are the dual of
_builders_.  Builders accept a sequence of calls to add content or set
properties on the object being built.  To deconstruct an object that has been
built in this manner, we can define an _unbuilder_, which could iterate through
the structure of the object, and expose various patterns (likely corresponding
to the builder methods) for "does the current item have this structure".  This
allows the API developer to expose a rich deconstruction API without having to
make the structure directly accessible, or copy the data to another format.

- **Do abstract patterns make sense?**  Yes!  An interface such as `Map` may
want to expose a pattern which matches a `Map` which has a certain key, and if
so, binding the corresponding value, and leave the implementation to concrete
subtypes.  

- **Does overriding patterns make sense?**  Yes!  A skeletal implementation
such as `AbstractMap` could provide a least-common-denominator implementation
of a pattern, allowing concrete subtypes to override it.

- **Do varargs patterns make sense?**  Yes!  Consider a method like
`String::format`, which takes a format string and a varargs of `Object...`
arguments to be substituted into the string:

      String s = String.format("%s is %d years old", name, age);

  To reverse the encoding from data to string, we might want to expose a
complementary pattern, which exposes an `Object...` of extracted values --
which can be refined further by pattern composition:

      if (s instanceof String.format("%s is %d years old",
                                     String name, Integer.valueOf(int age))) {
          ...
      }

- **Does it make sense for patterns to delegate to other patterns?**  Yes!  Just
as constructors delegate to other constructors via `this()` or `super()`, we
expect that patterns will also want to delegate to other patterns to bind a
subset of their binding variables -- and we want it to be easy to do so.

It should not be surprising that all the degrees of freedom that make sense for
constructors and methods, also make sense for patterns -- because they describe
complementary operations.

### Additional degrees of freedom

There are also some characteristics that are applicable to patterns but not to
constructors or methods, and these influence how we might declare patterns
in source code.  

- **Targets and applicability.**  Patterns have a _target_ operand, which is the
instance against which the pattern will be matched.  They also have a static
_target type_; if the static type of the operand is not cast-convertible to the
target type, the pattern cannot match.  For a type pattern `T t` or a
deconstruction pattern `T(...)`, the target type is `T`; for a static pattern
such as `Optional::of`, the target type is `Optional`.  For deconstruction /
instance patterns, the target type is the receiver.  (For static patterns, the
target type must be explicitly specified somehow as part of its declaration,
along with some way of denoting a reference to the target.)

- **Totality vs partiality.**  Some patterns are _total_ on their target type,
meaning that all (non-null) instances of that type will match the pattern; type
patterns and deconstruction patterns are total in this way.  Others are
_partial_, where not only must the target be of a certain type, but it also must
satisfy some predicate; `Optional.of(T t)` is such a pattern (it doesn't match
anything that is not an `Optional`, but it further requires it to be non-empty.)
If totality is a property of a pattern declaration, not just its implementation,
then the compiler can reason about when it can guarantee a match (and thus not
require some sort of unreachable `else` logic.) For partial patterns, there must
also be some way for the declaration to indicate a failure to match, whether
this be throwing an exception, returning a sentinel, or some other mechanism.
(For simplicity, we may decide that deconstruction patterns are always total and
other patterns are always partial.)

- **Input and output arguments.**  The patterns we've seen so far have a target
and zero or more binding variables, which can be thought of as outputs.  Some
patterns (such as "the target is a `Map` containing a mapping whose key is X")
may also need one or more _inputs_.  This puts some stress on the syntactic
expression of both the declaration and the use of a pattern, as it should be
obvious on both sides which arguments are inputs and which are outputs.

- **Exhaustiveness.**  In some cases, groups of patterns (such as `Optional::of`
and `Optional::empty`) are total _in the aggregate_ on a given type.  It would
be good if we could reflect this in the declaration, so that the compiler could
see that the following switch is exhaustive:

  ```
  Optional<Foo> o = ...;
  switch (o) {
      case Optional.empty(): ...
      case Optional.of(var foo): ...
      // Ideally, no default would be needed
  }
  ```

## Combining patterns

We've already seen one way to combine patterns -- nesting.  A nested pattern:

```
if (x instanceof Optional.of(Point var x, var y)) { ... }
```

is equivalent to:

```
if (x instanceof Optional.of(var p)
    && p instanceof Point(var x, var y)) { ... }
```

(Note that it is possible to express this decomposition using `if` and
`instanceof`, but not currently when the pattern is used in `switch`; there are
a number of possible ways to address this, which will be taken up in  a separate
document.)

Another possibly useful way to combine patterns is by AND and OR operators.
Suppose we have a pattern for testing whether a map has a given key (the
use-site syntax shown here is solely for purposes of exposition):

```
if (m instanceof Map.withMapping("name", var name)) { ... }
```

If we want to extract multiple mappings at once, we could of course use `&&`
(if we're in the context of an `if`):

```
if (m instanceof Map.withMapping("name", var name)
    && m instanceof Map.withMapping("address", var address)) { ... }
```

But, we can more directly express this as an AND pattern (combination syntax
chosen only for purposes of exposition):

```
if (m instanceof (Map.withMapping("name", var name)
                  __AND Map.withMapping("address", var address))) { ... }
```

This is somewhat more direct, eliminating the repetition of asking `instanceof`
twice, but more important, allows us to use compound patterns in other
pattern-aware constructs as well.

One of the nice properties of combining patterns (whether through nesting or
through algebraic combinators) is that the compound pattern is all-or-nothing;
either the whole thing matches and all the bindings are defined, or none of them
are.  

### A possible approach for parsing APIs

The techniques outlined so far pave the way for vastly improving APIs for
decomposing aggregates like JSON documents.  Taking an example from the JSONP
API, the following builder code:

```
JsonObject value = factory.createObjectBuilder()
     .add("firstName", "John")
     .add("lastName", "Smith")
     .add("age", 25)
     .add("address", factory.createObjectBuilder()
         .add("streetAddress", "21 2nd Street")
         .add("city", "New York")
         .add("state", "NY")
         .add("postalCode", "10021"))
     .build();
```     

creates the following JSON document:

```
{
    "firstName": "John",
    "lastName": "Smith",
    "age": 25,
    "address" : {
        "streetAddress": "21 2nd Street",
        "city": "New York",
        "state": "NY",
        "postalCode": "10021"
    }
}
```

The builder API allowed us to construct the document cleanly, but if we wanted
to parse the result, the code is far larger, uglier, and more error-prone -- in
part because we have to constantly stop and ask questions like "was there a key
called `address`?"  "Did it map to an object?"  We want to express the shape of
document we expect, and then have it either match, or not -- all-or-nothing.
One possible way to get there is by composing patterns.  

To start with, let's posit that we add some patterns to the API for `intKey`,
`stringKey`, `objectKey`, etc, which mean "does the current object have a key
that maps to this kind of value (similar to our `Map.withMapping` pattern.)
Now, we could parse the above document with something like:

```
switch (doc) {
    case stringKey("firstName", var first)
         __AND stringKey("lastName", var last)
         __AND intKey("age", var age)
         __AND objectKey("address",
                 stringKey("city", var city)
                 __AND stringKey("state", var state)
                 __AND ...): ...
}
```

This code looks more like the document we are trying to parse, and also has the
advantage that either the whole expression matches and all the bindings are
defined, or none of them are.  

### Down the road: structured patterns?

For each of the idioms for aggregation, we have seen that we can construct a
pattern which serves as its dual.  If, in some future version of Java, we added
_collection literals_, this would be a new idiom for aggregation.  And, as with
the other forms, there is an obvious corresponding dual for destructuring.

Suppose, for example, we could construct a `Map` as follows (again, syntax
purely for sake of exposition):

```
Map<String, String> m = {
    "name" : name,
    "age" : age
};
```

Then, we could similarly expose a _map pattern_ for deconstructing it:

```
if (m instanceof
    {
        "name": var name,
        "age": var age
    }) { ... }
```

And, just as with other patterns, these compose via nesting:

```
if (doc instanceof
    {
        "firstName": var first,
        "lastName", var last,
        "age", Integer.valueOf(var age),
        "address" : {
             "city": var city,
             "state": var state
        }
    }) { ... }
```

### Flatter APIs

One possible consequence of having patterns in our API toolbox is that APIs may
become "flatter".  In the `Shape` example above, its conceivable that  one might
expose an API that has lots of public static factories that  correspond to
private implementation classes, and public static patterns for identifying these
instances, but not exposing types for these various configurations at all.  This
allows APIs to use subtyping primarily as an implementation technique, but
expose polymorphism through patterns rather than type hierarchies.  For some
APIs, this may be a perfectly sensible move.


[patternmatch]: pattern-matching-for-java
[patternsem]: pattern-match-semantics
[gof]: https://en.wikipedia.org/wiki/Design_Patterns
[records]: ../records-and-sealed-classes
