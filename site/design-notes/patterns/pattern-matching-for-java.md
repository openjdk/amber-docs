# Pattern Matching for Java

#### Gavin Bierman and Brian Goetz {.author}
#### September 2018 {.date}

This document explores a possible direction for supporting _pattern
matching_ in the Java Language.  _This is an exploratory document only
and does not constitute a plan for any specific feature in any
specific version of the Java Language._  This document also may
reference other features under exploration; this is purely for
illustrative purposes, and does not constitute any sort of plan or
committment to deliver any of these features.

### Pattern matching documents

 - [Pattern Matching For Java](pattern-matching-for-java) (this document)
   --- Overview of pattern matching concepts, and how they might be surfaced in Java.

 - [Pattern Matching For Java -- Semantics](pattern-match-semantics) --- More
   detailed notes on type checking, matching, and scoping of patterns and
   binding variables.  

 - [Extending Switch for Patterns](extending-switch-for-patterns) --- An early
   exploration of the issues surrounding extending pattern matching to the
   `switch` statement.

 - [Type Patterns in Switch](type-patterns-in-switch) --- A more up-to-date
   treatment of extending pattern matching to `switch` statements, including
   treatment of nullity and totality.  

 - [Pattern Matching in the Java Object model](pattern-match-object-model)
   --- Explores how patterns fit into the Java object model, how they fill a hole we
   may not have realized existed, and how they might affect API design going
   forward.


## Motivation

Nearly every program includes some sort of logic that combines testing
if an expression has a certain type or structure, and then
conditionally extracting components of its state for further
processing.  For example, all Java programmers are familiar with the
instanceof-and-cast idiom:

```{.java}
if (obj instanceof Integer) {
    int intValue = ((Integer) obj).intValue();
    // use intValue
}
```

There are three things going on here: a test (is `x` an `Integer`), a
conversion (casting `obj` to `Integer`), and a destructuring
(extracting the `intValue` component from the `Integer`).  This
pattern is straightforward and understood by all Java programmers, but
is suboptimal for several reasons.  It is tedious; doing both the type
test and cast should be unnecessary (what else would you do after an
`instanceof` test?), and the accidental boilerplate of casting and
destructuring obfuscates the more significant logic that follows.  But
most importantly, the needless repetition of the type name provides
opportunities for errors to creep unnoticed into programs.

This problem gets worse when we want to test against multiple possible
target types.  We sometimes repeatedly test the same target with a
chain of `if...else` tests:

```{.java}
String formatted = "unknown";
if (obj instanceof Integer) {
    int i = (Integer) obj;
    formatted = String.format("int %d", i);
}
else if (obj instanceof Byte) {
    byte b = (Byte) obj;
    formatted = String.format("byte %d", b);
}
else if (obj instanceof Long) {
    long l = (Long) obj;
    formatted = String.format("long %d", l);
}
else if (obj instanceof Double) {
    double d = (Double) obj;
    formatted = String.format(“double %f", d);
}
else if (obj instanceof String) {
    String s = (String) obj;
    formatted = String.format("String %s", s);
}
...
```

The above code is familiar, but has many undesirable properties.  As
already mentioned, repeating the cast in each arm is annoying and
unnecessary.  The business logic can too easily get lost in the
boilerplate.  But most importantly, the approach allows coding errors
to remain hidden -- because we've used an overly-general control
construct.  The intent of the above code is to assign something to
`formatted` in each arm of the `if...else` chain.  But, there is
nothing here that enables the compiler to verify this actually
happens.  If some block -- perhaps one that is executed rarely in
practice -- forgets to assign to `formatted`, we have a bug.  (Leaving
`formatted` as a blank local or blank final would at least enlist the
"definite assignment" analysis in this effort, but this is not always
done.)  Finally, the above code is less optimizable; absent compiler
heroics, the chain with _n_ branches will have _O(n)_ time complexity,
even though the underlying problem is often _O(1)_.

There have been plenty of ad-hoc suggestions for ameliorating these
problems, such as _flow typing_ (where the type of `obj` after an
`instanceof Integer` test is refined in any control path dominated by
the test, so that the cast is unneeded), or _type switch_ (where the
case labels of a switch statement can specify types as well as
constants).  But these are mostly band-aids; there's a better
alternative that subsumes these (and other cases.)

### Patterns

