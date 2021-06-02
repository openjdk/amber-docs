# Towards Better Serialization

#### Brian Goetz, June 2019 {.author}

This document explores a possible direction for improving
serialization in the Java Platform.  This is an exploratory document
only and does not constitute a plan for any specific feature in any
specific version of the Java Platform.

## Motivation

Java's serialization facility is a bit of a paradox.  On the one hand,
it was probably critical to Java's success -- Java would probably not
have risen to dominance without it, as serialization enabled the
transparent remoting that in turn enabled the success of Java EE.  On
the other hand, Java's serialization makes nearly every mistake
imaginable, and poses an ongoing tax (in the form of maintenance
costs, security risks, and slower evolution) for library maintainers,
language developers, and users.

To be clear, there's nothing wrong with the _concept_ of
serialization; the ability to convert an object into a form that can
be easily transported across JVMs and reconstituted on the other side
is a perfectly reasonable idea.  The problem is with the _design_ of
serialization in Java, and how it fits (or more precisely, does not
fit) into the object model.

## What's wrong with serialization?

The mistakes made by Java's serialization are manifold.  A partial
list of sins includes:

 - **Pretends to be a library feature, but isn't.** Serialization
pretends to be a library feature -- you opt in by implementing the
`Serializable` interface, and serialize with `ObjectOutputStream`.  In
reality, though, serialization extracts object state and recreates
objects via privileged, extralinguistic mechanisms, bypassing
constructors and ignoring class and field accessibility.

 - **Pretends to be a statically typed feature, but isn't.**
Serializability is a function of an object's dynamic type, not its
static type; `implements Serializable` doesn't actually mean that
instances are serializable, just that they are not overtly
serialization-hostile.  So, despite the requirement to opt-in via the
static type system, doing so gives you little confidence that your
instances are actually serializable.

 - **The compiler won't help you.** There are all sorts of mistakes
one can make when writing serializable classes, and the compiler does
not help you identify them.  Instead, errors are caught at runtime.
(For example, there's no way for the `TreeMap` constructor to say that
the `Comparator` passed to its constructor should be serializable, so
that the compiler could warn you if you make a mistake.)

 - **Magic methods and fields.** There are a number of "magic" methods
and fields (in the sense that they are not specified by any base class
or interface) that affect the behavior of serialization.  (Before
reading on, close your eyes and try to name them all.  Can you?
Probably not.)  These include: `readObject`, `writeObject`,
`readObjectNoData`, `readResolve`, `writeReplace`, `serialVersionUID`,
and `serialPersistentFields`.  Because these do not exist in any
public type, they're hard to discover, and one cannot easily navigate
to their specification.  They are also easy to accidentally get wrong;
if you spell them wrong, or get the signature wrong, or make them
static members when they should be instance members, no one tells you.

 - **Woefully imperative.** If you want a customized serial form, you
can implement the methods `readObject()` and `writeObject()`.  But one
cannot easily read the code and deduce the serial form -- it's
implicit in the bodies of these methods, and it's on you to ensure
that they're consistent with each other.  Further, if you use these
methods, it's on you to build in a versioning mechanism from the
beginning (which is easy to forget), since otherwise this will cause
pain when you go to evolve your representation or invariants and want
to maintain serialization compatibility.  And it's also on you to
repeat (in a syntactically different form) the validity checking the
constructor does in `readObject` -- and keep the two in sync.

 - **Tightly coupled to encoding.** The serialization mechanism is
tightly coupled to its bytestream encoding.  This makes it
unnecessarily difficult to reuse serialization logic with other
encodings such as JSON or XML; the logic for deconstructing and
reconstructing the object is intertwined with the logic for reading
from and writing to the stream.

 - **Unfortunate stream format.** That we are stuck with the
serializion stream format is made worse by the fact that this format
is neither compact, nor efficient, nor human-readable.

These design choices leads directly to the following serious problems:

 - **Cripples library maintainers.** Library designers must think very
carefully before publishing a serializable class -- as doing so
potentially commits you to maintaining compatibility with all the
instances that have ever been serialized.  While the default
serialization scheme has some ability to deal with simple
representational evolution, evolving serializable classes is far more
constrained than evolving non-serializable ones, because there is an
implicit public API with which you must maintain compatibility.  And
if you choose explicit control over serialization (`readObject()` and
`writeObject()`), you need to manually build versioning into your
serial form.

 - **Makes a mockery of encapsulation.** Because an object is
