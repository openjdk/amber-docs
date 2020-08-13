# Functional transformation of immutable objects
#### Brian Goetz, Aug 2020

This document is an _early stage draft_ outlining a possible direction for supporting _functional transformation_ in the Java Language. This is an exploratory document only and does not constitute a plan for any specific feature in any specific version of the Java Language. This document also may reference other features under exploration; this is purely for illustrative purposes, and does not constitute any sort of plan or commitment to deliver any of these features.

Everyone likes records.  But, adding features like records and deconstruction,
which solves some old problems, raises news ones.  And, everything we can do
with records but not with classes increases a gap where users might feel they
have to make a hard choice; it's time to start charting the path of generalizing
the record "goodies" so that suitable classes can join in the fun.

This document focuses primarily on one specific goodie, which we'd like to endow
to records and then as soon as possible extend to classes: functional
transformation.  There are other potential new goodies for records (e.g.,
keyword-based construction and deconstruction) and new goodie-democratization
opportunities (e.g., accessors for arbitrary classes) that can come later.

More generally, in the last 10-15 years, Java developers have started to
appreciate the value of immutability for creating safe, reliable code that is
easy to reason about, and the language is starting to reflect that appreciation
-- but at every turn, the language and runtime still make us pay a tax for using
immutable objects.  Functional transformation allows immutable objects to pay
less usability tax (and, with inline classes, less performance tax as well.)

## Withers

Records and inline classes are two new forms of shallowly-immutable classes in
Java.  This makes it even more obvious that "mutating" (applying a functional
transformation to) an immutable object is currently too painful.  (Obviously
records cannot be mutated -- but the next best thing is to create a new record
that has a known delta from an existing record.)  if our `Point` record wants to
expose a way to "set" the `x` and `y` components, it has to write `withX` and
`withY` methods:

```
record Point(int x, int y) {
    Point withX(int newX) { return new Point(newX, y); }
    Point withY(int newY) { return new Point(x, newY); }
}
```

This is doable, and has the advantage of working with the language we have
today, but has two obvious drawbacks.  Developers are clearly thrilled to be
free of the usual state-related boilerplate, and this would form a new category
of boilerplate that does not get automated away (two steps forward, one step
back),  and when records have many state components, writing these "withers"
gets more tedious and error-prone.  For records, at least, the language has
enough information to automate this away, so it would be especially a shame to
ask developers to do it by hand.

In fact, "withers" would be even worse than getters and setters, since while a
class might have _O(n)_ getters, it could conceivably have _O(2^n)_ withers.
Worse, as the number of components grows, the bodies of these "wither" accessors
gets more error-prone.  Let's nip this one in the bud before it becomes a
"pattern" (or worse, a "best practice".)

This problem is not unique to records or inline classes; existing value-based
classes (such as `LocalDateTime`) have had to expose wither methods as well.
But, if the language is going to encourage us to write immutable classes, it
should also help us with this problem.

#### Digression: learning from C#

Our friends in the C# world have taken two swings at this problem already.
Their first solution builds on the fact that they already allow parameters to
have default values, and later added the ability for default values to refer to
`this`.  This means that you can write an ordinary library method called `with`:

```
class Point {
    int x;
    int y;

    Point with(int x = this.x, int y = this.y) {
       return new Point(x, y);
    }
}
```

This is an improvement in that it allows you to write _one_ method to handle the
_2^n_ possible combinations, as a pure API consideration, and the client can
specify only the parameters they want to change:

```
p = p.with(x : 3);
```

But, clearly that wasn't enough for C# developers, because recently (C# 9), they
have also introduced a `with` expression into the language:

```
p with { x = 3; }
```

The block on the right is extremely limited; it is a set of property
assignments.  (C# also recently introduced "init-only" properties (effectively,
named constructor arguments), so the above causes a new `Point` to be
instantiated, where the property assignments written in the block override those
of the left operand.)

#### With expressions in Java

The C# approach was sensible for them because they could build on features they
already had (default parameters, properties), but just copying that approach
would drag with it a lot of baggage.  But we already have, in Java, the building
blocks we need (almost) to do it differently, and possibly more richly:
constructors and deconstructors.

A _reconstruction expression_ takes an operand whose static type is `T` and
a block, where the block expresses a functional transformation on the state
of the operand, and yields a new instance of type `T`:

```
Point p;
Point pp = p with { x = 3; }
```

