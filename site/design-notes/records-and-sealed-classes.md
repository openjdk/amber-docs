# Data Classes and Sealed Types for Java

#### Brian Goetz, February 2019 {.author}

This document explores possible directions for _data classes_ and
_sealed types_ in the Java Language, and is an update
to [Data Classes in Java](data-classes-historical-2).  This is an
exploratory document only and does not constitute a plan for any
specific feature in any specific version of the Java Language.

## Background

It is a common (and often deserved) complaint that "Java is too
verbose" or has too much "ceremony."  A significant contributor to
this is that while classes can flexibly model a variety of programming
paradigms, this invariably comes with modeling overheads -- and in the
case of classes that are nothing more than "plain data carriers",
these modeling overhead can be out of line with their value.  To write
a simple data carrier class responsibly, we have to write a lot of
low-value, repetitive code: constructors, accessors, `equals()`,
`hashCode()`, `toString()`, etc.  And developers are sometimes tempted
to cut corners such as omitting these important methods, leading to
surprising behavior or poor debuggability, or pressing an alternate
but not entirely appropriate class into service because it has the
"right shape" and they don't want to define yet another class.

IDEs will help you _write_ most of this code, but writing code is only
a small part of the problem.  IDEs don't do anything to help the
_reader_ distill the design intent of "I'm a plain data carrier for
`x`, `y`, and `z`" from the dozens of lines of boilerplate code.  And
repetitive code is a good place for bugs to hide; if we can, it is
best to eliminate their hiding spots outright.

There is no formal definition of "plain data carrier", and opinions
may vary on what exactly "plain" means.  Nobody thinks that
`SocketInputStream` is just a carrier for some data; it fully
encapsulates some complex and unspecified state (including a native
resource) and exposes an interface contract that likely looks nothing
like its internal representation.

At the other extreme, it's pretty clear that:

```
    final class Point {
        public final int x;
        public final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        // state-based implementations of equals, hashCode, toString
        // nothing else
   }
```

is "just" the data `(x, y)`.  Its representation is `(x, y)`, its
construction protocol accepts an `(x, y)` pair and stores it directly
into the representation, it provides unmediated access to that
representation, and derives the core `Object` methods directly from
that representation.  And in the middle, there are grey areas where
we're going to have to draw a line.

