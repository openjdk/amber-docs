# Pattern Matching for Java -- Runtime and Translation

#### Brian Goetz and John Rose, June 2017

This document explores compiler translation strategies and runtime
support for supporting [_pattern matching_][pattern-match] in the Java
Language.  This is an exploratory document only and does not
constitute a plan for any specific feature in any specific version of
the Java Language.  This document also may reference other features
under exploration; this is purely for illustrative purposes, and does
not constitute any sort of plan or committment to deliver any of these
features.

## Background

We've proposed several kinds of [patterns][pattern-match], such as
deconstructor patterns, constant patterns, and type test patterns, and
several linguistic contexts in which pattern matching might be
supported (`match` predicate, `switch` statement).  An obvious
question is: what bytecode should the compiler generate for a pattern
match, or for the implementation of a pattern?  (There is also the
question of how one might declare a pattern in source code; this is a
topic for a separate document.)

#### What is a pattern?

A _pattern_ is a combination of a _predicate_ that can be applied to a
target, and a set of _binding variables_ that are produced if that
predicate applies.  We can model a pattern as a typed tuple _\<T,B\*\>(z,
b\*)_, where _T_ is the _target type_ of the pattern, _B\*_ are the
types of the binding variables, _z_ is a function _T->bool_
representing the predicate, and _b\*_ is a vector of partial functions
_T->Bi_ that produce the binding variables.

Some patterns are _total_; they match any target, and so their
predicate always returns `true`.  If a pattern is known to be total at
compile time, the compiler can use this knowledge to aid in
exhaustiveness analysis.

We've chosen to model patterns as nominal executable class members
(like methods or constructors).  A pattern is the dual of a method or
constructor; where methods and constructors take _N_ arguments and
produce one result, a pattern takes one argument and produces _N_
results.

#### Encoding patterns as methods

It is easy -- though neither performant nor consequence-free -- to
model patterns as ordinary methods.  Scala models patterns using
static `unapply` methods, which take a single argument and produces
either `Boolean` or an `Option` wrapper for one or more values to
communicate both the success/failure and the resulting binding
variables in a single invocation.  From a user and compiler
perspective, this is fine; the pattern

    def unapply(p : Point) : Option[(int, int)] = Some(p.x, p.y)

is clear enough (successfully deconstructing a `Point` results in an
`(int,int)` pair), and the compiler can readily translate pattern
matches into `unapply` calls.  However, from a cost perspective, this
is pretty bad; the `Option` is a heap-based box, the `Tuple` is a
heap-based box, and, in the absence of specialization, each `int` is
boxed into an `Integer`, for a total of four heap nodes per match.

This heap-based approach is the obvious one given a VM that lacks the
mechanisms that would make stack-based approaches possible, such as
multiple return (leaving multiple values on the stack), out parameters
(an alternate encoding of multiple return), uplevel references to
locals (providing down-stack frames the ability to access up-stack
locals), or value types (unboxed aggregates.)

However, with method handles, `invokedynamic` (indy), and soon,
`constantdynamic` (condy), we can encode a pattern so that most
pattern match operations can proceed without boxing, in a manner
highly optimizable by the JIT, and, as a bonus, in a way that can be
used across JVM languages.

#### Further performance considerations

The most common case of a pattern is that there is no significant
shared computation between testing to see if the pattern applies to
the target and extracting the various components.  For example, if we
are destructuring a `Point`, our test is an `instanceof` test, and
component extraction is field access.

The uncommon case is that there is either significant shared
computation, or there are atomicity requirements that say that the
components should be extracted in a single atomic operation.  In both
cases, it is desirable (in the latter case, necessary) to have an
intermediate carrier to hold the match state.  (In the common case, we
can think of the target as acting as its own carrier.)