serialized by scraping its state and is recreated through an
extralinguistic mechanism that bypasses user-written constructors,
choosing serializability effectively means forgoing the benefits of
encapsulation.  Serialization constitutes an invisible but public
constructor, and an invisible but public set of accessors for your
internal state.  This means it is easy to sneak bad data into a
serializable class (unless you've (painfully) duplicated your argument
checking between constructors and `readObject()`, in which case you
have lost the biggest benefit of Java's serialization mechanism:
that it supposedly comes for free).

 - **Readers cannot verify correctness merely by reading the code.**
In an object-oriented system, the role of the constructor is to
initialize an object with its invariants established; this allows the
rest of the system to assume a basic degree of object integrity.  In
theory, we should be able to reason about the possible states an
object might be in by reading the code for its constructors and any
methods that mutate the object's state.  But because serialization
constitutes a hidden public constructor, you have to also reason about
the state that objects might be in based on _previous versions_ of the
code (whose source code might not even exist any more, to say nothing
of maliciously constructed bytestreams).  By bypassing constructors,
serialization completely subverts the integrity of the object model.

 - **Too hard to reason about security.** The variety and subtlety of
security exploits that target serialization is impressive; no ordinary
developer can keep them all in their head at once.  Even security
experts can review serialization code and miss vulnerabilities.  It is
just too hard to secure serialization for trusted code -- because
serialization operates mostly invisibly, and is controlled by arcane
low-level mechanisms that do not fit into an intuitive model of how
classes work.  As a trivial example of what class writers have to
contend with, suppose you have a `final` field, initialized by
`myField = new Foo(arg)`, and not otherwise exposed to callers.  You
might be forgiven for assuming that the only outstanding reference to
this object is the one held by `myField`, or that the type of the
object to which it refers is exactly `Foo` and not one of its
subtypes, but if your class is serializable, you have to make heroic
efforts to preserve these implicit invariants -- if you even remember
that you have to.

 - **Impedes language evolution.** Complexity in programming
languages comes from unexpected interactions between features, and
serialization interacts with nearly everything.  Nearly every feature
added to the language must engage, some way, with serialization.  Some
of the most subtle aspects of the Java Memory Model were motivated by
serialization's need to write final fields after construction.  (Think
about that for a moment; the memory model is supposed to describe the
language's _low level interaction with hardware_, and we needed to
distort it to accomodate serialization!)  A nontrivial fraction of the
design effort for Lambdas involved interaction with serialization --
and the best we could accomplish was a compromise that no one could
really like all that much.  (This even bubbled up into the syntax,
requiring the need to express intersection types in casts -- purely to
accomodate serializability.)  And the same will be true with records,
and value types, and probably everything else in our future.
Serialization is an ongoing tax on evolving the language.

## The underlying mistake

Many of the design errors listed above stem from a common source --
the choice to implement serialization by "magic" rather than giving
deconstruction and reconstruction a first-class place in the object
model itself.  Scraping an object's fields is magic; reconstructing
objects through an extralinguistic back door is more magic.  Using
these extralinguistic mechanisms means we're outside the object model,
and thus we give up on many of the benefits that the object model
provides us.

Worse, the magic does its best to remain invisible to the reader.  It
would be one thing if there were big signs planted near the magic
warning us "Dark magic here!" -- at least we might stop and think
about what non-obvious things are going on.  But with invisible magic,
we continue to think that our primary job is designing a bulletproof
API and implementing our business logic, when in fact we've left the
back door wide open and unguarded.

The allure of magic is obvious; just sprinkle some serialization dust
over your classes, and voila: instant transparent remoting!  But
the accumulated cost is crippling.