Other OO languages have explored compact syntactic forms for modeling
data-oriented classes: `case` classes in [Scala][scaladata], `data`
classes in [Kotlin][kotlindata], and `record` classes in
[C#][csharpdata].  These have in common that some or all of the state
of a class can be described directly in the class header -- though
they vary in their semantics (such as constraints on the mutability or
accessibility of fields, extensibility of the class, and other
restrictions.)  Committing in the class declaration to at least part
of the relationship between state and interface enables suitable
defaults to be derived for many common members.  All of these
mechanisms (let's call them "data classes") seek to bring us closer to
the goal of being able to define `Point` as something like:

    record Point(int x, int y) { }

The clarity and compactness here is surely attractive -- a `Point` is
just a carrier for two integer components `x` and `y`, and from that,
the reader immediately knows that there are sensible _and correct_
implementations for the core `Object` methods, and doesn't have to
wade through a page of boilerplate to be able to confidently reason
about their semantics.  Most developers are going to say "Well, of
course I want _that_."  Further, it moves us closer to the place where
the only code that is present is the code that actually does something
non-obvious, which makes reading code easier in two ways: there's less
to read, and every line says something useful.

### Meet the elephant

Unfortunately, such universal consensus is only syntax-deep; almost
immediately after we finish celebrating the concision, comes the
debate over the natural semantics of such a construct, and what
restrictions we are willing to accept.  Are they extensible?  Are the
fields mutable?  Can I control the behavior of the generated methods,
or the accessibility of the fields?  Can I have additional fields and
constructors?

Just like the story of the blind men and the elephant, developers are
likely to bring very different assumptions about the "obvious"
semantics of a data class.  To bring these implicit assumptions into
the open, let's name the various positions.

 - *Algebraic Annie* will say "a data class is just an algebraic product
type."  Like Scala's case classes, they come paired with pattern
matching, and are best served immutable.  (And for dessert, Annie
would order sealed interfaces.)

 - *Boilerplate Billy* will say "a data class is just an ordinary class
with better syntax", and will likely bristle at constraints on
mutability, extension, or encapsulation.  (Billy's brother, JavaBean
Jerry, will say "of course, these are the replacement for JavaBeans --
so I need mutability and getters and setters."  And his sister, POJO
Patty, remarks that she is drowning in enterprise POJOs, and hopes
they are proxyable by frameworks like Hibernate.)

 - *Tuple Tommy* will say "a data class is just a nominal tuple" -- and
may not even be expecting them to have methods other than the
core `Object` methods -- they're just the simplest of aggregates.  (He
might even expect the names to be erased, so that two data classes of
the same "shape" can be freely converted.)

 - *Values Victor* will say "a data class is really just a more
transparent value type."

All of these personae are united in favor of "data classes" -- but
have different ideas of what data classes are, and there may not be
any one solution that makes them all happy.

### Encapsulation and boundaries

While we're painfully aware of the state-related boilerplate we deal
with every day, the boilerplate is just a symptom of a deeper problem,
which is that Java asks all classes to pay equally for the
cost of encapsulation -- but not all classes benefit equally from it.

To be sure, encapsulation is essential; mediating access to our state
(so it can't be manipulated without oversight) and encapsulating our
representation (so it can be evolved without affecting the API
contract) enables us to write code that can operate safely and
robustly across a variety of _boundaries_:

 - Maintenance boundaries -- when our clients are in a different
   sourcebase (or organization);
 - Security and trust boundaries -- where we do not want to expose our
   state to clients because we do not fully trust them to not
   deliberately modify or use it in malicious ways;
 - Integrity boundaries -- where we do not want to expose our state to
   clients because, while we may trust their intent and are willing to
   share our data with them, do not wish to burden them with the task
   of maintaining our own representational invariants;
 - Versioning boundaries -- where we want to ensure that clients
   compiled against one version of a library continue to work when run
   against a subsequent version.

But, not all classes value their boundaries equally.  Defending these
boundaries is essential for a class like `KeyStore` or
`SocketInputStream`, but is of far less value for a class like `Point`
or for typical domain classes.  Many classes are not concerned at all
with defending their boundaries, such as those that are private to a
package or module and co-compiled with their clients, trust their
clients, and have no complex invariants that need protecting.  Since
the cost of establishing and defending these boundaries (how
constructor arguments map to state, how to derive the equality
contract from state, etc) is constant across classes, but the benefit
is not, the cost may sometimes be out of line with the benefit.  This
is what Java developers mean by "too much ceremony" -- not that the
ceremony has no value, but that _they're forced to invoke it even when
it does not offer sufficient value_.

The encapsulation model that Java provides -- where the representation
is entirely decoupled from construction, state access, and equality --
is just more than many classes need.  Classes that have a simpler
relationship with their boundaries can benefit from a simpler model
where we can define a class as a thin wrapper around its state, and
derive the relationship between state, construction, equality, and
state access from that.

Further, the costs of decoupling representation from API goes beyond
the overhead of declaring boilerplate members; encapsulation is, by
its nature, information-destroying.  If you see a class with a
constructor that takes an argument `x`, and an accessor called `x()`,
we often have only convention to tell us that they probably refer to
the same thing.  Relying on this may be a pretty safe guess, but its
just a guess.  It would be nicer if tools and library code could
mechnically rely on this correspondence -- without a human having to
read the specs (if there even is one!) to confirm this expectation.

### Digression --- enums

If the problem is that we're modeling something simple with something
overly general, simplification is going to come from constraint; by
letting go of some degrees of freedom, we hope to be freed of the
obligation to specify everything explicitly.

The `enum` facility, added in Java 5, is an excellent example of such
a tradeoff.  The type-safe enum pattern was well understood, and easy
to express (albeit verbosely), prior to Java 5
(see [Effective Java, 1st Edition][ej], item 21.)  The initial
motivation to add enums to the language might have been irritation at
the boilerplate required for this idiom, but the real benefit is
semantic.

The key simplification of enums was to constrain the lifecycle of
their instances -- enum constants are singletons, and instantiation is
managed by the runtime.  By baking singleton-awareness into the
language model, the compiler can safely and correctly generate the
boilerplate needed for the type-safe enum pattern.  And because enums
started with a semantic goal, rather than a syntactic one, it was
possible for enums to interact positively with other features, such as
the ability to `switch` on enums, or to get comparison and safe
serialization for free.

Perhaps surprisingly, enums delivered their syntactic and semantic
benefits without requiring us to give up most other degrees of freedom
that classes enjoy; Java's enums are not mere enumerations of
integers, as they are in many other languages, but instead are
full-fledged classes (with some restrictions.)

If we are looking to replicate the success of this approach with data
classes, our first question should be: what constraints will give us
the semantic and syntactic benefits we want, and, are we willing to
accept these constraints?

### Priorities and goals

While it is superficially tempting to to treat data classes as
primarily being about boilerplate reduction, we prefer to start with a
semantic goal: _modeling data as data_.  If we choose our goals
correctly, the boilerplate will take care of itself, and we will gain
additional benefits aside from concision.

So, what do we mean by "modeling data as data", and what are we going
to have to give up?  What degrees of freedom that classes enjoy do
such "plain" data aggregates not need, that we can eliminate and
thereby simplify the model?  Java's object model is built around the
assumption that we want the representation of an object to be
completely decoupled from its API; the APIs and behavior of
constructors, accessor methods, and `Object` methods need not align
directly with the object's state, or even with each other.  However,
in practice, they are frequently much more tightly coupled; a `Point`
object has fields `x` and `y`, a constructor that takes `x` and `y`
and initializes those fields, accessors for `x` and `y`, and `Object`
methods that characterize points solely by their `x` and `y` values.
We claim that for a class to be "just a plain carrier for its data",
this coupling is something that can be counted upon -- that we're
giving up the ability to decouple its (publicly declared) state from
its API.  The API for a data class models _the state, the whole state,
and nothing but the state_.  One consequence of this is that data
classes are _transparent_; they give up their data freely to all
requestors.  (Otherwise, their API doesn't model their whole state.)

Being able to count on this coupling drives a number of advantages.
We can derive sensible and correct implementations for standard class
members.  Clients can freely deconstruct and reconstruct aggregates,
or restructure them into a more convenient form, without fear that
they will discard hidden data or undermine hidden assumptions.
Frameworks can safely and mechanically serialize or marshal them,
without the need to provide complex mapping mechanisms.  By giving up
the flexibility to decouple a classes state from its API, we gain all
of these benefits.

### Records and sealed types

We propose to surface data classes in the form of _records_; like an
`enum`, a `record` is a restricted form of class.  It declares its
representation, and commits to an API that matches that
representation.  We pair this with another abstraction, _sealed
types_, which can assert control over which other types may be its
subclasses.  (A `final` class is the ultimate form of sealed class; it
permits no subtypes at all.)

If we wanted to model simple arithmetic expressions with records
and sealed types, it would look something like this:

```{.java}
sealed interface Expr { }

record ConstantExpr(int i) implements Expr { }
record PlusExpr(Expr a, Expr b) implements Expr { }
record TimesExpr(Expr a, Expr b) implements Expr { }
record NegExpr(Expr e) implements Expr { }
```

This declares four concrete types, `ConstantExpr` (which holds a
single integer), `PlusExpr` and `TimesExpr` (which hold two
subexpressions), and `NegExpr` (which holds one subexpression.)  It
also declares a common supertype for expressions, and captures the
constraint that these are the _only_ subtypes of `Expr`.

The concrete types acquire all the usual members -- (final) fields,
constructors, accessors, `equals()`, `hashCode()`, and `toString()`.
(If the default implementations are unsuitable, they can be specified
explicitly.)

Records use the same tactic as enums for aligning the
boilerplate-to-information ratio: offer a constrained version of a
more general feature that enables standard members to be derived.
Enums ask the user to cede instance control to the runtime; in
exchange, the language can offer streamlined declaration of instances,
as well as provide implementations for core behaviors such as
`Object::equals`, `Enum::values`, and serialization.  For records, we
make a similar trade; we give up the flexibility to _decouple the
classes API from its state description_, in return for getting a
highly streamlined declaration (and more).

### Records and pattern matching

One of the big advantages of defining data classes in terms of
coupling their API to a publicly specified state description, rather
than simply as boilerplate-reduced class, is that we gain the ability
to freely convert a data class instance back and forth between its
aggregate form and its exploded state.  This has a natural connection
with [_pattern matching_][patterns]; by coupling the API to the state
description, we also can derive the obvious deconstruction pattern --
whose signature is the dual of the constructor's.

Given our `Expr` hierarchy, the code for evaluating an expression
looks like this:

```{.java}
int eval(Expr e) {
    return switch (e) {
        case ConstantExpr(var i) -> i;
        case PlusExpr(var a, var b) -> eval(a) + eval(b);
        case TimesExpr(var a, var b) -> eval(a) * eval(b);
        case NegExpr(var e) -> -eval(e);
        // no default needed, Expr is sealed
    }
}
```

using the mechanically generated pattern extractors that records
automatically acquire.  Both records and sealed types have a synergy
with pattern matching; records admit easy decomposition into their
components, and sealed types provide the compiler with exhaustiveness
information so that a switch that covers all the subtypes need not
provide a `default` clause.

### Records and externalization

Data classes are also a natural fit for safe, mechanical
externalization (serialization, marshaling to and from JSON or XML,
mapping to database rows, etc).  If a class is a transparent carrier
for a state vector, and the components of that state vector can in
turn be externalized in the desired encoding, then the carrier can be
safely and mechanically marshaled and unmarshaled with guaranteed
fidelity, without the security and integrity risks of bypassing the
constructor (as built-in serialization does).  In fact, a transparent
carrier need not do anything special to support externalization; the
externalization framework can deconstruct the object using its
deconstruction pattern, and reconstruct it using its constructor,
which are already public.

### Why not "just" do tuples?

Some readers will surely be thinking at this point: if we "just" had
tuples, we wouldn't need data classes.  And while tuples might offer a
lighter-weight means to express some aggregates, the result is often
inferior aggregates.

Classes and class members have meaningful names; tuples and tuple
components do not.  A central aspect of Java's philosophy is that
_names matter_; a `Person` with properties `firstName` and `lastName`
is clearer and safer than a tuple of `String` and `String`.  Classes
support state validation through their constructors; tuples do not.
Some data aggregates (such as numeric ranges) have invariants that, if
enforced by the constructor, can thereafter be relied upon; tuples do
not offer this ability.  Classes can have behavior that is derived
from their state; co-locating state and derived behavior makes it more
discoverable and easier to access.

For all these reasons, we don't want to abandon classes for modeling
data; we just want to make modeling data with classes simpler.  The
major pain of using named classes for aggregates is the overhead of
declaring them; if we can reduce this sufficiently, the temptation to
reach for more weakly typed mechanisms is greatly reduced.  (A good
starting point for thinking about records is that they are _nominal
tuples_.)

### Are records the same as value types?

With _value types_ coming down the road through [Project
Valhalla][valhalla], it is reasonable to ask about the overlap between
(immutable) data classes and value types, and as whether the
intersection of data-ness and value-ness is a useful space to inhabit.

Records and value types have some obvious similarities; they are both
immutable aggregates, with restrictions on extension.  Are these
really the same feature in disguise?

When we look at their semantic goals, we can see that they differ.
Value types are primarily about enabling _flat_ and _dense_ layout of
objects in memory.  In exchange for giving up _object identity_ (which
in turn entails giving up mutability and layout polymorphism), the
runtime gains the ability to optimize the heap layout and calling
conventions for values.  With records, in exchange for giving up the
ability to decouple a classes API from its representation, we gain a
number of notational and semantic benefits.  But while some of what we
give up is the same (mutability, extension), some values may still
benefit from state encapsulation, and some records may still benefit
from identity, so they are not the exact same trade.  However, there
are classes that may be willing to tolerate both restrictions, in
order to gain both sets of benefits -- we might call these `value
records`.  So while we wouldn't necessarily want to only have one
mechanism or the other, we certainly want the mechanisms to work
together.

### Digression: algebraic data types

The combination of data classes and sealed types are a form of
[_algebraic data types_][adt], which refers to the combination of
_product types_ and _sum types_.

A product type gets its name from _cartesian product_, because its
value set is the cartesian product of the value sets of a vector of
types.  Tuples are a kind of product type, as are our `records`, as
are many ad-hoc domain classes (such as a `Person` type having String
fields `firstName` and `lastName`.)  A sum type is a discriminated
union of a fixed set of types; enums are a kind of union (where the
union members are constants).

Some languages have direct support for declaring algebraic data types;
for example, we would declare the equivalent of our `Expr` hierarchy
in Haskell with the `data` construct:

    data Expr = ConstantExpr Int
              | PlusExpr Expr Expr
              | TimesExpr Expr Expr
              | NegExpr Expr
    deriving (Show, Eq);

(The `deriving` clause says that these types automagically acquire the
obvious equivalents of `Object::equals` and `Object::toString`.)

## Use cases

Use cases abound for records and sealed hierarchies of records.  Some
typical examples include:

- **Tree nodes.** The `Expr` example earlier shows how records can make
short work of tree nodes, such as those representing documents,
queries, or expressions, and sealing enables developers and compilers
to reason about when all the cases have been covered.  Pattern
matching over tree nodes offers a more direct and flexible alternative
to traversal than the Visitor pattern.

- **Multiple return values.** It is often desirable for a method to
return more than one thing, whether for reasons of efficiency
(extracting multiple quantities in a single pass may be more efficient
than making two passes) or consistency (if operating on a mutable data
structure, a second pass may be operating on different state.)

  For example, say we want to extract both the minimal and maximal value
of an array.  Declaring a class to hold two integers may seem
overkill, but if we can reduce the declaration overhead sufficiently,
it becomes attractive to use a custom product type to represent these
related quantities, enabling a more efficient (and readable)
computation:

  ```{.java}
  record MinMax(int min, int max);

  public MinMax minmax(int[] elements) { ... }
  ```

  As noted earlier, some users would surely prefer we expose this
ability via structural tuples, rather than via a nominal mechanism.
But having reduced the cost of declaring the `MinMax` type to
something reasonable, the benefit starts to come into line with the
cost: nominal components such as `min` and `max` are both more
readable and less error-prone than a simple structural tuple. A
`Pair<int,int>` doesn't tell the reader what it represents; `MinMax`
does (and, the compiler will prevent you from accidentally assigning a
`MinMax` to a `Range`, even though both could be modeled as pairs of
ints.)

- **Data transfer objects.**   A _data transfer object_ is an aggregate
whose sole purpose is to package up related values so they can be
communicated to another activity in a single operation.  Data transfer
objects typically have no behavior other than storage, retrieval, and
marshaling of state.

- **Joins in stream operations.**  Suppose we have a derived quantity,
and want to perform stream operations (filtering, mapping, sorting)
that operate on the derived quantity.  For example, suppose we want
to select the `Person` objects whose name (normalized to uppercase)
has the largest `hashCode()`.  We can use a `record` to temporarily
attach the derived quantity (or quantities), operate on them, and then
project back to the desired result, as in:

  ```{.java}
  List<Person> topThreePeople(List<Person> list) {
      // local records are OK too!
      record PersonX(Person person, int hash) {
          PersonX(Person person) {
              this(person, person.name().toUpperCase().hashCode());
          }
      }

      return list.stream()
                 .map(PersonX::new)
                 .sorted(Comparator.comparingInt(PersonX::hash))
                 .limit(3)
                 .map(PersonX::person)
                 .collect(toList());
  }
  ```

  Here, we start by adjoining the `Person` with the derived quantity,
then we can do ordinary stream operations on the combination, and then
when we're done, we throw away the wrapper and extract the `Person`.

  We could have done this without materializing an extra object, but
then we would potentially have to compute the hash (and the uppercase
string) many more times for each element.

- **Compound map keys.** Sometimes we want to have a `Map` that is keyed
on the conjunction of two distinct domain values.  For example,
suppose we want to represent the last time a given person was seen in
a given place: we can easily model this with a `HashMap` whose key
combines `Person` and `Place`, and whose value is a `LocalDateTime`.
But if your system has no `PersonAndPlace` type, you have to write one
with boilerplate implementations of construction, equals, hashCode,
etc.  Because the record will automatically acquire the desired
constructor, `equals()`, and `hashCode()` methods, it is ready to be
used as a compound map key:

  ```{.java}
  record PersonPlace(Person person, Place place) { }
  Map<PersonPlace, LocalDateTime> lastSeen = ...
  ...
  LocalDateTime date = lastSeen.get(new PersonPlace(person, place));
  ...
  ```

- **Messages.** Records and sums of records are commonly useful for
representing messages in actor-based systems and other
message-oriented systems (e.g., Kafka.)  The messages exchanged by
actors are ideally described by products; if an actor responds to a
set of messages, this is ideally described by a sum of products.  And
being able to define an entire set of messages under a sum type
enables more effective type checking for messaging APIs.

- **Value wrappers.**  The `Optional` class is an algebraic data type in
disguise; in languages with algebraic data types and pattern matching,
`Optional<T>` is typically defined as a sum of a `Some(T value)` and a
`None` type (a degenerate product, one with no components).
Similarly, an `Either<T,U>` type can be described as a sum of a
`Left<T>` and a `Right<U>` type.  (At the time `Optional` was added to
Java, we had neither algebraic data types nor pattern matching, so it
made sense to expose it using more traditional API idioms.)

- **Discriminated entities.** A more sophisticated example is an API
that needs to return a discriminated entity.  For example, in [JEP
348][jep348], the Java compiler is extended with a mechanism by which
invocations of JDK APIs can be transformed at compile time to a more
efficient representation.  This involves a conversation between the
compiler an an "intrinsic processor"; the compiler passes information
about the call site to the processor, which returns a description of
how to transform the call (or not).  The options are: transform to an
`invokedynamic`, transform to a constant, or do nothing.  Using sealed
types and records, the API would look like:

  ```{.java}
  interface IntrinsicProcessor {

      sealed interface Result {
          record None() implements Result;
          record Ldc(ConstantDesc constant) implements Result;
          record Indy(DynamicCallSiteDesc site, Object[] args)
              implements Result;
      }

      public Result tryIntrinsify(...);
  }
  ```

  In this model, an intrinsic processor receives information about the
call site, and returns either `None` (do no transformation), `Indy`
(replace the call with the specified `invokedynamic`), or `Ldc`
(replace the call with the specified constant.)  This sum-of-products
is compact to declare, easy to read, and easy to deconstruct with
pattern matching; without such a mechanism, we might well be tempted to
structure the API is a less readable or more error-prone manner.

## Records

Records give up a key degree of freedom that classes usually enjoy --
the ability to decouple a classes API from its representation.  A
record has a name, a state description, and a body:

```{.java}
record Point(int x, int y) { }
```

Because a record is "the state, the whole state, and nothing but the
state", we are able to derive most of the members mechanically:

  - a private `final` field, with the same name and type, for each
    component in the state description;
  - a public read accessor method, with the same name and type, for
    each component in the state description;
  - a public constructor, whose signature is the same as the state
    description, which initializes each field from the corresponding
    argument;
  - a public deconstruction pattern, whose signature is the same as
    the state description, which extracts each field into the
    corresponding binding slot;
  - implementations of `equals` and `hashCode` that say two records
    are equal if they of the same type and contain the same state;
  - implementation of `toString` that includes all the components,
    with their names.

The representation, and the protocols for construction, deconstruction
(either a deconstructor pattern, or accessors, or both), equality, and
display are all derived from the same state description.

### Customizing records

Records, like enums, are classes.  The record declaration can have
most of the things class declarations can: accessibility modifiers,
Javadoc, annotations, an `implements` clause, and type variables
(though the record itself is implicitly final.)  The component
declarations can have annotations and accessibility modifiers (though
the components themselves are implicitly `private` and `final`).  The
body may contain static fields, static methods, static initializers,
constructors, instance methods, instance initializers, and nested
types.

For any of the members that are implicitly provided by the compiler,
these can also be declared explicitly.  (However, carelessly
overriding accessors or `equals`/`hashCode` risks undermining the
semantic invariants of records.)

Further, some special consideration is provided for explicitly
declaring the default constructor (the one whose signature matches
that of the record's state description.)  The argument list is omitted
(because it is identical to the state description); further, any
record fields which are _definitely unassigned_ on all normal
completion paths are implicitly initialized from their corresponding
arguments (`this.x = x`) on exit.  This allows the constructor body to
specify only argument validation and normalization, and omit the
obvious field initialization.  For example:

```{.java}
record Range(int lo, int hi) {
    public Range {
        if (lo > hi)
            throw new IllegalArgumentException(String.format("(%d,%d)", lo, hi));
    }
}
```

The implicit deconstruction pattern is derived from the accessors for
the fields, so if these are overridden, this will be reflected in
deconstruction semantics as well.

A record that is declared in a nested context is implicitly static.

### Odds and ends

The above is just a sketch; there's lots of smaller details to work
out.

- **Javadoc.** Since the fields and accessor methods are declared as
part of the class declaration, we will want to adjust the Javadoc
conventions a bit to accommodate this.  This can be done by permitting
the `@param` tag on records, which can be propagated to the field and
accessor documentation.

- **Annotations.** Record components constitute a new place to put
annotations; we'll likely want to extend the `@Target` meta-annotation
to reflect this.

- **Reflection.** Since being a record is a semantic statement,
record-ness -- and the names and types of the state components --
should be available reflectively.  We may wish to consider a base type
for records (as `enum` classes have) where additional methods and/or
specifications can live.

- **Serialization.** One of the advantages of records is that we can
mechanically derive a safer protocol for marshaling and unmarshaling.
A sensible way to take advantage of this is for records that implement
`Serializable` to automatically acquire a `readResolve` method which
extracts the state and runs it back through the constructor, to
prevent malicious streams from injecting bad data.

- **Extension.** It would be possible to permit records to extend
_abstract records_, which would allow related records to share certain
members.  We should hold this possibility in reserve.

- **Compatibility.** Since the arity, names, and types of the components
are propagated directly into the signatures and names of members,
changes to the state description may not be source- or
binary-compatible.

- **Named invocation.** Because the names of the components form part of
the record's API, this opens the door to _named invocation_ of
constructors -- which allows us, in most cases, to forgo the companion
_builder_ that often goes along with domain classes.  (While it would
be nice to support this for all classes, records have some special
properties that make this much less complicated; we might consider
starting with records and then extending.)

### Restrictions

Careful readers will have noted several restrictions: record fields
cannot be mutable; no fields other than those in the state description
are permitted; and records cannot extend other types or be extended.

It is easy to imagine situations where each of these restrictions will
feel stifling, and tempting to try to make the construct more flexible
in order to broaden its applicability.  But, we should not do so at
the expense of the conditions that make it work in the first place --
that we can derive everything from a single state description.

- **Extension.** If our mantra is that the record's state description is
"the state, the whole state, and nothing but the state", then this
rules out extending anything (except possibly abstract records),
because we cannot be sure that there is no state hidden in the
superclass.  Similarly, if records can be extended, then their state
description is, again, not a complete description of their state.
(Note that in addition to excluding ordinary extension, this also
excludes dynamic proxies.)

- **Mutability.** The stricture against mutability is more complex,
because in theory one can imagine examples which do not fall afoul of
the goals.  However, mutability puts pressure on the alignment between
the state and the API.  For example, it is generally incorrect to base
the semantics of `equals()` and `hashCode()` on mutable state; doing
so creates risks that such elements could silently disappear from a
`HashSet` or `HashMap`.  So adding mutability to records also likely
means we may want a different equality protocol from our state
description; we may also want a different construction protocol (many
domain objects are created with no-arg constructors and have their
state modified with setters, or with a constructor that takes only the
"primary key" fields.)  Now, we've lost sight of the key
distinguishing feature: that we can derive the key API elements from a
single state description.  (And, once we introduce mutability, we need
to think about thread-safety, which is going to be difficult to
reconcile with the goals of records.)

  As much as it would be nice to automate away the boilerplate of
mutable JavaBeans, one need only look at the many such attempts to do
so (Lombok, Immutables, Joda Beans, etc), and look at how many "knobs"
they have acquired over the years, to realize that an approach that is
focused exclusively on boilerplate reduction _for arbitrary code_ is
guaranteed to merely create a new kind of boilerplate.  These classes
simply have too many degrees of freedom to be captured by a single
simple description.

  Nominal tuples with clearly defined semantics is something that can
make our programs both more concise _and_ more reliable for a lot of
use cases -- but there are still use cases beyond the limits of what
they can do for us.  (That doesn't mean that there aren't things we
can do for these classes too -- it just means that this feature will
not be the delivery vehicle for them.)  So, to be clear: records are
not intended to replace JavaBeans, or other mutable aggregates -- and
that's OK.

- **Additional fields.** A related tension is whether a record can
declare fields other than those that are part of the state
description.  (And again, one can easily imagine examples where this
is safe.)  On the other hand, this capability again introduces the
temptation to violate "the state, the whole state, and nothing but the
state" -- a temptation best avoided.

## Sealed types

A _sealed type_ is one for which subclassing is restricted according
to guidance specified with the type’s declaration.  (Finality can be
considered a degenerate form of sealing.)

Sealing serves two distinct purposes. The first is that it restricts
who can be a subtype. This is largely a declaration-site concern,
where an API owner wants to defend the integrity of their API. The
other, less obvious benefit is that it potentially enables
_exhaustiveness analysis_ at the use-site, such as when switching over
type patterns in a sealed type.

We specify that a class is sealed by applying the `sealed` modifier to
a class, abstract class, or interface, with an optional `permits`
list:

```
sealed interface Node
     permits A, B, C { ... }
```

In this explicit form, `Node` may be extended only by the types
enumerated in the `permits` list (which must further be members of the
same package or module.)  In many situations, this may be overly
explicit; if all the subtypes are declared in the same compilation
unit, we can omit the `permits` clause, in which case the compiler
infers it by enumerating the subtypes in the current compilation unit.

Anonymous subclasses (and lambdas) of a sealed type are prohibited.

Sealing, like finality, is both a language and JVM feature; the
sealed-ness of a type, and its list of permitted subtypes, are reified
in the classfile so that it can be enforced at runtime.

The list of permitted subtypes should also be incorporated somehow
into the Javadoc.  Note that this is not exactly the same as the
current "All implementing classes" list that Javadoc currently
includes, so a list like "All permitted subtypes" might be added
(possibly with some indication if the subtype is less accessible than
the parent, or including an annotation that there exist others that
are not listed.)

### Exhaustiveness

One of the benefits of sealing is that the compiler can enumerate the
permitted subtypes of a sealed type; this in turn lets us perform
exhaustiveness analysis when switching over patterns involving sealed
types.

_Note:_  It is superficially tempting to say `permits package` or
`permits module` as a shorthand, which would allow for a type to be
extended by package-mates or module-mates without listing them all.
However, this would undermine the compiler’s ability to reason about
exhaustiveness, because packages and modules are not always
co-compiled.

On the other hand, subtypes need not be as accessible as the sealed
parent.  In this case, some clients may not get the chance to
exhaustively switch over them; they'll have to make these switches
exhaustive with a `default` clause or other total pattern. When
compiling a switch over such a sealed type, the compiler can provide a
useful error message ("I know this is a sealed type, but I can't
provide full exhaustiveness checking here because you can't see all
the subtypes, so you still need a default.")

### Inheritance

Unless otherwise specified, abstract subtypes of sealed types are
implicitly sealed, and concrete subtypes are implicitly final.  This
can be reversed by explicitly modifying the subtype with `non-sealed`.
(Not for records, though; they are always `final`.)

Unsealing a subtype in a hierarchy doesn't undermine all the benefits
of sealing, because the (possibly inferred) set of explicitly
permitted subtypes still constitutes a total covering. However, users
who know about unsealed subtypes can use this information to their
benefit (much like we do with exceptions today; you can catch
`FileNotFoundException` separately from `IOException` if you want, but
don't have to.)

An example of where explicit unsealing (and private subtypes) is
useful can be found in the JEP 334 API:

```{.java}
sealed interface ConstantDesc
    permits String, Integer, Float, Long, Double,
            ClassDesc, MethodTypeDesc, MethodHandleDesc,
            DynamicConstantDesc { }

sealed interface ClassDesc extends ConstantDesc
    permits PrimitiveClassDescImpl, ReferenceClassDescImpl { }

private class PrimitiveClassDescImpl implements ClassDesc { }
private class ReferenceClassDescImpl implements ClassDesc { }
sealed interface MethodTypeDesc extends ConstantDesc
    permits MethodTypeDescImpl { }

sealed interface MethodHandleDesc extends ConstantDesc
    permits DirectMethodHandleDesc, MethodHandleDescImpl { }
sealed interface DirectMethodHandleDesc extends MethodHandleDesc
    permits DirectMethodHandleDescImpl { }

// designed for subclassing
non-sealed class DynamicConstantDesc extends ConstantDesc { ... }
```


## Summary

The combination of records and sealed types follows a powerful, and
well-understood, pattern for describing related groups of structured
data, and there are many situations where it can improve the
readability and concision of common code.  Records may not be suitable
for all code that would like some relief from the boilerplate of
declaration; we plan to continue to look into features that can
improve the situation for these use cases as well.



[valhalla]: http://openjdk.java.net/projects/valhalla/
[kotlindata]: https://kotlinlang.org/docs/reference/data-classes.html
[csharpdata]: https://github.com/dotnet/roslyn/blob/features/records/docs/features/records.md
[scaladata]: https://docs.scala-lang.org/tour/case-classes.html
[ej]: https://www.amazon.com/gp/product/0321356683?ie=UTF8&tag=briangoetz-20&camp=1789&linkCode=xm2&creativeASIN=0321356683
[uniform]: https://en.wikipedia.org/wiki/Uniform_access_principle
[adt]: https://en.wikipedia.org/wiki/Algebraic_data_type
[patterns]: https://openjdk.java.net/jeps/305
[jep348]: https://openjdk.java.net/jeps/348
