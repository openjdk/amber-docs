# Deconstruction patterns for records and classes
#### Brian Goetz, Aug 2020

This document describes a possible approach for a future phase of _pattern
matching_ -- adding deconstruction patterns for records and classes.  This
builds on the work of [JEP 375](https://openjdk.java.net/jeps/375), and would follow [type patterns in
switch](type-patterns-in-switch.html).  _This is an exploratory document only
and does not constitute a plan for any specific feature in any specific version
of the Java Language._

[Records][records] are transparent carriers for their data.  By _transparent_,
we mean that they will readily give up their data, in the same form as their
state description, when asked.  The simplest way that they expose their data is
through accessor methods; for each record component `x`, there is a
corresponding accessor method `x()`.  (These methods can be overridden to
perform defensive copies when needed, but such overrides are constrained by the
specification of `Record::equals` to conform to the the constraint that
deconstructing a record and creating a new record from the results must be
`equals` to the  original record.)

Because records give up their state when asked, they already work, to some
degree, with [pattern matching][patterns0], since we can pattern match on a
record type and then call the accessors to extract the state:

```
if (shape instanceof Circle c) {
    double radius = c.radius();
    double circumference = 2 * Math.PI * c.radius();
    double area = Math.PI * radius * radius;
    ...
}
```

But, we can do better.  Records are _nominal tuples_, and languages with tuples
generally support deconstruction on tuples.  The analogue of this for records in
Java is a _deconstruction pattern_:

```
if (shape instanceof Circle(var c, var r)) {
    double circumference = 2 * Math.PI * r;
    double area = Math.PI * r * r;
}
```

Here, we perform the type test, extract the components, and bind the components
to variables in one go.  A deconstruction pattern for a record is equivalent to
testing it with a type pattern, and if that succeeds, invoking the accessors for
each component and binding the results to fresh variables.  Just as we are able
to infer the behavior for constructors, accessors, and `Object` methods for
records, we can do the same for deconstruction patterns.

#### Deconstruction patterns

[JEP 375][patterns0] gave us one kind of pattern: type patterns.  A type pattern
is denoted by a type name and a variable identifier: `String s` or `List<String>
list`.  The semantics of a type pattern is that of an `instanceof` test for the
type, plus, if the test succeeds, casting to that type.  For a type pattern `T
t` to be applicable to a target of type `U` (`u instanceof T t`), `U` must be
cast-convertible to `T` without unchecked warnings.  (This is why we can use
generics safely in type patterns.)  

A simple deconstruction pattern takes the form of `D(T t, ...) [d]`, where `D`
is the name of a type with a deconstruction pattern, `T t` is a  type pattern,
and `d` is an optional binding variable (of type `D`) to receive the result of
casting the target to `D`.

A deconstruction pattern `D(T t)` is applicable to any target type to which the
type pattern `D d` would be applicable to -- any type that is cast-convertible
to `D` without an unchecked warning.  Further, the nested pattern `T t` must be
applicable to the type of the corresponding binding variable of the
deconstruction pattern `D`.  A deconstruction pattern never matches `null`.

Records automatically acquire a canonical deconstruction pattern whose binding
variables correspond to the components of the record in the canonical order,
and whose implementation binds these to the return value of the corresponding
accessor.

#### Nested patterns

The above description -- where we describe what is between the parentheses in a
deconstruction pattern -- is a simplification.  In reality, what is between the
parenthesis is an ordered list of patterns, which will be recursively matched
against the extracted components.  If our deconstruction pattern is
`Circle(Point center, double radius)`, then `Point center` and `double radius`
are just ordinary patterns.  But we can nest any pattern there, as long as
it is applicable to the type of the corresponding binding.  

Matching a nested pattern `target matches P(Q)` is equivalent to the compound
match

    target matches P(T alpha) && alpha matches Q

where `T` is the type of the binding component of `P`.  We say "matches" here
rather than `instanceof` to highlight that we want to  use the intrinsic
matching semantics of the pattern, without consideration for the null-handling
behavior of language constructs such as `instanceof` or `switch`.

We can nest deconstruction patterns inside deconstruction patterns:

```
if (shape instanceof Circle(Point(var x, var y), double radius)) { ... }
```

We can also use type inference to elide the manifest type name, and the type
will be inferred based on the type of the corresponding binding component:

```
if (shape instanceof Circle(var center, var radius)) { ... }
```

A nested deconstruction pattern `D(P, Q)` is _total_ on a target type `T` if the
type pattern `D d` is total on `T`, and the patterns `P` and `Q` are total on
the types of `D`'s binding components.

#### Match statements