Here, `pp` will have whatever `y` value `p` had, and `x=3`.  The block  can be
an arbitrary sequence of Java statements (assignment, ,loops, method calls,
etc.), with some restrictions.  Ideally, we can define reconstruction for
arbitrary classes, not just records, but we'll start with records.  A record
always has a canonical constructor and deconstruction pattern.  That means
we can interpret the above `with` expression as:

 - Declare a new block, with fresh mutable locals whose names and types are
   those of the record components;
 - Deconstruct the target with the canonical deconstructor, and assign the
   results to the variables describe above;
 - Execute the block from the `with` in that scope, that can mutate those locals
   if the right names are used;
 - Read the final value of the locals, and invoke the canonical constructor with
   them;
 - The value of the `with` expression is the resulting instance.

So, an expression such as

```
p with { x = 3; }
```

can be interpreted as something like:

```
{                             // new scope
    Point(var x, var y) = p;  // deconstruct the LHS with canonical ctor
    { x = 3; }                // execute the RHS of in that scope
    yield new Point(x, y);    // reconstruct with new values
}
```

We can think of the block on the RHS of the `with` expression as a functional
transformation on the state of the record.  As such, it is reasonable to impose
some restrictions on it.  For reasons that will become clear later, we will
prohibit writing to any variables other than the locals corresponding to the
extracted components, and locals declared inside the block.  The block is free
to use any feature of the language (such as loops and conditional), not just
assignment -- it is not a "DSL", it's a block of Java code that expresses a
transformation on the state of the record.  

Clients can use `with` expressions, but classes may also want to use them in
their implementation as well.  For example:

```
record Complex(double real, double im) {
    Complex conjugate() { return this with { im = -im; } }
    Complex realOnly() { return this with { im = 0; } }
    Complex imOnly() { return this with { re = 0; } }
}
```

Clients can of course perform these same manipulations, but the author may deem
these operations to be important enough in this domain that it makes sense to
expose as methods.

Note too that if the canonical constructor checks invariants, then a `with`
expression will check them too.  For example:

```
record Rational(int num, int denom) {
    Rational {
        if (denom == 0)
            throw new IllegalArgumentException("denom must not be zero");
    }
}
```

If we have a rational, and say

```
r with { denom = 0; }
```

we will get the same exception, since what this will do is unpack the numerator
and denominator into mutable locals, mutate the denominator to zero, and then
feed them back to the canonical constructor -- who will throw.  

## Extrapolating from records

There is very little we would have to do to start supporting `with` expressions
on records; all the building blocks are in place.  But before we go there, we
want to make sure we have a path to extending this to any class that wants to
participate in this protocol.  

What made things work with records is that we had a canonical constructor and
deconstruction pattern, which were known to have stable signatures, names, and
which match each other.  We can interpret this as an _external state
description_ which we know how to map to and from the internal state description
(which in the case of records is the same as the external description, possibly
with some validation.)  Ordinary classes will not have all that, since they may
have a different representation, but it may be possible to add back enough
metadata to derive these behaviors.

Functional transformation depends on deconstruction, so we need a way to declare
a deconstructor for an arbitrary class, and this is on the plan.  For purposes
of this document, we will denote a deconstruction pattern as:

```
public deconstructor Point(int x, int y) { ... }
```

But, we're not there yet.  A class may have multiple constructors and
deconstructors; we have to select a matched pair that extracts all the relevant
state, and then is willing to repack the modified state into a new object.  We
need a way of selecting the correct constructor and deconstructor.  

Obviously, the traditional way of selecting overloads won't work here, but
there's another way we could get to -- by parameter name.  Let's assume for a
moment that parameter names were significant (though we know they are not.)  
We can interpret

```
p with { x = 3; }
```

as an overload selection problem: "Find me a (maximal) constructor that takes an
`x`.  Then, take all the names and types in the argument list for that
constructor, and find me a (minimal) deconstructor that extracts them all." But
how do we even find the initial set `{ x }`?  This is where our restriction
(which is a reasonable one anyway) comes in; we can look at the assignment
targets in the block that are not assignments to locals declared in the block,
and they must be assignments to "properties" of the object being reconstructed.

#### Making names significant

Making the parameter and binding names of constructors and deconstructors
significant was needed for this feature (and, as it turns out, for a number of
other desirable features too.)  We surely can't just say "from today on, they
are significant", but we can give people an opt-in to significant names.  For purposes
of exposition within this document, we'll
use the modifier `__byname` on a constructor or deconstructor to indicate that
names are significant.