Rather than reach for ad-hoc solutions to the test-and-extract
problem, we believe it is time for Java to embrace _pattern matching_.
Pattern matching is a technique that has been adapted to many
different styles of programming languages going back to the 1960s,
including text-oriented languages like SNOBOL4 and AWK, functional
languages like Haskell and ML, and more recently extended to
object-oriented languages like Scala (and most recently, C#).

A _pattern_ is a combination of a _match predicate_ that determines if
the pattern matches a target, along with a set of _pattern variables_
that are conditionally extracted if the pattern matches the target.
Many language constructs that test an input, such as `instanceof` and
`switch`, can be generalized to accept patterns that are matched
against the input.

One form of pattern is a _type pattern_, which consists of a type name
and the name of a variable to bind the result to, illustrated below in
a generalization of `instanceof`:

```{.java}
if (x instanceof Integer i) {
    // can use i here, of type Integer
}
```

Here, `x` is being matched against the type pattern `Integer i`.
First `x` is tested to see if it is an instance of `Integer`.  If so,
it is cast to `Integer`, and the result assigned to `i`.  The name `i`
is not a reuse of an existing variable, but instead a declaration of a
pattern variable.  (The resemblance to a variable declaration is not
accidental.)

Using patterns with `instanceof` simplifies commonly messy operations,
such as implementation of `equals()` methods.  For a class `Point`, we
might implement `equals()` as follows:

```{.java}
public boolean equals(Object o) {
    if (!(o instanceof Point))
        return false;
    Point other = (Point) o;
    return x == other.x
        && y == other.y;
}
```

Using a pattern match instead, we can combine this into a single
expression, eliminating the repetition and simplifying the control
flow:

```{.java}
public boolean equals(Object o) {
    return (o instanceof Point other)
        && x == other.x
        && y == other.y;
}
```

Similarly, we could simplify the `if..else` chain above with type
patterns, eliminating the casting and binding boilerplate:

```{.java}
String formatted = "unknown";
if (obj instanceof Integer i) {
    formatted = String.format("int %d", i);
}
else if (obj instanceof Byte b) {
    formatted = String.format("byte %d", b);
}
else if (obj instanceof Long l) {
    formatted = String.format("long %d", l);
}
else if (obj instanceof Double d) {
    formatted = String.format(“double %f", d);
}
else if (obj instanceof String s) {
    formatted = String.format("String %s", s);
}
...
```

This is already a big improvement -- the business logic pops out much
more clearly -- but we can do better.

### Patterns in multi-way conditionals

The chain of `if...else` still has some redundancy we'd like to
squeeze out, both because it gives bugs a place to hide, and makes
readers work harder to understand what the code does.  Specifically,
the `if (obj instanceof ...)` part is repeated.  We'd like to say
"choose the block which best describes the target object", and be
guaranteed that exactly one of them will execute.

We already have a mechanism for a multi-armed equality test in the
language -- `switch`.  But `switch` is currently very limited.  You
can only switch on a small set of types -- numbers, strings, and enums
-- and you can only test for exact equality against constants.  But
these limitations are mostly accidents of history; the `switch`
statement is a perfect "match" for pattern matching.  Just as the type
operand of `instanceof` can be generalized to patterns, so can `case`
labels.  Using a switch expression with pattern cases, we can express
our formatting example as:

```{.java}
String formatted =
    switch (obj) {
        case Integer i -> String.format("int %d", i);
        case Byte b    -> String.format("byte %d", b);
        case Long l    -> String.format("long %d", l);
        case Double d  -> String.format("double %f", d);
        case String s  -> String.format("String %s, s);
        default        -> String.format("Object %s", obj);
    };
...
```

Now, the intent of the code is far clearer, because we're using the
right control construct -- we're saying "the expression `obj` matches
at most one of the following conditions, figure it out and evaluate
the corresponding expression".  This is more concise, but more
importantly it is also safer -- we've enlisted the language's aid in
ensuring that `formatted` is always assigned, and the compiler can
verify that the supplied cases are exhaustive.  As a bonus, it is more
optimizable too; in this case we are more likely to be able to do the
dispatch in _O(1)_ time.

### Constant patterns

We're already familiar with a kind of pattern, namely, the constant
case labels in today's `switch` statement.  Currently, case labels can
only be numeric, string, or enum constants; going forward, these
constant case labels are just _constant patterns_.  Matching a target
to a constant pattern means the obvious thing: test for equality
against the constant.  Previously, a constant case label could only be
used to match a target of the same type as the case label; going
forward, we can use constant pattern to combine type tests and
equality tests, allowing us to match `Object` against specific
constants.

## Operations on polymorphic data

The example above, where we are handed an `Object` and have to do
different things depending on its dynamic type, can be thought of as a
sort of "ad-hoc polymorphism."  There is no common supertype to appeal
to that would give us virtual dispatch or methods that we could use to
differentiate between the various subtypes, so we can resort only to
dynamic type tests to answer our question.

Often, we are able to arrange our classes into a hierarchy, in which
case we can use the type system to make answering questions like this
easier.  For example, consider this hierarchy for describing an
arithmetic expression:

```{.java}
interface Node { }

class IntNode implements Node {
    int value;
}

class NegNode implements Node {
    Node node;
}

class MulNode implements Node {
    Node left, right;
}

class AddNode implements Node {
    Node left, right;
}
```

An operation we might commonly perform on such a hierarchy is to
evaluate the expression; this is an ideal application for a virtual
method:

```{.java}
interface Node {
    int eval();
}

class IntNode implements Node {
    int value;

    int eval() { return value; }
}

class NegNode implements Node {
    Node node;

    int eval() { return -node.eval(); }
}

class MulNode implements Node {
    Node left, right;

    int eval() { return left.eval() * right.eval(); }
}

class AddNode implements Node {
    Node left, right;

    int eval() { return left.eval() + right.eval(); }
}
```

In a bigger program, we might define many operations over a hierarchy.
Some, like `eval()`, are intrinsically sensible to the hierarchy, and
so we will likely implement them as virtual methods.  But some
operations are too ad-hoc (such as "does this expression contain any
intermediate nodes that evaluate to 42"); it would be silly to put
this into the hierarchy, as it would just pollute the API.

### The Visitor pattern

The standard trick for separately specifying a hierarchy from its
operations is the _visitor pattern_, which separate traversal of a
data structure from the definition of the data structure itself.  For
example, if the data structure is a tree that represents a design in a
CAD application, nearly every operation requires traversing at least
some part of the tree -- saving, printing, searching for text in
element labels, computing weight or cost, validating design rules,
etc.  While we might start out by representing each of these
operations as a virtual method on the root type, this quickly becomes
unwieldy, and the visitor pattern enables us to decouple the code for
any given traversal (say, searching for text in element labels) from
the code that defines the data structure itself, which is often a
superior way of organizing the code.

But, the visitor pattern has costs.  To use it, a hierarchy has to be
designed for visitation.  This involves giving every node an
`accept(Visitor)` method, and defining a `Visitor` interface:

```{.java}
interface NodeVisitor<T> {
    T visit(IntNode node);
    T visit(NegNode node);
    T visit(MulNode node);
    T visit(AddNode node);
}
```

If we wanted to define our evaluation method as a visitor over `Node`,
we would do so like this:

```{.java}
class EvalVisitor implements NodeVisitor<Integer> {
    Integer visit(IntNode node) {
        return node.value;
    }

    Integer visit(NegNode node) {
        return -node.accept(this);
    }

    Integer visit(MulNode node) {
        return node.left.accept(this) * node.right.accept(this);
    }

    Integer visit(AddNode node) {
        return node.left.accept(this) + node.right.accept(this);
    }
}
```

For a simple hierarchy and a simple traversal, this isn't too bad.  We
suffer some constant code overhead for being visitor-ready (every node
class needs an `accept` method, and a single visitor interface), and
thereafter we write one visitor per traversing operation.  (As an
added penality, we have to box primitives returned by visitors.)  But,
visitors rightly have a reputation for being verbose and rigid; as
visitors get more complicated, it is common to have multiple levels of
visitors involved in a single traversal.

Visitor has the right idea -- separating the operations over a
hierarchy from the hierarchy definition itself -- but the result is
less than ideal.  And, if the hierarchy was not designed for
visitation -- or worse, the elements you are traversing do not even
have a common supertype -- you are out of luck.  In the next section,
we'll see how pattern matching gets us the type-driven traversal that
Visitor offers, without its verbosity, intrusiveness, or restrictions.

## Deconstruction patterns

Many classes -- like our `Node` classes -- are just typed carriers
for structured data; typically, we construct an object from its state
with constructors or factories, and then we access this state with
accessor methods.  If we can access all the state components we pass
into the constructor, we can think of construction as being
reversible, and the reverse of construction is _deconstruction_.

A _deconstruction pattern_ is like a constructor in reverse; it
matches instances of the specified type, and then extracts the state
components.  If we construct a `Node` with

    new IntNode(5)

then we can deconstruct a node (assuming `IntNode` supports
deconstruction) with

    case IntNode(int n) -> ... n is in scope here ...

Here's how we'd implement our `eval()` method using deconstruction
patterns on the `Node` classes:

```{.java}
int eval(Node n) {
    return switch(n) {
        case IntNode(int i) -> i;
        case NegNode(Node n) -> -eval(n);
        case AddNode(Node left, Node right) -> eval(left) + eval(right);
        case MulNode(Node left, Node right) -> eval(left) * eval(right);
    };
}
```

The deconstruction pattern `AddNode(Node left, Node right)` first tests
`n` to see if it is an `AddNode`, and if so, casts it to `AddNode` and
extracts the left and right subtrees into pattern variables for
further evaluation.

This is obviously more compact than the Visitor solution, but more
importantly, it is also more direct.  We didn't even need the `Node`
types to have visitor support -- or even for there to be a common
nontrivial supertype.  All we needed was for the `Node` types to be
sufficiently transparent that we could take them apart using
deconstruction patterns.

### Sidebar: Data-driven polymorphism with patterns

The promise of the Visitor pattern is that operations on stable
hierarchies can be specified separately from the hierarchy.  But, this
comes at a cost -- visitor-based code is bulky, easy to get wrong,
annoying to write, and annoying to read.  Pattern matching allows you
to achieve the same result without the machinery of Visitors
interposing themselves, resulting in cleaner, simpler, more
transparent, and more flexible code.  And, pattern matching doesn't
even have the "stable hierarchy" requirement -- or indeed, any
hierarchy requirement -- that Visitor does.

Supporting ad-hoc polymorphism with pattern matching doesn't mean that
inheritance hierarchies and virtual methods are wrong -- it's just
that this is not the only useful way to attack a problem.  As we saw
with the `eval()` method, sometimes an operation is an ideal candidate
for including in the hierarchy.  But sometimes this isn't the right
choice, or even possible -- such as when an endpoint listens for a
variety of messages, and not all message types have a common supertype
(or even come from the same library.)  In these cases, pattern
matching offers clean and simple data-driven polymorphism.

It is sometimes said that many "design patterns" are workarounds for
features missing from the language.  While this claim may be too
facile, it is reasonably accurate in the case of Visitor -- if your
language has sufficiently powerful pattern matching, the Visitor
pattern is almost completely unnecessary.

## Composing patterns

Deconstruction patterns are deceptively powerful.  When we matched
against `AddNode(Node x, Node y)` in the previous example, it may have
looked like `Node x` and `Node y` are simply declarations of pattern
variables.  But, in fact, they are patterns themselves!

Assume that `AddNode` has a constructor that takes `Node` values for
the left and right subtrees, and a deconstructor that yields the left
and right subtrees as `Node`s.  The pattern `AddNode(P, Q)`, where `P`
and `Q` are patterns, matches a target if:

 - the target is an `AddNode`;
 - the `left` node of that `AddNode` matches `P`;
 - the `right` node of that `AddNode` matches `Q`.

Because `P` and `Q` are patterns, they may have their own pattern
variables; if the whole pattern matches, any binding variables in the
subpatterns are also bound.  So in:

    case AddNode(Node left, Node right) -> ...

the nested patterns `Node left` and `Node right` are just the type
patterns we've already seen (which happen to be guaranteed to match in
this case, based on static type information.)  So the effect is that
we check if the target is an `AddNode`, and if so, immediately bind
`left` and `right` to the left and right sub-nodes.  This may sound
complicated, but the effect is simple: we can match against an
`AddNode` and bind its components in one go.

But we can go further: we can nest other patterns inside a
deconstruction pattern as well, either to further constrain what is
matched or further destructure the result, as we'll see below.

### Exhaustiveness

In the expression form of `switch`, we evaluate exactly one arm of the
switch, which becomes the value of the `switch` expression itself.
This means that there must be at least one arm that applies to any
input -- otherwise the value of the `switch` expression might be
undefined.  If the switch has a `default` arm, there's no problem.
For switches over `enum`, where all enum constants are handled, it is
often irritating to have to write a `default` clause that we expect
will never be taken; worse, if we write this default clause, we lose
the ability to have the compiler verify that we have exhaustively
enumerated the cases.

Similarly, for many hierarchies where we might apply pattern matching,
such as our `Node` classes, we would be annoyed to have to include a
never-taken `default` arm when we know we've listed all the subtypes.
If we could express that the _only_ subtypes of `Node` are `IntNode`,
`AddNode`, `MulNode`, and `NegNode`, the compiler could use this
information to verify that a `switch` over these types is exhaustive.

There's an age-old technique we can apply here: hierarchy sealing.
Suppose we declare our `Node` type to be _sealed_; this means that
only the subtypes that are co-compiled with it (often from a single
compilation unit) can extend it:

    sealed interface Node { }

Sealing is a generalization of finality; where a final type has no
subtypes, a sealed type can have no subtypes beyond a fixed set of
co-declared subtypes.  The details of sealing will be discussed
separately.

### Patterns and type inference

Just as we sometimes want to let the compiler infer the type of a
local variable for us using `var` rather than spell the type out
explicitly, we may wish to do the same thing with type patterns.
While it might be useful to explicitly use type patterns in our
`AddNode` example (and the compiler can optimize them away based on
static type information, as we've seen), we could also use a nested
`var` pattern instead of the nested type patterns.  A `var` pattern
uses type inference to map to an equivalent type pattern (effectively
matching anything), and binds its target to a pattern variable of the
inferred type.  A pattern that matches anything may sound silly -- and
it is silly in itself -- but is very useful as a nested pattern.  We
can transform our `eval` method into:

```{.java}
int eval(Node n) {
    return switch(n) {
        case IntNode(var i) -> i;
        case NegNode(var n) -> -eval(n);
        case AddNode(var left, var right) -> eval(left) + eval(right);
        case MulNode(var left, var right) -> eval(left) * eval(right);
    };
}
```

This version is equivalent to the manifestly typed version -- as with
the use of `var` in local variable declarations, the compiler merely
infers the correct type for us.  As with local variables, the choice
of whether to use a nested type pattern or a nested `var` pattern is
solely one of whether the manifest type adds to or distracts from
readability and maintainability.

### Nesting constant patterns

Constant patterns are useful on their own (all existing `switch`
statements today use the equivalent of constant patterns), but they
are also useful as nested patterns.  For example, suppose we want to
optimize some special cases in our evaluator, such as "zero times
anything is zero".  In this case, we don't even need to evaluate the
other subtree.

If `IntNode(var i)` matches any `IntNode`, the nested pattern
`IntNode(0)` matches an `IntNode` that holds a zero value.  (The `0`
here is a constant pattern.)  In this case, we first test the target
to see if it is an `IntNode`, and if so, we extract its numeric
payload, and then further try to match that against the constant
pattern `0`.  We can go as deep as we like; we can match against a
`MulNode` whose left component is an `IntNode` containing zero, and we
could optimize away evaluation of both subtrees in this case:

```{.java}
int eval(Node n) {
    return switch(n) {
        case IntNode(var i) -> i;
        case NegNode(var n) -> -eval(n);
        case AddNode(var left, var right) -> eval(left) + eval(right);
        case MulNode(IntNode(0), var right),
             MulNode(var left, IntNode(0)) -> 0;
        case MulNode(var left, var right) -> eval(left) * eval(right);
    };
}
```

The first `MulNode` pattern is nested three deep, and it only matches
if all the levels match: first we test if the matchee is a `MulNode`,
then we test if the `MulNode`'s left component is an `IntNode`; then
we test whether that `IntNode`'s integer component is zero.  If our
target matches this complex pattern, we know we can simplify the
`MulNode` to zero.  Otherwise, we proceed to the next `case`, which
matches _any_ `MulNode`, which recursively evaluates the left and
right subnodes as before.

Expressing this with visitors would be circuitous and much harder to
read; even though a visitor will handle the outermost layer easily, we
would then have to handle the inner layers either with explicit
conditional logic, or with more layers of visitors.  The ability to
compose patterns in this way allows us to specify complicated matching
conditions clearly and concisely, making the code easier to read and
less error-prone.

### Any patterns

Just as the `var` pattern matches anything and binds its target to
that, the `_` pattern matches anything -- and binds nothing.  Again,
this is not terribly useful as a standalone pattern, but is useful as
a way of saying "I don't care about this component."  If a
subcomponent is not relevant to the matching, we can make this
explicit (and prevent ourselves from accidentally accessing it) by
using a `_` pattern.  For example, we can further rewrite the
"multiply by zero" case from the above example using a `_` pattern:

    case MulNode(IntNode(0), _), MulNode(_, IntNode(0)) -> 0;

Which says that the other component is irrelevant to the matching
logic, and doesn't need to be given a name -- or even be extracted.


## Patterns are the dual of constructors (and literals)

Patterns may appear to be a clever syntactic trick, combining several
common operations, but they are in fact something deeper -- they are
duals of the operations we used to construct, denote, or otherwise
obtain values.  The literal `0` stands for the number zero; when used
as a pattern, it matches the number zero.  The expression `new
Point(1, 2)` constructs a `Point` from a specific `(x, y)` pair; the
pattern `Point(int x, int y)` matches all points, and extracts the
corresponding `(x, y)` values.  For every way we have of constructing
or obtaining a value (constructors, static factories, etc), there can
be a corresponding pattern that takes apart that value into its
component parts.  The strong syntactic similarity between construction
and deconstruction is no accident.

### Static patterns

Deconstruction patterns are implemented by class members that are
analogous to constructors, but which run in reverse, taking an
instance and destructuring it into a sequence of components.  Just as
classes can have static factories as well as constructors, it is also
reasonable to have static _patterns_.  And just as static factories
are an alternate way to create objects, static patterns can perform
the equivalent of deconstruction patterns for types that do not expose
their constructors.

For example, `Optional` is constructed with factory methods
`Optional.of(v)` and `Optional.empty()`.  We can expose static
patterns accordingly that operate on `Optional` values, and extract
the relevant state:

```{.java}
switch (opt) {
    case Optional.empty(): ...
    case Optional.of(var v): ...
}
```

The syntactic similarly between how the object is constructed and how
it is destructured is again not accidental.  (An obvious question is
whether instance patterns make sense as well; they do, and they
provide API designers with some better choices than we currently have.
Static and instance patterns will be covered in greater depth in a
separate document.)

## Pattern bind statements

We've already seen two constructs that can be extended to support
patterns: `instanceof` and `switch`.  Another pattern-aware control
construct we might want is a _pattern binding statement_, which
destructures a target using a pattern.  For example, say we have:

```{.java}
record Point(int x, int y);
record Rect(Point p0, Point p1);
```

And we have a `Rect` which we want to destructure into its bounding
points.  An unconditional destructuring might look like:

```.{java}
Rect r = ...
match Rect(var p0, var p1) = r;
// use p0, p1
```

Here, we assert (and the compiler will check) that the pattern is
_total_ on the target type, so we destructure the target and bind its
components to new variables.  If the pattern is _partial_ on the
target operand, and thus we cannot guarantee it will match, we can
provide an `else` clause:

```.{java}
Object r = ...
match Rect(var p0, var p1) = r
else throw new IllegalArgumentException("not a Rect");
// use p0, p1
```

We could even use a nested pattern to extract the corner coordinates
in one go:

```.{java}
Rect r = ...
match Rect(Point(var x0, var y0), Point(var x1, var y1)) = r;
// use x0, x1, y0, y1
```

A `match` statement can take multiple `P=target` clauses; in this
case, all clauses must match.  We could restate the nested match above
as follows:

```{.java}
match Rect(Point p1, Point p2) = r,
      Point(var x0, var y0) = p1,
      Point(var x1, var y1) = p2;
```

More precisely, for a `match` statement with pattern `P`, all the
bindings of `P` must be definitely assigned when the `match` statement
completes normally.  In general, this means that the else clause must
either match something else, or must terminate abruptly (such as by
throwing), but we might wish to add a third possibility -- an "anonymous
matcher" whose bindings are the bindings from the pattern being matched:

```{.java}
match Foo(int a, int b) = maybeAFoo
else {
    a = 0;
    b = 0;
}
```

While the operand of the else block looks like an ordinary block, it
is type-checked as if it were a matcher whose declaration is `matcher
anonymous(int a, int b)`.

Like `switch`, `match` may throw `NullPointerException` at runtime if we
attempt to destructure a `null` and do not provide an `else` clause.

### Summary of patterns and control flow constructs

We've now seen several kinds of patterns:

 - Constant patterns, which test their target for equality with a
   constant;
 - Type patterns, which perform an `instanceof` test, cast the target,
   and bind it to a pattern variable;
 - Deconstruction patterns, which perform an `instanceof` test, cast
   the target, destructure the target, and recursively match the
   components to subpatterns;
 - Method patterns, which are more general than deconstruction
   patterns;
 - Var patterns, which match anything and bind their target;
 - The any pattern `_`, which matches anything and binds nothing.

We've also seen several contexts in which patterns can be used:

 - A `switch` statement or expresion;
 - A `instanceof` predicate;
 - A `match` statement.

Other possible kinds of patterns, such as _collection patterns_, could
be added later.  Similarly, other linguistic constructs, such as
`catch`, could potentially support pattern matching in the future.

## Pattern matching and records

Pattern matching connects quite nicely with another feature currently
in development, _records_ (data classes).  A data class is one where
the author commits to the class being a transparent carrier for its
data; in return, data classes implicitly acquire deconstruction
patterns (as well as other useful artifacts such as constructors,
accessors, `equals()`, `hashCode()`, etc.)  We can define our `Node`
hierarchy as records quite compactly:

```{.java}
sealed interface Node { }

record IntNode(int value) implements Node;
record NegNode(Node node) implements Node;
record SumNode(Node left, Node right) implements Node;
record MulNode(Node left, Node right) implements Node;
record ParenNode(Node node) implements Node;
```

We now know that the only subtypes of `Node` are the ones here, so the
`switch` expressions in the examples above will benefit from
exhaustiveness analysis, and not require a `default` arm.  (Astute
readers will observe that we have arrived at a well-known construct,
_algebraic data types_; records offer us a compact expression for
product types, and sealing offers us the other half, _sum types_.)

## Scoping

Pattern-aware constructs like `instanceof` have a new property: they
may introduce variables from the middle of an expression.  An obvious
question is: what is the scope of those pattern variables?  Let's look
at some motivating examples (the details are in a separate document.)

```{.java}
if (x instanceof String s) {
    System.out.println(s);
}
```

Here, the pattern variable `s` is used in the body of the `if`
statement, which makes sense; by the time we're executing the body,
the pattern must have matched, so `s` is well-defined, and we should
include `s` in the set of variables that are in scope in the body of
the `if`.  We can extend this further:

```{.java}
if (x instanceof String s && s.length() > 0) {
    System.out.println(s);
}
```

This makes sense too; since `&&` is short-circuiting, so whenever we
execute the second condition, the match has already succeeded, so `s`
is again well-defined for this use, and we should include `s` in the
set of variables that are in scope for the second subexpression of the
conditional.  On the other hand, if we replace the AND with an OR:

```{.java}
if (x instanceof String s || s.length() > 0) {  // error
    ...
}
```

we should expect an error; `s` is not well-defined in this context,
since the match may not have succeeded in the second subexpression of
the conditional.  Similarly, `s` is not well-defined in the
else-clause here:

```{.java}
if (x instanceof String s) {
    System.out.println(s + "is a string");
    // OK to use s here
}
else {
    // error to use s here
}
```

But, suppose our condition inverts the match:

```{.java}
if (!(x instanceof String s)) {
    // error to use x here
}
else {
    System.out.println(s + "is a string");
    // OK to use s here
}
```

Here, we want `s` to be in scope in the else-arm (if it were not, we
would not be able to freely refactor `if-then-else` blocks by
inverting their condition and swapping the arms.)

Essentially, we want a scoping construct that mimics the _definite
assignment_ rules of the language; we want pattern variables to be in
scope where they are definitely assigned, and not be in scope when
they are not.  This allows us to reuse pattern variable names, rather
than making up a new one for each pattern, as we would have to here:

```
switch (shape) {
    case Square(Point corner, int length): ...
    case Rectangle(Point rectCorner, int rectLength, int rectHeight): ...
    case Circle(Point center, int radius): ...
}
```

If the scope of pattern variables were similar to that of locals, we
would be in the unfortunate position of having to make up unique names
for every case, as we have here, rather than reusing names like
`length`, which is what we'd prefer to do.  Matching scope to definite
assignment gives us that -- and comports with user expectations of
when they should be able to use a pattern variable and when not.