We want to identify an encoding for matchers such that the common
cases are fast and allocation free, but that it is possible to use an
intermediate result carrier where that is required by semantics (i.e.,
`synchronized` patterns) or desired for efficiency reasons.  Further,
it should be a binary compatible change to switch from one mode to the
other -- client code shouldn't have to distinguish the two at runtime.
And, for matchers that use intermediate result carriers, it should be
a binary compatible change to migrate to using a value type as a
carrier in the future.

## Basic strategy

We'll start with the runtime representation and work our way up to
classfile representation (and later, source file representation).  Our
runtime strategy represents a pattern as a constant bundle of method
handles.  Let's cover the simple (common) case and then we'll add in
machinery for the general case.

```{.java}
interface __Pattern {
    int numComponents();
    MethodHandle predicate();      // T -> bool
    MethodHandle component(int i); // T -> Bi
}
```

To emit pattern-matching code, a compiler has to acquire a reference
to the pattern object (likely via indy/condy), ask the pattern for its
predicate method handle, invoke the predicate handle on the target,
and, if the predicate succeeds, ask the pattern for the component
method handles and invoke them on the target.  (We can further use
indy/condy to cache the individual method handles, moving the "ask the
pattern for its handles" code to link time.)  If the pattern is
statically known to be total, then invoking the `predicate` can be
omitted.

Illustrating deconstructing a `Point` into its `x` and `y` components
using Java code (though in reality, this would only be called by
compiler-generated code, or via reflection):

```{.java}
__Pattern p = ...;                       // constant
MethodHandle predicate = p.predicate();  // constant
MethodHandle pointX = p.component(0);    // constant
MethodHandle pointY = p.component(1);    // constant
if ((boolean) predicate.invoke(target)) {
    int x = (int) pointX.invoke(target);
    int y = (int) pointY.invoke(target);
    ... use x and y ...
}
```

#### Intermediate carriers

As mentioned, there is a minority of cases where an intermediate
carrier is needed to hold the results of preprocessing the target
(which might just precompute the components and store them in a holder
aggregate, or might just compute precursors for computing the
bindings).  We can model a pattern with such a carrier as a tuple
_\<T,C,B\*\>(p,z,b\*)_, where _C_ is the carrier type, _p_ is a
preprocessing function from `T->C`, and the predicate _z_ and binding
functions _b\*_ are extended to take both the target and the carrier
as arguments (and in practice, will likely ignore one or the other of
them.)  So the predicate _p_ is a function `(T,C)->bool`, and similar
for the binding functions.

```{.java}
interface __Pattern {
    int numComponents();
    MethodHandle preprocess();     // T -> C
    MethodHandle predicate();      // (T,C) -> bool
    MethodHandle component(int i); // (T,C) -> Bi
}
```

Patterns that cannot tolerate concurrent interference can extract the
components into a carrier with the appropriate lock held, as can
patterns requiring complex imperative logic -- without the client
having to treat this case separately.  This protocol is designed to
prevent the uncommon carrier-ful case(s) from polluting the common
carrier-free case with heap allocation (and when needed, we can
eventually use value types rather than reference types for carriers.)

The choice of carrier type is ideally an implementation choice by the
class declaring the pattern, but must be subject to some migration
compatibility constraints, since existing client code will embed the
carrier type in call sites.

#### Combinators

This strategy also allows us to move much of the work of implementing
pattern matching into the runtime (without sacrificing efficiency),
rather than burdening the compiler.  Because patterns are constant
bundles of functions, we can compose them in interesting ways.  For
example, suppose we have patterns _P\<T,C1,A\*\>(pp,pz,pb\*)_ and
_Q\<T,C2,B\*\>(qp,qz,qb\*)_.  We can create a combinator for the pattern
_R_ representing _P && Q_ as follows:

 - The target types of `P` and `Q` must be the same, and the target
   type of `R` is the same as `P` and `Q`;
 - The carrier type of `R` is nominally the tuple `(C1,C2)`, though in
   practice this can be optimized away if either `P` or `Q` (or
   ideally, both) is carrier-free;
 - The `preprocess` of `R` applies the `preprocess` of both `P` and
   `Q`, and constructs a carrier that holds both results;
 - The `predicate` of `R` the logical `AND` of the predicates of `P`
   and `Q`;
 - The components of `R` is the concatenation of the components of `P`
   and `Q`.

Such combinators act only on the (constant) pattern objects, and are
themselves constant pattern objects, so are suitable to construct at
link time with indy/condy.  As a result, compilers can destructure
complex patterns (AND, OR, nested, guarded) into the same pattern
protocol, allowing uniform code generation, and move the complexity of
complex pattern generation to link time via indy/condy.

## Classfile encoding

Logically, patterns are members of classes, like methods and
constructors, though we cannot use the exact encoding of these
artifacts for reasons outlined earlier.

Just as executable members include constructors, static methods, and
instance methods, each of these cases is potentially sensible for
patterns as well.  We've already enumerated several types of useful
patterns; match-everything patterns, type-test patterns,
deconstruction patterns, etc.  Deconstruction patterns (e.g.,
`Point(var x, var y)`) are analogous to constructors -- in fact, for
well-behaved objects, they are the dual of constructors.  And static
patterns are the dual of static factories.

Like methods, patterns have names, and it is reasonable to want to
overload multiple patterns with the same name but different
signatures.  For example, suppose we have overloaded constructors:

    File(String s) { ... }
    File(Path p) { ... }
    File(URI u) { ... }

We have these constructors for client convenience -- whatever the
client has, we can make a `File` out of that.  So similarly, we want
deconstructing a `File` to be equally convenient, and expose
overloaded patterns:

    case File(String name): ...
    -- or --
    case File(Path p): ...
    -- or --
    case File(URI u): ...

#### Method naming

Even if we could easily represent patterns as methods in a class file,
we can easily fall afoul of overloading constraints.  If we took the
Scala approach and mapped these all to methods returning `Tuple` or
`Option[Tuple]`, erasure would prevent us from overloading in this
way.

But, since our runtime representation is a constant bundle of method
handles, we have a different option -- generate methods not to do the
matching and extraction, but instead generate methods to return a
`__Pattern` (which can be invoked at link time via indy/condy).  And
because matchers are not called directly, but instead through pattern
matching (or reflection), it doesn't really matter what we call these
methods, so long as we preserve some reasonable compatibility
requirements.

It's worth noting that we can use the `MethodType` type to describe a
pattern -- in reverse.  Where a method has multiple inputs and one
output, a pattern has one input and multiple outputs.  So we can use
`MethodType` to construct a descriptor for a pattern, just inverting
the input/output sense.  So the `String`-consuming `File` constructor
and the `String`-producing `File` pattern could both be described the
method type `(LString;)LFile;`.  (This duality is not accidental; the
two operations are inverses of each other.)  So let's call `D` the
descriptor for a pattern, and `N` the name for the pattern
(deconstruction patterns are named for the class, just like
constructors.)

If we pick an encoding scheme that can stably encode a descriptor and
is resistent to collisions between overload-equivalent strings (such
as the [symbolic freedom encoding][sym-free]), we can construct an
identifier `DD=Enc(N,D)` and generate static factory methods:

```{.java}
static __Pattern DD() { ... }
```

Just as with methods, we need to encode some additional information,
such as generic type signature, which we can do with an attribute,
such as:

```
Deconstructor_attribute {
    u2 name_index;
    u4 length;
    u1 is_total;
    u2 carrier_type;        // UTF8 type descriptor
    u2 generic_signature;   // S (UTF8 signature)
}
```

#### Additional optimizations

It may be desirable for a `__Pattern` to convey, at run time, that it
is carrier-free; this enables combinators to optimize away boxes for
tuples of carriers.  For patterns that use value type carriers, it may
also be useful for the `__Pattern` to be willing to dispense a
sentinel value for the chosen carrier, which can also be used to
optimize combinators.

This design is extremely `condy`- and JIT-friendly; the compiler can
generate descriptions of constants to describe exactly the patterns,
or sub-parts of patterns, that are needed, and rely on constant pool
caching to provide fast lazy initialization (with all initialization
costs paid at link time.)  The JIT will recognize that all method
handles used for pattern matching are grounded in chains of constants,
and so will routinely inline away all the intermediate data-shuffling
code and carrier management code.

#### Data classes

Even in the absence of a language syntax for declaring member
patterns, the _data classes_ feature currently under consideration
lends itself cleanly to automatically exposing a pattern which matches
the class signature (and the constructor signature).

#### Reflection

As patterns are class members, we'll need reflective support for
discovering and invoking patterns.  This is a straightforward
extension of existing reflective support for `Constructor` and
`Method` members, which are wrappers around the `__Pattern` runtime
abstraction.

## Migration compatibility

Because compile-time information is used to condition code generation,
we need to be clear about what can change, and can't, in a
binary-compatible way.

Compilers can use pattern total-ness to make exhaustiveness decisions,
which is extremely useful.  To be able to rely on this, total-ness
should be an intrinsic property of the pattern that does not change
across maintenance, and changing a total pattern to partial should not
be a binary- or source-compatible change.

However, we envision patterns changing their carrier types, either
changing from a box object to a value type when practical, or going
from a carrier-free to a carrier-ful implementation through ordinary
code evolution.  Therefore, existing call sites that embed the carrier
type must continue to link, which means that if we are changing a
carrier from `C` to `D`, then `C` and `D` should be adaptable to each
other via `MethodHandles.asType()`.  Migrating carriers from a
reference type `LFoo` to a value type `QFoo` would be supported by
`asType()`.  (This suggests that carrier-free implementations should
use `LObject`, allowing future implementations to migrate to a subtype
of `LObject`, or to a value type.)

## Strawman API

We've built a prototype of this approach.  It has factories for
constant patterns, type test patterns, any patterns, and
deconstruction patterns, and combinators for dropping bindings,
adapting the target to a supertype of the target, ANDing patterns
together, and nesting patterns.

```{.java}
public interface __Pattern<T> {
    /**
     * A method handle used to preprocess a target into an intermediate carrier.
     * The method handle accepts a match target and returns the intermediate
     * carrier.
     *
     * If the carrierFree() method returns true, then this method need not be
     * called, and null can be used for the carrier in other method handle
     * invocations.
     */
    MethodHandle preprocess();

    /**
     * A method handle used to determine if the match succeeds.  It accepts
     * the match target and the intermediate carrier returned by preprocess(),
     * and returns a boolean indicating whether the match was successful.
     *
     * If the pattern is declared to always match, then this method need not be
     * called.
     */
    MethodHandle predicate();

    /**
     * A method handle to return the i'th component of a successful match.  It
     * accepts the match target and the intermediate carrier returned by
     * preprocess(), and returns the component.
     */
    MethodHandle component(int i);

    /**
     * Indicates that this pattern does not make use of an intermediate carrier,
     * and that the tryExact() method handle is a no-op.
     * Combinators exploit carrier freedom to reduce unnecessary allocation.
     */
    boolean isCarrierFree();

    /**
     * Returns the pattern descriptor, which is a MethodType whose return type
     * is the match target, and whose parameter types are the components of the
     * match.
     */
    MethodType descriptor();

    /**
     * Returns the match target type
     */
    default Class<?> targetType() {
        return descriptor().returnType();
    }

    /**
     * Return the intermediate carrier type
     */
    default Class<?> carrierType() {
        return preprocess().type().returnType();
    }

    // -- Combinators --

    /**
     * Return a __Pattern that is identical to this one, but with fewer
     * binding components
     * @param positions indices of the binding components to drop
     */
    default __Pattern<T> dropBindings(int... positions) {
        return __Patterns.dropBindings(this, positions);
    }

    // -- Factories --

    /**
     * Return a pattern handle that matches a constant
     */
    static <T> __Pattern<T> ofConstant(Class<T> targetType, T constant) {
        return __Patterns.ofConstant(targetType, constant);
    }

    /**
     * Return a pattern handle that matches null
     */
    static <T> __Pattern<Object> ofNull() {
        return __Patterns.ofNull();
    }

    /**
     * Return a pattern handle that matches a non-null reference
     */
    static<T> __Pattern<T> ofNonNull(Class<T> targetType) {
        return __Patterns.ofNonNull(targetType);
    }

    /**
     * Return a pattern handle that matches targets of the type testType, and
     * produces its target as a binding component.  If
     * testType == targetType, this pattern will always succeed.
     */
    static <T, U extends T> __Pattern<T> ofType(Class<T> targetType, Class<U> testType) {
        return __Patterns.ofType(targetType, testType);
    }

    /**
     * Return a pattern handle for a given target type that always succeeds, and
     * produces its target as a binding component.
     *
     * To accept a broader range of target types, use {@link __Pattern::ofType(Class,Class)}
     * or {@link __Pattern::adaptTarget}
     */
    static <T> __Pattern<T> ofType(Class<T> targetType) {
        return __Patterns.ofType(targetType, targetType);
    }

    /**
     * Return a pattern handle that always succeeds
     */
    static <T> __Pattern<T> ofAny(Class<T> targetType) {
        return __Patterns.ofAny(targetType);
    }

    /**
     * Return a pattern handle that tests its target against testType, and, if
     * successful, extracts components from it according to the {@code components}
     * array of method handles, which each accept a single argument of {@code typeTest}
     */
    static <T, U extends T> __Pattern<T> ofComponents(Class<T> targetType,
                                                          Class<U> testType,
                                                          MethodHandle... components) {
        return __Patterns.ofComponents(targetType, testType, components);
    }

    /**
     * Adapt a pattern handle to a broader target type.
     */
    static<T, U extends T> __Pattern<T> adaptTarget(Class<T> targetType,
                                                        __Pattern<U> pattern) {
        return __Patterns.adaptTarget(pattern, targetType);
    }

    /**
     * Return a pattern handle that is the AND of the two patterns provided.
     * The binding components will be the union of the binding components
     * of the two patterns.
     */
    static<T> __Pattern<T> and(Class<T> targetType,
                                   __Pattern<? extends T> left,
                                   __Pattern<? extends T> right) {
        return __Patterns.and(targetType, left, right);
    }

    /**
     * Returns a pattern handle that matches the outer pattern and then
     * matches its binding components to the nested patterns.  The binding
     * components are the binding components of the outer pattern, followed
     * by the binding components of each nested pattern.
     */
    static<T> __Pattern<T> nested(__Pattern<T> outer, __Pattern... nested) {
        return __Patterns.nest(outer, nested);
    }
```

## Future work

This document focuses mostly on the compilation target, and on
analysis of compatibility requirements.  Deliberately left out
topics include:

 - Language syntax.  While this is a very interesting topic, we prefer
   to lay a solid groundwork that captures runtime description,
   translation strategy, and migration compatibility before moving on
   to the far more subjective subject of defining a language syntax
   for describing patterns.
 - Switch classifiers.  While some languages translate pattern
   switches into chains of if-else, it is often possible to do better,
   by performing a pre-computation (e.g., hashing) on the target, and
   strength-reducing to a traditional switch.  (This generally must be
   tolerant of changes induced by recompilation, such as changed class
   hierarchies, so `indy` is likely called for here.)  We anticipate
   being able to use class hierarchy information to optimize switches
   over subtypes of sealed hierarchies, and avoid redundant tests
   based on known type relationships.  This problem will be addressed
   in a separate document.


[pattern-match]: pattern-matching-for-java
[sym-free]: https://blogs.oracle.com/jrose/entry/symbolic_freedom_in_the_vm