Since we're anticipating using these names at the client site, we also need to
define how `__byname` constructors and deconstructors can be overloaded, and how
to do overload selection.  As a strawman, we'll take the simplest thing that
could work: only one `__byname` constructor or deconstructor.  Later we'll see
how to extend this.   If there is only one `__byname` constructor and
deconstructor, clearly given:

```
p with { y = 3 }
```

we can interpret this the same way as for records:

 - Deconstruct the target with the `__byname` deconstructor found in the class
   which is the static type of the target;
 - Bind the resulting components to fresh mutable locals;
 - Execute the RHS block in this context, with the restriction that it may only
   mutate variables whose names appear in the constructor;
 - Invoke the constructor with the final values of the component variables.

In this way, any class with a `__byname` constructor and deconstructor can
participate in the reconstruction protocol.

#### Refining overloading

The "only one constructor and deconstructor" rule is overly restrictive,  so
let's extend this to be more useful.  The existing rules about overloading and
selecting members according to their parameter types and positions do not need
to change, but we would need new rules for overloading `__byname` members (both
against each other, and against positional declarations).

Each `__byname` constructor / deconstructor can be invoked positionally, so  it
must follow the existing rules for overloading and overload selection.  A set of
`__byname` members is valid if and only if for each two such members, their name
sets are either disjoint or one is a proper subset of the other and the types
corresponding to the common names are the same.  What this does is organize
`__byname` members into disjoint telescoping chains.

For our

```
p with { x = 3; }
```

example, this means we select the _maximal_ (top member of the chain)
constructor that has an `x`, and then find the _minimal_ deconstructor that
contains all the names in that selected constructor.  This reproduces the object
with maximal fidelity and minimal extraction cost.

The overloading rules may seem restrictive at first, but they are not as bad as
they might initially seem.  Many current constructor overloads -- especially for
classes that are suited to functional transformation -- are of the "telescoping"
variety, where the overloads form a linear chain of _x-is-shorthand-for-y_ and
the simpler ones exist merely as positional conveniences to provide defaults
(`new HashMap()` delegates to `new HashMap(initCapacity)` which delegates to
`new HashMap(initCapacity, loadFactor)`.)

This scheme was selected in part because some classes may have a wholly
different internal representation than their external API, but want to support
functional transformation using the external API for clients and the internal
representation for the implementation.  By having a private constructor /
deconstructor pair for internal use in terms of the internal representation, and
a public pair for client use in terms of the external API, both clients and
implementation can take advantage of functional transformation in terms that
make sense to them.  And to the extent there is overlap between the public and
private pair, names in the private pair can always be alpha-renamed.

#### Teasers

The `__byname` modifier is part of the underpinnings for many other useful
features, such as keyword-based invocation (`new Foo(x: 3)`), with or without
default parameters, keyword-based deconstruction, and automatic generation of
read accessors for ordinary classes (you can think of a deconstructor as a
multi-getter, and derive getters from that.)  We'll talk about these some other
time.

#### Factories

Functional transformation depends on having a `__byname` constructor, but many
developers prefer to expose factories rather than constructors.  Can we nominate
a `__byname` factory to take this role instead?

The first problem is that factory methods are an API design pattern, not a
language feature.  We can fix that with some simple mostly-sugar; we can
interpret:

```
class Box<T> {
    public factory of(T t) { ... }
}
```

to mean

```
class Box<T> {
    public static<T> Box<T> of(T t) { ... }
}
```

This is a trivial transformation, and as a bonus, gives us permission to include
factory methods in the "Constructors" section of Javadoc.  Then we can allow
`__byname` factories, and extend the overload rules to consider all the
`__byname` constructors and factories as a group.  To support functional
transformation, we can extend the rule to allow selecting either a `__byname`
constructor or factory.

**Bonus round: interfaces?**   We already allow static factories in interfaces,
and there's no reason why interfaces cannot have deconstructors (if the
interface provides an API that lets the state components be extracted, which
some do).  If we also allowed deconstructors in interfaces we could even do
with'ing on interfaces too:

```
interface Pointy {
    public __byname factory of(int x, int y) { return new PointImpl(x, y); }
    public __byname __deconstructor Pointy(int x, int y) { __match (x(), y()); }
    public int x();
    public int y();
}
```

Then clients would be able to say:

```
Pointy p = Pointy.of(1, 2);
Pointy flipped = p with { x = -x; y = -y; };
```

(Or not.)