In ["Goto Considered Harmful"](https://www.cs.utexas.edu/users/EWD/transcriptions/EWD02xx/EWD215.html),
Dijkstra offers a rational basis for why a language with `goto` places
an unreasonable cognitive load on developers.  The same arguments apply
equally well to the current state of serialization:

> My second remark is that our intellectual powers are rather geared
> to master static relations and that our powers to visualize
> processes evolving in time are relatively poorly developed. For that
> reason we should do (as wise programmers aware of our limitations)
> our utmost to shorten the conceptual gap between the static program
> and the dynamic process, to make the correspondence between the
> program (spread out in text space) and the process (spread out in
> time) as trivial as possible.

Serialization, as it is currently implemented, does the exact opposite
of minimizing the gap between the text of the program and its
computational effect; we could be forgiven for mistakenly assuming
that our objects are always initialized by the constructors written in
our classes, but we shouldn't have to be.

In addition to trying to be invisible, serialization also tries to do
_too much_.  That is, it aims to be able to serialize any object graph
and reconstitute it, at full fidelity and in full working order, on
the other side.  But in reality, the vast majority of use cases for
serialization don't involve serializing _programs_, but merely
serializing _data_ -- which is a far easier problem.  (This mistake is
understandable in historical context, since at the time the industry
believed that distributed objects were going to save us from
complexity.)  Many of the sins of serialization were committed in the
desire to get that last .1%, but the cost and benefit of that last
.1% are woefully out of balance.

### Why not "just" use JSON or XML or protobuf or ...

Much ink has been spilled over the choice of bytestream-encoding
format, but in reality this is the least of our concerns.  While it is
unfortunate that choosing an alternate encoding is unnecessarily
difficult, and that the encoding we have is obscure and inefficient,
switching to another encoding doesn't solve the main problem of
serialization, which is: how we can safely extract the state of
objects to be serialized without compromising the integrity of the
access control model, and safely reconstruct them on the other side
with their invariants intact?  These problems have to be solved before
we can even talk about bytestream encoding.

### Why not write a new serialization library?

There is a veritable cottage industry of libraries that are either
intended as serialization "replacements", or that are valid
alternatives to serialization for some use cases (an incomplete list:
Arrow, Avro, Bert, Blixter, Bond, Capn Proto, CBOR, Colfer, Elsa,
Externalizor, FlatBuffers, FST, GemFire PDX, Gson, Hessian, Ion,
Jackson, JBoss Marshaling, JSON.simple, Kryo, Kudu, Lightning,
MessagePack, Okapi, ORC, Paranoid, Parcelable, Parquet, POF, Portable,
Protocol Buffers, Protostuff, Quickser, ReflecT, Seren, Serial,
Simple, Simple Binary Encoding, SnakeYAML, Stephenerialization,
Thrift, TinySerializer, travny, Verjson, Wobly, Xson, XStream,
YamlBeans, and surely more).

While some of these exist to provide Java bindings for popular
cross-language serialization approaches (e.g., CBOR, Protocol
Buffers), most represent someone's attempt to do a "better"
serialization.  And it's worth asking: what did they think "better"
meant?  To end up with such a broad variety of attempts suggests that
there is a broad variety of metrics on which existing options are
considered "not good enough", and surely some of these are
intrinsically in tension with others (for example, some might consider
using JSON to be an advantage for reasons of human readability or
interoperability; others see it as an inefficient and error-prone
encoding).  But very few of these alternate libraries attempt to
address the fundamental programming-model or security concerns -- they
are mostly concerned either with encoding format, efficiency, or
flexibility.

## What does success look like?

As we've said, there's nothing fundamentally wrong with the _concept_
of serializing objects to a bytestream.  But, if we want to avoid the
problems described so far, we're going to have to adjust our goals and
priorities.  Let's try to state some assumptions about what success
looks like, and set out some terminology.

For the remainder of this document, _serialization_ will refer to the
abstract concept of turning objects into bytestreams and
reconstituting them, a _serialization framework_ is a library or
facility that implements some form of serialization, and _Java
Serialization_ will refer to the specific serialization framework
built into the platform and defined by the [_Java Object Serialization
Specification_](https://docs.oracle.com/en/java/javase/12/docs/specs/serialization/index.html).

### Narrow the requirements

We've noted above that one of the problems with Java Serialization is
that it tries to do too much -- to ensure that an arbitrary object
graph can be persisted and reconstituted in perfect operational order.
But this reflects a view of the world that has not come to pass, and
modern expectations of a serialization mechanism are generally far
more modest; applications use serialiation to persist data, or to
exchange data with other applications.  Not objects; data.

We can make serialization considerably simpler and safer by narrowing
the goals explicitly to reflect this reality:

> We should seek to support serialization of _data_, not _objects_.

For example, many modern services are built around exchanging JSON
documents -- which cannot even represent the notion of object
identity!  The fact that JSON is considered a viable encoding strategy
for nearly all services underscores the fact that Java serialization
is solving a much harder problem than it actually needs to.

Much of the complexity and risk of Java Serialization stems from the
desire to transparently serialize logically cyclic data, such as
collections that contain themselves (which cannot be represented by
JSON documents at all).  Similarly, many of the potential attacks
rely on exploiting backreferences -- which comes from the desire to
produce a topologically identical copy.

### Make serialization explicit

Java serialization is transparent; this was assumed to be one of its
primary benefits.  But this transparency is also a weakness, and we
can easily forget that we are dealing with a serializable class.  The
Java language provides tools for building robust, secure APIs; Java
developers know how to write constructors which validate their
arguments and make defensive copies of mutable data, and how to use
non-public members to keep certain operations out of the public-facing
API.  But Java serialization constitutes an implicit public API, and
because it is often invisible, it is too easy to forget to secure it.

When we design a class, we're often thinking about its more typical
clients -- ordinary Java code that will access or extend our class,
through the documented, public-facing API.  Let's call this the "front
door" API or the "user-facing" API.  But classes often have APIs that
are for a different category of clients -- frameworks such as
serialization, mocking, or dependency injection.  We commonly expose
API points intended for use by frameworks, that we do not necessarily
want to expose to "ordinary" users.  Let's call these the "back door"
APIs.

The problem is not that we have back-door APIs; it is that the
back-door APIs are implicit, and therefore too hard for class authors
to secure and for clients to reason about.

> It should be as easy to secure the "back door" API as it is the
> "front door" API -- and ideally we would do so with the same
> techniques.

At the very least, we want to make it harder to forget to consider
serialization; this calls for making the back door API used by
serialization explicit.  It would be even better if securing this API
could use the same techniques that developrs are already familiar with
(such as defensive constructors).  And it would be better still if
this back-door API could directly share members with the front door
API, such as using a constructor both for programmatic instantiation
and for deserialization.

Another form of convenient, but dangerous, implicitness is
inheritance.  Because serializability is indicated by implementing an
interface, making a class or interface `Serializable` puts the
responsibility for serialization on all your subclasses.  This makes
it too easy for classes to be serializable without realizing it --
and therefore at risk for leaving the back door wide open.

### Bring serialization into the object model

If the major sins of serialization are related to its extralinguistic
nature, then the cure is to bring serialization back within the
language and the object model, so that developers are equally in
control of the front-door and back-door APIs.  This means not only
providing explicit control over _whether_ a class is serializable, but
also _how_ it is to be serialized and deserialized, and _by whom_.
Some basic requirements include:

 - Serializable classes should be designed for serialization; authors
   should provide class members that deconstruct and reconstruct the
   object.  It should be clear from reading the source or
   documentation for a class that it is designed for serialization.

 - Authors should have control over the _serialized form_ of their
   classes; this is the logical at-rest state that can be written to a
   stream to represent an instance of the class.  (This can be, but
   need not be, similar to the in-memory representation (fields) of
   the class.)  The serial form should be manifest in the code so
   readers can reason about it, and the choice of serial form should
   be orthogonal to the choice of bytestream encoding.

 - Deserialized objects should be created through ordinary
   constructors or factories, to get the full benefit of validity
   checking and defensive copies.  The constructor used for
   deserialization could be, but need not be, shared with the
   front-door API.

 - Schema evolution should be manifest in the source code.  It should
   be clear which old versions of the serial form a class agrees to or
   refuses to deserialize, and how they map to the current
   representation.

Bringing serialization into the object model in this way means that,
by reading the code for a class, we can see all the ways in which an
instance might come into existence, and validate that each way
properly respects the invariants of the _current_ representation.
(Ideally the validation code is shared between constructors used for
deserialization and those used for routine instantiation, thereby
eliminating another place for bugs to hide.)

### Break up the monolith

Java serialization is a monolithic facility, spanning state
extraction, object graph traversal, wire encoding, and reconstruction,
and several of these aspects use privileged magic to do their job.  If
you want any of them, you have to take them all together; if you are
building a serialization framework, you have to reinvent them all.
Components of Java serialization include:

 - **State extraction.** Java serialization uses reflection to extract
   the non-transient fields of an object, using its privileged status
   to access otherwise inacessible fields.

- **Serial form.** Java serialization strongly encourages using an
   object's in-memory state as its serial form.  Sometimes this is a
   sensible choice, but sometimes this is a terrible choice, and
   overriding this choice currently involves using a difficult and
   error-prone mechanism (`readObject` and `writeObject`.)

- **Versioning.** Classes evolve over time; Java serialization forces
   implementations to confront past (and possibly future) versions of
   their serial form.  It should be easy and explicit to mediate
   between different versions of serial form and live object state.
   However, unless you plan for versioning from the beginning, it can
   be very difficult to version the serialized form with the tools
   available without sacrificing compatibility.

- **Reconstruction.** Like deconstruction, Java serialization leans on
   privileged reflective mechanisms to reconstruct an object's state.
   This should be carried out in concert with the object's
   implementation, so that invariants can be validated, maliciously
   constructed instances rejected, and the serial form mapped to a
   sensible in-memory equivalent, but unless the author has
   implemented `readObject` and `writeObject`, serialization will just
   accept whatever state it found in the bytestream.  The lack of
   flexibility and transparency here is the source of many of core
   serialization's biggest weaknesses.

- **Stream format.** The choice of stream format is probably the
   least interesting part of a serialization mechanism; once a
   suitable serial form is chosen, it can be encoded with any number
   of encodings.

- **Relaxing encapsulation.**  In the desire to make serialization
   ubiquitous and transparent, core serialization is willing to
   trample on the encapsulation of any class.  While we likely do want
   to strike a balance between the competing needs of "API users" and
   serialization with respect to encapsulation, it would be better if
   this were more explicitly reflected in the programming model.

To improve the state of serialization, we focus on bringing the touch
points with serialization directly into the object model.  Not only
does this put authors more in control of how these tasks are
performed, but it also pulls them out of the otherwise monolithic
serialization stack, where they can be reused by all serialization
frameworks.

### Non-goals

It is not a goal _of this effort_ to address resource-consumption attacks
(such as ["Billion
Laughs"](https://en.wikipedia.org/wiki/Billion_laughs_attack)); these may
be the subject of other efforts.

## A concrete proposal

Our approach centers around factoring state extraction and object
reconstruction out of Java serialization and into the object model.
Further, to the extent that the "back door" API requires relaxation of
accessibility, this should also be explicit and under the control of
the author.  This puts authors in control, makes serialization obvious
to readers, and allows all serialization frameworks to reuse the logic
provided by the user.  In an ideal world, serialization frameworks
should not require special privileges.

Over time, we can migrate JDK classes away from the serialization
artifacts supported by Java serialization (e.g., `readObject`) and
towards the more explicit mechanism.

### Sidebar: pattern matching

It should be clear enough how we might explicitly represent
reconstruction of an object from its serialized form -- a constructor
or factory method.  To represent the other direction -- state
extraction -- we need to borrow some machinery from an upcoming
language feature: [_pattern matching_][patterns].  Pattern matching
provides class authors with the ability to implement mediated
destructuring logic as part of a class's API -- which is exactly what
we need for extracting the serialized form of an instance.

In the following `Point` class, the author has provided a constructor
and a _deconstruction pattern_ that share the state description `(int
x, int y)`:

```{.java}
public class Point {
    private final int x;
    private final int y;

    // Constructor
    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // Deconstruction pattern
    public pattern Point(int x, int y) {
        x = this.x;
        y = this.y;
    }
}
```

The declared pattern `Point` acts as a "reverse constructor"; where a
constructor takes state components as arguments and aggregates them
into a `Point` object, a deconstruction pattern takes a `Point` object
and breaks it down into its state components.  The "argument list" of
the pattern is actually a declaration of a list of _binding
variables_, which can be thought of as being the "output parameters"
upon a successful match:

```{.java}
if (o instanceof Point(var xVal, yVal)) {
    // can use xVal, yVal here
}
```

Note that the argument list of the constructor `Point` and the binding
variable list of the deconstruction pattern `Point` are compatible;
this means that, while these members are part of the front-door API,
they are also suitable for serializing a `Point` using the serialized
form `(int x, int y)` and deserializing it back into a `Point`.  All
we need now is for the author to tell us that the class is
serializable, and which members should be used for serialization and
deserialization.  (Just as an author can expose either a constructor
or a factory as part of its API, pattern matching allows for multiple
kinds of patterns, including _deconstruction patterns_ (analogous to
constructors, in reverse) and _static patterns_ (analogous to static
factories, in reverse), and both are suitable for serializing
instances.)

### Serializers and deserializers

The first thing we have to add to the object model is the ability to
project an instance into its serial form.  Sometimes the serial form
will exactly coincide with the in-memory representation (fields) of
the object, such as for most simple domain objects like our `Point`
above.  Other times, the serial form may discard accidental
implementation detail and strip the serial form down to its essence,
or uplevel it to a more descriptive form.  (For example, the existing
serial form for `LinkedList` discards the internal link nodes and just
serializes the elements, and on deserialization, adds the elements
into an empty list.)

As our `Point` example hinted, we want to use linguistic constructs
(constructors, factories, and patterns) to declare serializers (to
convert an object into its serial form) and deserializers (to convert
the serial form back into an object).  The benefits of this approach
are:

 - The serial form is under the control of the author, and explicit in
   the code, and can be mechanically transcribed into Javadoc or
   accessed via reflection;
 - The serial form can be decoupled from the in-memory representation;
 - Extraction and reconstruction can be factored from the stream
   representation -- one can take a serializable object and serialize
   to byte streams, JSON, XML, etc., using the same basic extraction
   and reconstruction mechanism;
 - We can statically validate that serialization and deserialization
   class members are compatible; and
 - Deserialization ultimately proceeds through constructors, and
   therefore the serialized form can be validated before the object is
   created, rather than merely accepting whatever (potentially
   malicious) data was present in the bytestream.

The last point is the most important, as it is key to taming the
security risks inherent in serialization.  While we're used to coding
constructors and factories defensively, deserialization is when we
most need that defensiveness -- and when we currently use it the
least!  Deserialization should be able to proceed through the same
defensive code as objects created through the primary API.

Finally, we must mark the members to be used for serialization and
deserialization, so that a serialization framework will know that a
constructor can be used as a deserializer for the corresponding serial
form.  We can use ordinary annotations for this.  Here's an example of
what a class designed for serialization might look like under this
scheme.

```{.java}
public class Range {
    int lo;
    int hi;

    private Range(int lo, int hi) {
        if (lo > hi)
            throw new IllegalArgumentException(String.format("(%d,%d)",
                                                             lo, hi));
        this.lo = lo;
        this.hi = hi;
    }

    @Serializer
    public pattern Range(int lo, int hi) {
        lo = this.lo;
        hi = this.hi;
    }

    @Deserializer
    public static Range make(int lo, int hi) {
        return new Range(lo, hi);
    }
}
```

In this example, we've chosen a serial form that matches the
representation.  The "front door" API has a public factory and a
public deconstruction pattern that share this form; by applying the
appropriate annotations, we capture the author's design intent that
these members are also suitable for serialization and deserialization.
(These annotations are not special; they serve only to capture the
author's design intent, and can be acted on by serialization
frameworks.)  It is not required that the serialization members be
part of the "front door" API, but is allowable and often convenient to
do so.  (If `Point` were declared as a [record][records], it would
acquire a public deconstruction pattern and constructor for free,
which could be used by a serialization framework.)

Note that we've not said anything about `implements Serializable`;
we've just annotated the matcher and factory to say that these members
are suitable for deconstructing and reconstructing a `Range`.  This is
deliberate; rather than encouraging users to write `Serializable`
classes, we encourage users to write classes that are _ready for
serialization_, using primitives provided by the language, and
capturing the author's design intent through annotations.  Then any
serialization framework can use the members marked by the author as
intended for deconstruction and reconstruction.

This approach captures a common idiom already used by some
serializable classes: the _serialization proxy pattern_ (which
involves using explicit `readResolve` and `writeReplace` methods to
override the default choice of serialized form, and replace it with a
custom state carrier, and reconstitute the object through a
constructor at deserialization time).  While a serialization framework
is free to encode the serialized form as it chooses, a sensible
encoding would be to encode the name of the class, some version
information (see below), and the serializations of the components of
the serial form.  (Obviously, these components must themselves be
serializable somehow.)

### Alternate serial forms

Not all classes want to use their in-memory representation as their
serial form.  Consider `LinkedList`, which, rather than serializing
the full graph including the internal doubly-linked nodes, instead
merely serializes the elements, and reconstructs the list at
deserialization time by adding them to an empty list.  (In this case,
this trick turns a cyclic data structure into a serial form that is
(usually) backreference-free, and also reduces the size of the serial
form.)  We can represent this as having a serial form of `(Object[]
elements)`, and code it as follows:

```{.java}
public class LinkedList {
    @Serializer
    public pattern serialize(Object[] elements) {
        elements = toArray();
    }

    @Deserializer
    public static LinkedList deserialize(Object[] elements) {
        LinkedList list = new LinkedList();
        for (Object e : elements)
            list.add(e);
        return list;
    }
}
```

Another situation where we might want to use a serial form that
doesn't match our representation is when we have components that are
not serializable, or for some reason we don't want to serialize them
directly.  As an example, consider a `ServerConnection` class which is
not serializable, and a `ServerMonitor` class that maintains a
connection to a server.  The constructor for `ServerMonitor` takes the
name of the server to connect to.  As our serial form, instead of
serializing the `ServerConnection`, we could instead serialize the
information needed to reconstruct the `ServerConnection`:

```{.java}
class ServerMonitor {
    private final ServerConnection conn;

    @Deserializer
    public ServerMonitor(String serverName) {
        conn = new ServerConnection(serverName);
    }

    @Serializer
    public pattern serializeMe(String serverName) {
        serverName = conn.getName();
    }
}
```

A side-benefit of using this technique is that it can protect against
aliasing attacks; in the class as written, the `ServerConnection` is
fully encapsulated, but using ordinary serialization, it's possible
(using backreferences) for malicious serialization code to obtain a
reference to the underlying `ServerConnection` at deserialization time,
and potentially mutate it.  (It is possible to guard against this
attack today, but very easy to forget to do so.)  By reconstructing
our state from its base ingredients, rather than just serializing it
directly, we gain defense against such attacks (as well as the ability
to reconstruct nonserializable state).

### Versioning

Over time, the representation of our objects may evolve or may
acquire new invariants.  It's easy to change our code, and to change
the serializer/deserializer to match the latest version, but we may
also want to continue to support deserialization of objects that were
serialized under previous versions (or alternately, not).  Explicit
control over versioning can be easily added to the mechanism outlined
above.  If we assume that each version is identified by a version tag,
all we need to do is ensure that each supported version of the
serialized form has a deserializer that accepts that version of the
serialized form.

Suppose we have a class `C` which is evolved over time.  In version 1,
it has only an `a` field; in version 2, a `b` field is added (for
which zero is a suitable default); and in version 3, a `c` field is
simlarly added.  Over time, `C` can accrete additional deserializers
to handle the various forms it expects to encounter:

```{.java}
class C {
    int a;
    int b;
    int c;

    @Deserializer(version = 3)
    public C(int a, int b, int c) {
        this a = a;
        this.b = b;
        this.c = c;
    }

    @Deserializer(version = 2)
    public C(int a, int b) {
        this(a, b, 0);
    }

    @Deserializer(version = 1)
    public C(int a) {
        this(a, 0, 0);
    }

    @Serializer(version = 3)
    public pattern C(int a, int b, int c) {
        a = this.a;
        b = this.b;
        c = this.c;
    }
}
```

Now, it is clear exactly which old serialized forms `C` is expecting,
and how they map to the current representation.  If the author decides
that supporting a specific old version is impractical or undesirable,
all they need do is not provide a deserializer for that version (or,
to make it even more explicit, a deserializer that always throws), and
such forms will be rejected at deserialization time.  Again, this is
all under the control of the class author, and manifest in the source
and documentation.

### Encapsulation relaxation

The above examples focused on the common case where serialization and
deserialization proceeded through members of the public (front door)
API.  However, sometimes we might not want the members designed for
serialization to be part of the public API, but may still want
serialization frameworks to be able to dynamically invoke them.

The natural way to denote the intended set of API users is through
accessibility modifiers (e.g, `public`, `private`).  Which leaves us
looking for a way to denote the fact that a given serializer or
deserializer should always be accessible reflectively to serialization
frameworks, even if it is otherwise inaccessible (or the class itself
is inaccessible).

We've encountered this exact same problem before with modules, where
we wanted to make one set of choices for exporting packages for direct
use by clients, and another set of choices for _opening_ them for deep
reflective access by frameworks (such as dependency injection).  The
different categories of users want to interact with our API in
different ways; we'd like to expose the smallest API to each that
meets the requirements.

We could address this by saying that any module that wants to
serialize non-public classes or classes using non-public serialization
members use the `opens` facility of the module declaration, and then
have serialization uses unprivileged reflection and `setAccessible()`
to access the serialization members.  But this is a blunt tool; we
probably want something more direct, both for means of explicitness
(so the member is explicitly identified in the source as "frameworks
are going to invoke this reflectively, and that's OK") and granularity
(we probably do not want to open an entire package to reflection just
to support serialization of a few classes; we may prefer to expose
only a few specific members of specific classes).  Rather than
requiring modules that have serializable classes to open them widely,
or relying on the special magic privileges of Java serialization,
let's again declare this explicitly in the class, by extending the
notion of `open` from packages in module descriptors to individual
members in classes.  For consistency with modules, let's call this
`open`, and its meaning is just as it is with packages in a module:
that it should be reflectively accessible even when they are otherwise
inaccessible.

```{.java}
class Foo {
    private final InternalState is;

    public Foo(ExternalState es) {
        this(new InternalState(es));
    }

    @Deserializer
    private open Foo(InternalState is) {
        this.is = is;
    }

    @Serializer
    private open pattern serialize(InternalState is) {
        is = this.is;
    }
}
```

The `open` modifier would permit core reflection to allow the member
to be dynamically invoked, even though its accessibility is otherwise
restricted -- whether because the member itself is inaccessible, or
the containing class is inaccessible, or the containing package is not
exported or opened.  (We choose a language keyword here rather than an
annotation because it actually affects accessibility semantics, rather
than merely signaling design intent.)

The combination of `private` and `open` means that for purposes of
ordinary invocation, the method can only be invoked from members of
the same class or nest, but can be reflectively invoked by anyone.
Because the serializer and deserializer are private, they don't show
up in JavaDoc or in the IDE's completion or navigation aids, but
serialization frameworks (with no special privilege except that
granted by `open` and core reflection) can still invoke the
serialization-related members.

### Adapting Java serialization

It is a relatively simple matter to adapt Java serialization (or any
serialization framework) to support the mechanisms outlined here.
When an object is presented for serialization, it can introspect for a
suitable serializer; serializers must either be accessible to the
client requesting serialization, or be `open` (either explicitly or
through the module declaration).  If a serializer is found, an object
description is written to the bytestream containing the name of the
class, its serialization version (from the serializer), and the
serialization of the components of its serial form.

In the other direction, when a serialization framework encounters an
object description corresponding to an explicit serial form, the
framework again uses ordinary unprivileged reflection to find a
suitable deserializer for that class, version, and serial form, which
again is subject to the same access control requirements.

Java serialization would use the new mechanism in preference to the
legacy serialization approaches; other serialization frameworks would
be free to select their own policies.

For new language features such as value types, we can go farther: make
the new serialization approach the _only_ way to serialize a value
type.

### Adapting the JDK

Existing serializable classes in the JDK can be adapted to support the
new mechanism, by adding serializers and deserializers.

Historically the JDK has followed a policy of "one forward, one back"
for serialization compatibility; this means that an instance that is
serialized on JDK N should (at least) deserialize on JDK N-1 and N+1.
(Among other things, this allows for rolling upgrades of clusters
across consecutive JDK versions.)

We can migrate JDK classes compatibly via a three-step process:

 - Version N: add a _deserializer_, but not a serializer.  This will
   enable serialized instances from version N+1 to be deserialized,
   providing the one-forward part of the story.

 - Version N+1: add a _serializer_, but leave old serialization members
   in place.  This allows instances from version N to be deserialized,
   providing the one-back part of the story.

 - Version N+2 (or later, depending on policy choices): remove old
   serialization members, and replace with a `readObject()` method
   that throws.  Now, legacy instances cannot be deserialized.

At the current six-month cadence, legacy serialization can be phased
out of JDK classes in as little as 18 months.

### Limitations

There are two main limitations of the approach outlined here.  The
first is the inability to represent cyclic object graphs, such as
lists that contain each other.  (However, we should note that this
exclusion does not apply to object graphs whose cyclicity is a merely
a representational artifact, such as the link nodes in a doubly-linked
list -- these can be serialized by extracting a more abstract,
non-cyclic state description (such as the elements themselves) rather
than the physical representation.)  This is indeed a real restriction;
on the other hand, JSON is incapable of representing cycles and it is
still a very popular encoding target.  If our goal is to serialize
_data_, it seems an acceptable restriction to exclude cyclic graphs.

The other main limitation is that every class must carry its own
serialization behavior, rather than inheriting it from a supertype
that implements `Serializable` or using default serialization.  This
may be a challenge for inner classes and lambdas, for which declaring
the appropriate members may not be syntactically possible, and for
which we might have to make some special accomodation in the language.
This limitation is also, in some way, a benefit -- it is far more
obvious what is going to happen when an instance is serialized or
deserialized, since all the code is in one place, and subclasses do
not inherit the serialization vulnerabilities of their supertypes.

## Summary

We've factored out a number of concerns that used to be part of
Java serialization, and made them part of the object model:

 - Using factories, constructors, and matchers for explicit
   serialization and deserialization;
 - Publishing a trivial set of annotations intended to capture
   serialization-related design intent;
 - Classes can additionally permit dynamic frameworks to access
   specific members with the explicit "dynamically public" `open`
   modifier, relieving serialization frameworks of the need for
   special accessibility relaxation;
 - Serialization frameworks (including Java serialization) can use
   these annotated members to safely extract and reconstitute state,
   using ordinary access control.

The result is that classes that are _designed for serialization_ can
be serialized and deserialized safely, under the control of the class,
with no magic extralinguistic state-scraping, reconstruction, or
accessibility mechanisms, by any serialization framework.  And
deserialization always proceeds through a constructor that can
validate its inputs.

By "safely", we don't mean magic security dust; the language cannot
defend against constructors that do not validate their arguments.
Instead, we mean that the mechanisms for managing the integrity of the
API exposed for programmatic use are also used for (or even shared
with) the API implicitly exposed for construction and deconstruction
via serialization.  With some extra work, and some limitations, we can
get away from the undesirable status quo of the remotely-accessible
API being _less_ defensive than the programmatic API.

[records]: records-and-sealed-classes
[patterns]: patterns/pattern-matching-for-java