Some patterns (such as `var x`) are total on their target type (will always
match); others are partial (may fail).  Further, for some patterns, the
applicability criteria can be evaluated statically by the compiler (the compiler
knows that the type pattern `Object o` is total on `String`), whereas for others
(such as `Optional.of(var x)`), whether the pattern matches can only be
determined as runtime.  Total patterns can be given special treatment by the
language, since their applicability can be statically analyzed.

If a pattern `P` is total on its target type, we don't have to apply it with a
conditional construct (`instanceof` or `switch`); we can apply it with an
unconditional construct.  We propose to introduce a _deconstruction statement_
which is modeled on local variable declarations with initializers:

```
Foo f = bar.getFoo();
```

Here, `Foo f` is a local variable declaration, and `= bar.getFoo()` is an
initializer of type `Foo`, the compiler type-checks that the initializer has a
compatible type with the declaration, and the variable `f` is in scope for the
remainder of the block immediately containing this declaration.  But, note that
`Foo f` is also a pattern, and we could also interpret the above as a pattern
match: since we know that `Foo f` will always match an expression of type `Foo`,
the above has equivalent semantics to:

```
if (!(bar.getFoo() instanceof Foo f))
    throw new ImpossibleException();
// f is in scope here, due to flow scoping!
```

So we can generalize the declaration of local variables with initializers as
being a match to a total pattern.  Since deconstruction patterns (with total
nested patterns) are total on their corresponding type, we could deconstruct
a `Circle` as:

```
void foo(Circle c) {
    Circle(var center, var radius) = c;
}
```

The deconstruction pattern here is total on circles, so the above statement has
the effect of deconstructing the circle and binding `center` and `radius` to
local variables that are in scope for the remainder of the method body.

A match statement with a non-nullable pattern throws `NullPointerException` if
the match target is null.  Deconstruction patterns are not nullable -- because
they intrinsically invoke a member with the target as a receiver -- so the above
would throw `NullPointerException` (as would the equivalent code that accesses
the state of the `Circle` directly.)

## Deconstructors for classes

We can surely derive deconstruction patterns for records, but it would be nice
if we could do deconstruction on arbitrary classes as well.  Of course, classes
would have to declare something about how to deconstruct them, since we cannot
derive this automatically the way we do for records.

A deconstruction pattern can be thought of as the dual of a constructor; a
constructor takes N state components and aggregates them into an object, and a
deconstruction pattern takes an object and decomposes it into N state
components.  Constructors are instance members but are not inherited, whose
names are constrained to be the name of the class; the same is true for
deconstructors.

```
class Point {
    int x;
    int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public deconstructor Point(int x, int y) {
        x = this.x;
        y = this.y;
    }
}
```

The arguments of a constructor are parameters; the "arguments" of a
deconstructor are declarations of binding variables (which are treated as blank
finals in scope in the body.)  Because a deconstruction pattern is total, it
_must_ assign all of the bindings, so we require that they all be DA in all exit
paths.  The body is a deconstructor is effectively the body of a `void` method,
so may use `return` if needed (like constructors).  The syntax is chosen to
highlight the duality between constructors and deconstructors.

#### Overloading

Like constructors, deconstructors can be overloaded.  However, overload
resolution is slightly different than for methods and constructors.  (For
example, deconstructor parameters are output parameters rather than inputs, and
so the directions of relations like subtyping are reversed in some of the
applicability rules.)  And at the use site, what is specified is not an
expression, but a pattern, so the way we derive constraints about which
overloads are applicable is different as well.

(Details TBD, but given the expected prevalance of `var` patterns at the use
site, we'd expect that in practice, we'll mostly only see overloads on arity.)

#### Varargs

Varargs patterns seem quite useful.  As a motivating example, suppose we have a
pattern for a regular expression, whose binding is a varargs containing the
matched groups.  We may well want to further match on the elements individually
with separate patterns; suppose a regex extracts an integer and a string, we may
well want to  match the regex against a target, and then have separate nested
patterns to  further refine these two extracted groups.  

This is a substantial separate investigation; for now, details TBD, and we will
likely hold off supporting the declaration of varargs patterns until we have a
suitable semantics for matching varargs patterns.

#### Composition

Constructors compose with other constructors; constructors may delegate to
superclass constructors, and may further invoke constructors to initialize the
fields of the object.  Given the duality between constructors and
deconstructors, not only do we want to support these modes of composition, but
we'd like the composed form of each to look similar (enough); the more that the
denotation of a constructor and deconstructor diverge, the more likely bugs will
creep in.  We can lean on pattern assignment as our analogue of constructor
invocation.

As a starting point:

```
class A {
    int a;

    public A(int a) { this.a = a; }
    public deconstructor A(int a) { a = this.a; }
}

class B extends A { // extension
    int b;

    public B(int a, int b) {
        super(a);
        this.b = b;
    }

    public deconstructor B(int a, int b) {
        super(var aa) = this;
        a = aa;
        b = this.b;
    }
}

class WithB { // composition
    B b;

    public WithB(int a, int b) {
        this.b = new B(a, b);
    }

    public deconstructor WithB(int a, int b) {
        B(var aa, var bb) = this;
        a = aa;
        b = bb;
    }
}
```

These examples illustrate composition with both extension and delegation.  In
both cases, the constructor and deconstructor can delegate to that of another
class (whether a superclass or not) to do part of its work.  This is good.

No one could read the above examples, though, without immediately disliking the
fact that we had to introduce "garbage" variables `aa` and `bb` just to  receive
the downstream bindings, and then immediately assign them to the current
bindings.  (Secondarily, it might be pleasant and less error-prone to be able to
omit the `= this` when invoking the superclass deconstructor.)

Without devolving into the bikeshed, what we're missing here to make composition
smooth is some way to use a blank final as a pattern in a pattern assignment.
One obvious choice is to just use the variable name directly:

```
B(a, b) = this;
```

but this is undesirable as (unlike with all other patterns) there is no visual
cue that `a` is not just an expression and this is not just a method call.  (We
had the same concern with constant patterns, and the current thinking is to just
not have them, or if we must, to give them a more obviously-a-pattern syntax.)
So some syntactic decoration of `a` to make it clear that is the assignment
target, rather than a value source, (e.g., `a=`, `bind a`, etc) may be in order.

The other form of composition in constructors is delegating to `this`; this is
analogous to delegating to `super` (and we may want similarly to default to `=
this`).

#### Translation

A deconstructor cannot be translated in the obvious way, because of its multiple
bindings.  But, we can lean on records (and eventually, inline records) to
smooth this out.  A deconstruction pattern:

```
deconstructor Foo(int x, int y) { x = blah; y = blarg; }
```

can be translated as

```
[inline] record Foo$abc(int x, int y) { }
Foo$abc <deconstruct>() { return new Foo$abc(blah, blarg); }
```

However, we have to exercise care with the name mangling of the record, since
this descriptor will appear in the classfiles of clients -- this name must be
stable with respect to source- and binary-compatible changes to the declaration
of the deconstructor (or the rest of the class.)  

Our strategy here is to compute the descriptor for the deconstructor as if it
were a method (yielding a method descriptor containing the erasure of the
binding variables), and then encode that descriptor using the [symbolic
freedom][symfree] encoding -- which was designed for exactly this purpose -- as
our `abc` disambiguator.  Valid overloads will have distinct, stable
disambiguators.

The use of inline classes (when we have them) reduces the cost of this
translation mechanism; we can compatibly switch from records to inline records
later as long as we generate a reference bridge for binary compatibility with
old clients.

On the client side, we can factor a deconstruction pattern into an
instanceof test and a conditional invocation of the desugared
`<deconstruct>` method, and then invoking the corresponding record
accessor for each binding variable needed by client code.

Nested patterns `P(Q)` at the client can be unrolled into `P(var
alpha) && alpha matches Q` at the use site; in switches, the secondary
component can be lowered to a guard.

#### Relationship to accessors

There is a notable relationship between deconstruction patterns and accessors;
we can think of deconstructors as "multi-accessors" and implement accessors in
terms of them, or we can implement a deconstructor in terms of accessors.  

Without diving too deeply into this topic, just as we can derive read accessors
for record components, we would like to be able to, eventually, derive read
accessors from a suitable deconstruction pattern for arbitrary classes.  This
feature is outside the scope of this document.  

#### Records

Going forward, we can compile records to have a canonical deconstruction pattern
whose implementation merely delegates to the component accessors.  In fact,
because it is important that the accessors and deconstruction pattern agree on
how to extract a given component, this will likely be the _only_ way to
influence the deconstruction of records -- override the accessor.  You can
declare additional (overloaded) deconstructors.

#### Switch miscellany

With the advent of deconstruction and nested patterns, some additional
limitations of `switch` will be exposed.  We would like it to be a
universally valid refactoring to refactor a set of nested
deconstruction patterns:

```
case P(Q): A
case P(R): B
case P(S): C
```

to

```
case P(var alpha):
    switch (alpha) {
        case Q: A
        case R: B
        case S: C
    }
```

However, there are still several primitive types (`float`, `double`,
and `boolean`) that `switch` does not support as target types; these
would need to be added in order for this to not become a refactoring
impediment.  Adding these to `switch` is easy enough, and most of the
type-specific work can be done in an `indy` bootstrap.



[records]: https://openjdk.java.net/jeps/359
[patterns0]: https://openjdk.java.net/jeps/375
[symfree]: https://blogs.oracle.com/jrose/symbolic-freedom-in-the-vm
