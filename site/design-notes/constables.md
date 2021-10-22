# Symbolic References for Constants
#### Brian Goetz {.author}
#### March 2018 {.date}

[JEP 303][jep303] exposes compiler intrinsics so that Java source code
can deterministically generate `ldc` and `invokedynamic` bytecodes.
Further, [JEP 309][jep309] defines a new loadable constant pool form,
`CONSTANT_Dynamic`, where the constant is produced by linking a
bootstrap method and invoking it.  This document outlines JDK and
compiler support for these feature.  (This API is for low-level users;
most users will never see this API.)

## Background

The [JVM specification][jvmspec] defines a number of constant pool
forms, a subset of which (the _loadable_ constants) can be used as the
operand of an `ldc` instruction or included in the static argument
list of a bootstrap method.  These correspond to the Java types
`Integer`, `Long`, `Float`, `Double`, `String`, `Class`, `MethodType`,
and `MethodHandle` (and, soon, dynamically computed constants.)  For
each of these constant types, there is a corresponding "live" object
type (`String`, `Class`.)

Activities such as bytecode generation have a frequent need to
describe constants such as classes.  However, a `Class` object is a
poor description for an arbitrary class.  Producing a `Class` instance
has many environmental dependencies and failure modes; loading may
fail in because the desired class does not exist or may not be
accessible to the requestor, the result of loading varies with class
loading context, loading classes has side-effects, and sometimes may
not be possible at all (such as when the classes being described do
not yet exist or are otherwise not loadable, as in during compilation
of those same classes, or during `jlink`-time transformation.)  So,
while the `String` class is a fine description for a
`Constant_String_info`, the `Class` type is not a very good
description for a `Constant_Class_info`.

A number of activities share the need to deal with classes, methods,
and other entities in a purely nominal form.  Bytecode parsing and
generation libraries must describe classes and method handles in
symbolic form.  Without an official mechanism, they must resort to
ad-hoc mechanisms, whether descriptor types like ASM's `Handle`, or
tuples of strings (method owner, method name, method descriptor), or
ad-hoc (and error-prone) encodings of these into a single string.
Bootstraps for `invokedynamic` that operate by spinning bytecode (such
as `LambdaMetafactory`) would prefer to work in a symbolic domain
rather than with live classes and method handles.  Compilers and
offline transformers (such as `jlink` plugins) need to describe
classes and members for classes that cannot be loaded into the running
VM.  Compiler plugins (such as annotation processors) similarly need
to describe program elements in symbolic terms.  They would all
benefit from having a single, official way to describe such constants.

### Symbolic references

Our solution is to define a family of value-based _symbolic reference_
([JVMS][jvmspec] 5.1) types, capable of describing each kind of
loadable constant.  A symbolic reference describes a constant in
purely nominal form, separate from class loading or accessibility
context.  Some classes can act as their own symbolic references (e.g.,
`String`); for linkable constants we define a family of symbolic
reference types (`ClassRef`, `MethodTypeRef`, `MethodHandleRef`, and
`DynamicConstantRef`) that contain the nominal information to describe
these constants.

## ConstantRef

The symbolic reference API lives in the package
`java.lang.invoke.constant`.  The basic new abstraction is the
`ConstantRef<T>` interface, which indicates that a class acts as a
symbolic reference for a constant whose live type is `T`, and supports
reflective resolution of constants, given a `Lookup`:

```{.java}
public interface ConstantRef<T> {
    T resolveConstantRef(MethodHandles.Lookup lookup)
        throws ReflectiveOperationException;
}
```

The classes `String`, `Integer`, `Long`, `Float`, and `Double` act as
`ConstantRef` for themselves.  Additionally, we provide interfaces
`ClassRef`, `MethodTypeRef`, and `MethodHandleRef` to represent
`Class`, `MethodType`, and `MethodHandle`, and concrete
implementations `ConstantClassRef`, `ConstantMethodHandleRef`, and
`ConstantMethodTypeRef` that correspond to the constant pool forms of
similar name.  We also provide `DynamicConstantRef` for dynamic
(bootstrap-generated) constants.

`ConstantRef` can be used in APIs that wish to constrain their input
or output to be symbolic references to classfile constants; such uses
arise naturally in the intrinsification API, the API for describing
`invokedynamic` bootstrap specifiers, bytecode APIs, compiler plugin
APIs, etc.

### ClassRef

A `ClassRef` describes a `Class` (including the `Class` mirrors
associated with non-reference types, like `int.class`, and array
classes.)  `ClassRef` provides factories for creating class
references, accessors for its state, and combinators to create new
class references (such as going between a component type and the
corresponding array type.)

```{.java}
public interface ClassRef extends ConstantRef<Class<?>> {
    // Factories
    static ClassRef of(String name);
    static ClassRef of(String packageName, String className);
    static ClassRef ofDescriptor(String descriptor);

    // Combinators
    ClassRef array();
    ClassRef inner(String innerName);
    ClassRef inner(String firstInnerName, String... moreInnerNames);

    // Accessors
    boolean isArray();
    boolean isPrimitive();
    ClassRef componentType();
    default String simpleName();
    String descriptorString();
}
```

Because some class mirrors are represented in the constant pool using
`Constant_Class_info`, and others (primitives) are represented using
dynamic constants, there are multiple concrete implementations of
`ClassRef`.

### MethodTypeRef

A `MethodTypeRef` describes a `MethodType`; `MethodTypeRef` uses
`ClassRef` to describe the parameter and return types.
`MethodTypeRef` includes a similar set of combinators as `MethodType`
for modifying return and parameter types, so that bootstraps that want
to work symbolically can perform similar operations as bootstraps that
work on live objects.

```{.java}
public interface MethodTypeRef extends ConstantRef<MethodType> {
    // Factories
    static MethodTypeRef ofDescriptor(String descriptor);
    static MethodTypeRef of(ClassRef returnDescriptor, ClassRef... paramDescriptors);

    // Accessors
    String descriptorString();
    String simpleDescriptor();
    int parameterCount();
    ClassRef parameterType(int index);
    List<ClassRef> parameterList();
    ClassRef[] parameterArray();

    // Combinators
    MethodTypeRef changeReturnType(ClassRef returnType);
    MethodTypeRef changeParameterType(int index, ClassRef paramType);
    MethodTypeRef dropParameterTypes(int start, int end);
    MethodTypeRef insertParameterTypes(int pos, ClassRef... paramTypes);
}
```

### MethodHandleRef

A `MethodHandleRef` describes a method handle.  It can describe both
direct method handles (`ConstantMethodHandleRef`) and derived method
handles; accessors for properties of direct method handles are defined
on `ConstantMethodHandleRef`.

```{.java}
public interface MethodHandleRef extends ConstantRef<MethodHandle> {
    enum Kind {
        @Foldable STATIC(REF_invokeStatic),
        @Foldable VIRTUAL(REF_invokeVirtual),
        @Foldable INTERFACE_VIRTUAL(REF_invokeInterface),
        @Foldable SPECIAL(REF_invokeSpecial),
        @Foldable CONSTRUCTOR(REF_newInvokeSpecial),
        @Foldable GETTER(REF_getField),
        @Foldable SETTER(REF_putField),
        @Foldable STATIC_GETTER(REF_getStatic),
        @Foldable STATIC_SETTER(REF_putStatic);

    // Factories
    static ConstantMethodHandleRef of(Kind kind, ClassRef clazz, String name, MethodTypeRef type);
    static ConstantMethodHandleRef of(Kind kind, ClassRef clazz, String name, String descriptorString);
    static ConstantMethodHandleRef of(Kind kind, ClassRef clazz, String name, ClassRef returnType, ClassRef... paramTypes);
    static ConstantMethodHandleRef ofField(Kind kind, ClassRef clazz, String name, ClassRef type);

    // Accessors
    methodType();

    // Combinators
    MethodHandleRef asType(MethodTypeRef type);
}

public class ConstantMethodHandleRef implements MethodHandleRef {
    // Accessors
    public int refKind();
    public Kind kind();
    public ClassRef owner();
    public String methodName();
    public MethodTypeRef methodType();
}
```

### DynamicConstantRef

A `DynamicConstantRef` describes a dynamic constant in terms of a
bootstrap method, bootstrap arguments, and invocation name and type.

```{.java}
public class DynamicConstantRef<T> implements ConstantRef<T> {
    // Factories
    static<T> DynamicConstantRef<T> of(MethodHandleRef bootstrapMethod, String name, ClassRef type, ConstantRef<?>[] bootstrapArgs);
    static<T> DynamicConstantRef<T> of(MethodHandleRef bootstrapMethod, String name, ClassRef type);
    static<T> DynamicConstantRef<T> of(MethodHandleRef bootstrapMethod, ClassRef type);
    static<T> DynamicConstantRef<T> of(MethodHandleRef bootstrapMethod, String name);
    static<T> DynamicConstantRef<T> of(MethodHandleRef bootstrapMethod);
    static<T> ConstantRef<T> ofCanonical(MethodHandleRef bootstrapMethod, String name, ClassRef type, ConstantRef<?>[] bootstrapArgs);

    // Combinators
    public DynamicConstantRef<T> withArgs(ConstantRef<?>... bootstrapArgs);

    // Accessors
    public String constantName();
    public ClassRef constantType();
    public MethodHandleRef bootstrapMethod();
    public ConstantRef<?>[] bootstrapArgs();
}
```

There are also subtypes of `DynamicConstantRef` for describing
important runtime types such as enums (`EnumRef`) and `VarHandle`s
(`VarHandleRef`).  The class `ConstantRefs` defines useful symbolic
references such as `CR_int` (a `ClassRef` describing the primitive
type `int`) or `NULL` (describing the null value).

### Bytecode writing and reading

If a compiler or bytecode API uses symbolic references to describe
constants, it will have to be able to write constants described by
`ConstantRef` to the constant pool, and translate entries read from
the constant pool to `ConstantRef`.  For each type of constant pool
entry, there is a corresponding concrete symbolic reference type, so a
bytecode writer need only case over the types corresponding to each
constant pool entry, cast to the appropriate type, and call the
appropriate accessor methods.  A bytecode reader would case over the
constant pool types, and call the appropriate `XxxRef` factory method.

For dynamic constants whose bootstraps are "well-known", the library
will lift dynamic constants to the appropriate subtype, if asked (via
the `DynamicConstantRef.ofCanonical()` method.)  For example, a
dynamic constant describing the primitive type `int.class` using the
bootstrap `ConstantBootstraps.primitiveType()` will be lifted to a
`ClassRef` for `int`; a dynamic constant describing an `enum` via the
bootstrap `ConstantBootstraps.enumConstant()` will be lifted to an
`EnumRef`.  Bytecode reading libraries need only materialize dynamic
constants using the `ofCanonical()` factory to deliver strongly typed
symbolic references to their clients.

### Extensibility

The `ConstantRef` hierarchy will eventually be sealed (prohibiting new
direct subtypes beyond the ones defined), but the `DynamicConstantRef`
type will be left open for extension.  Creating a symbolic reference
type for an arbitrary type `T` can be done by creating a subtype of
`DynamicConstantRef`, providing factories for describing the instances
in nominal form, and implementing the `resolveConstantRef()` method.
This is how `EnumRef` and `VarHandleRef` are implemented.

### Representing invokedynamic sites

Call sites for `invokedynamic` are defined similarly to dynamic
constants, with `DynamicCallSiteRef`.

```{.java}
public final class DynamicCallSiteRef {
    // Factories
    public static DynamicCallSiteRef ofCanonical(MethodHandleRef bootstrapMethod, String name, MethodTypeRef type,
                                                 ConstantRef<?>... bootstrapArgs);
    public static DynamicCallSiteRef of(MethodHandleRef bootstrapMethod, String name, MethodTypeRef type,
                                        ConstantRef<?>... bootstrapArgs);
    public static DynamicCallSiteRef of(MethodHandleRef bootstrapMethod, String name, MethodTypeRef type);
    public static DynamicCallSiteRef of(MethodHandleRef bootstrapMethod, MethodTypeRef type);

    // Combinators
    public DynamicCallSiteRef withNameAndType(String name, MethodTypeRef type);
    public DynamicCallSiteRef withArgs(ConstantRef<?>... bootstrapArgs);

    // Accessors
    public String name();
    public MethodTypeRef type();
    public MethodHandleRef bootstrapMethod();
    public ConstantRef<?>[] bootstrapArgs();

    // Reflection
    public MethodHandle dynamicInvoker(MethodHandles.Lookup lookup);
```

## Intrinsics

When the `invokedynamic` bytecode was introduced in Java SE 7, it was
largely intended to be used as a compilation target; no provision was
made for directly accessing the functionality of `invokedynamic` from
Java source code, except through the reflective `dynamicInvoker()`
mechanism.  Over time, as more library functionality was exposed
through bootstrap methods, it became more desirable to be able to
express `invokedynamic` directly in Java source.  In turn, this
requires being able to describe the bootstrap method handle, and the
static bootstrap arguments, as classfile constants.  And, with the
introduction of `Constant_Dynamic` (condy) in JEP 309, there are
additional forms of classfile constants that would be convenient to
express from Java source code.

Our approach is to expose methods that correspond to the `ldc` and
`invokedynamic` instructions, that the compiler can deterministically
_intrinsify_ into the appropriate bytecode instruction, thus allowing
Java source code to directly express these instructions and reason
confidently about their translation by the compiler.

```{.java}
public class Intrinsics {
    public static <T> T ldc(ConstantRef<T> constant) { ... }

    public static Object invokedynamic(DynamicCallSiteRef site,
                                       Object... dynamicArgs)
            throws Throwable { ... }
}
```

When intrinsifying an `ldc()` call, the compiler must first ensure
that the arguments provided are compile-time constants, so that it can
emit the appropriate entries into the constant pool of the class being
generated, which it does by introspecting on the contents of the
`ConstantRef` passed to `ldc()`.

Similarly, when intrinsifying an `invokedynamic`, the compiler will
ensure that the `DynamicCallSiteRef` argument is a compile-time
constants, and then use its contents generate an `invokedynamic`
instruction and the corresponding entries in the constant pool and
`BootstrapMethods` attribute.  Since the `DynamicCallSiteRef` contains
the `MethodType` for the invocation, the compiler will use that to
determine the compile-time type of the result (unlike
signature-polymorphic invocation, which cast context to condition the
return type.)

### Examples

If we want to load the method handle for `String::isEmpty`, we could
do as follows, which would translate as an `ldc` of a `MethodHandle`
constant.

```{.java}
MethodTypeRef mtr = MethodTypeRef.of(CR_void);
MethodHandleRef mhr = MethodHandleRef.of(VIRTUAL, CR_String, "isEmpty", mtr);
...
MethodHandle mh = Intrinsics.ldc(mhr);
```

Similarly, we can load a dynamic constant, but we must first know the
bootstrap method (and describe it as a `MethodHandleRef`.)  To load an
`enum` constant, we could just `ldc` an `EnumRef` constant, but here's
what it looks like using the `enumConstant()` bootstrap directly:

```{.java}
public static <T extends Enum<T>> T enumConstant(Lookup lookup,
                                                 String name,
                                                 Class<T> type);
```

We create a `DynamicConstantRef` to describe the desired constant:

```{.java}
// Convenience method inserts the standard condy preamble
MethodHandleRef bsm
    = MethodHandleRef.ofDynamicConstant(ClassRef.of("MyBootstraps"), "enumConstant");
DynamicConstantRef ElementType_METHOD
    = DynamicConstantRef.of(bsm, "METHOD", ClassRef.of("java.lang.ElementType"));
...
ElementType et = Intrinsics.ldc(ElementType_METHOD);
```

Suppose we have an `invokedynamic` bootstrap method:

```{.java}
public static CallSite returnsStaticArg(MethodHandles.Lookup lookup,
                                        String invocationName,
                                        MethodType invocationType,
                                        String arg) {
    return new ConstantCallSite(MethodHandles.constant(String.class, arg));
}
```

which takes a static string argument, and links a callsite that just
always returns that string.

We can express an `invokedynamic` site for this in Java source with:

```{.java}
ClassRef owner = ClassRef.of("HelperClass");
MethodHandleRef bsm
    = MethodHandleRef.ofDynamicCallSite(owner, "returnsStaticArg",
                                        CR_String, CR_String);
...
String s = Intrinsics.invokedynamic(bsm, "theString");
```

## Constant tracking, propagation, and folding

The arguments provided to `Intrinsics` calls must be compile-time
constants.  However, in the examples we've seen, they are the result
of calling factory methods like `ClassRef.of()`, which don't qualify
as constants.  So, what's going on?

To implement our `Intrinsics` support, we extend the definition of
compile-time constant, and improve the compiler's ability to do
constant propagation and folding.  The language has an existing notion
of compile-time constant; rather than extend this notion, which would
interact with `ConstantValue` treatment (inlining of constants across
compilation units), we define an extended notion of compile-time
constant-ness called a _trackable constant_ (TC).  To start with:

 - String and numeric literals are TC.
 - Arithmetic combinations of TC are TC.
 - Effectively final locals whose initializers are TC are TC.
 - Static final fields _within the same compilation unit_ whose
   initializers are TC are TC.

### Tracking

Rather than go right to constant folding or propagation, we use a
technique called _constant tracking_ that is more flexible.  For each
AST node, if the expression that the node describes is TC, the
compiler evaluates the constant at compile time and associates the
constant value with its node.  For example, the node that describes a
string literal stores a corresponding `String` instance as the tracked
value of that node.  When the compiler encounters a string
concatenation operation, both of whose operand nodes have a tracked
value, it computes the concatenation of that value, and tracks that
value with the concatenation node.  The compiler can then use the
tracked value in later operations, or not; it can fold the node to its
constant value, propagate the constant, or fall back to tree analysis
and bytecode generation as it sees fit. Constant tracking broadens the
reach of existing constant-based optimizations; we have always folded
things like `"a" + "b"`, but until now we haven't been willing to flow
known constants through static final fields or effectively final
locals to expose more opportunities for folding.

Constant tracking is inherently partial; there are many reasons a
constant cannot be computed at compile time.  If the attempt to
compute a tracked values fails, the node simply has no tracked value,
and is therefore not TC.  For example, for the expression `12/0`,
while the compiler ordinarily folds simple arithmetic expressions on
constants, if doing so would cause an exception, it simply treats the
expression as not a constant and generates bytecode to compute `12/0`
(which will surely throw at runtime) instead.

Intrinsification uses constant tracking in the obvious way: if the
node corresponding to the arguments of an intrinsic does not have a
tracked value, it is an error, and if it does, the compiler
introspects on that value to generate the correct code.

Obviously, better support for identification, propagation, and folding
of constants is useful for far beyond mere intrinsification of
constants.

### Constant propagation

Tracking also enables us to perform _constant propagation_.  In the
following code:

    String s = "Foo";
    ...
    m(s);

We would normally translate this as:

    ldc "Foo"
    astore n
    ...
    aload n
    invokestatic m

However, if we know that `s` is TC (because it is an effectively final
local with an TC initializer), rather than fetching from `s`, we can
directly propagate the known constant value instead.  Rather than
loading its value from the local variable, we can load it directly
from the constant pool, so the second line translate to:

    ldc "Foo"
    invokestatic m

By propagating constants to their points of use, we reduce the
complexity of the data flow and expose opportunities for other
optimizations, such as dead code elimination.

### Foldable

To complete the story of why intrinsification works, we have to add
some more ways to generate TC constants.  We mark certain methods,
including certain static factories, accessors, and combinators in the
`ConstantRef` types, as "foldable":

 - Invocations of foldable methods applied to TC constants (and
   receiver, if applicable) are TC.
 - Loads of foldable static final fields across compilation units are
   TC.

To mark a method or field as foldable, the current prototype uses the
`@Foldable` annotation.  The constraints on foldable methods are high;
in order for constant-folding such invocations to be semantically
equivalent, the method must be free of observable side-effects (since
it might well get folded away) and be a pure function of its inputs
(and receiver.)  A foldable method applied to TC arguments (and with
an TC receiver, if an instance method) can be evaluated at compile
time reflectively using the tracked values of the arguments and
receiver.  (Obviously that means that the foldable code must be on the
classpath during compilation; currently `@Foldable` is restricted to
`java.base`.)  If the reflective invocation completes successfully,
the result is tracked with the invocation node.

### Intrinsification

The arguments to the intrinsics methods `ldc()` and `invokedynamic()`,
with the exception of the dynamic arguments to `invokedynamic` (the
last `Object...` argument), must be TC.  This means the trees
associated with their arguments must have tracked constants associated
with them.  If they do not, it is a compile-time error, and the
compiler issues an error identifying which argument was non-constant;
if they do, the tree is replaced with a `ldc()` node, with constant
information scraped from the argument, which must be `String`,
`Integer`, `Long`, `Float`, `Double`, `ConstantClassRef`,
`ConstantMethodTypeRef`, `ConstantMethodHandleRef`, or
`DynamicConstantRef`.  The compiler will reflectively inspect the
arguments, test them against these types, and cast them to the
appropriate type and call the appropriate accessor methods to extract
class names, descriptor strings, etc, and write them to the classfile.

### Constable

So far, we've made relatively little use of the tracked constants,
other than to intrinsify `ldc()` and `invokedynamic()`, and to
propagate string and numeric constants.  In order to do more, we need
to be able to map back from tracked values (including the result of
reflective evaluation of foldable methods and fields) to
constants that can be described in the constant pool.  Just as a
`ConstantRef` has a `resolveConstantRef()` method that allows you to
reflectively go from the nominal constant description to the live
object it describes, `Constable` provides the reverse direction.  A
type is `Constable` if it can produce a `ConstantRef` to describe its
live values.

```java
public interface Constable<T> {
    Optional<? extends ConstantRef<? super T>> toConstantRef(MethodHandles.Lookup lookup);
}
```

The types `String`, `Integer`, `Long`, `Float`, `Double`, `Class`,
`MethodType`, `MethodHandle`, `Enum`, and `VarHandle` have been fitted
to implement `Constable`.  As with `@Foldable`, the bar for
`Constable` is high; the effect of loading the constant described by
`toConstantRef()` must be identical, in value and observable
side-effects, as executing the path by which the `Constable` was
created.

### Constant folding

With the addition of `Constable`, we are finally ready to perform more
comprehensive compile-time constant folding.  If a node has a tracked
value, and it is an instance of `Constable`, we can attempt to convert
it to a `ConstantRef` (a partial operation), and, if that succeeds,
fold the node into an `ldc` of the corresponding `ConstantRef`.  (Not
all `@Foldable` methods must yield a `Constable`; builder instances
may not be `Constable` themselves, but if their `build()` method
produces a `Constable`, we can constant-fold that.)

As an example, consider this code:

    ClassRef cr1 = ClassRef.of("java.lang.String");
    ClassRef cr2 = cr1.array();
    System.out.println(cr2.descriptorString());

Because `ClassRef.of()` is `@Foldable` and its argument is constant,
the compiler reflectively invokes it and tracks the resulting
`ClassRef` (which represents `String`) with the invocation node, and
propagates that value into the symbol for the effectively-final
variable `cr1`.  At the next line, we can repeat the process; the
receiver `cr1` is a constant, so we can invoke the foldable `array()`
method, and get the `ClassRef` for `String[]`, and track that with
`cr2`.  Finally, `descriptorString()` is also foldable, and yields a
`String`, which is `Constable` (its `toConstantRef()` returns itself,
since `String` and friends act as their own nominal descriptor.)  At
this point, we can fold away the tree for `cr2.descriptorString()`,
and replace it with an `ldc` of the descriptor string pulled out of
`cr2` at compile time, `[Ljava/lang/String;`.

### Dead code elimination

Having done constant propagation and folding, the local variables
`cr1` and `cr2` are now effectively unused, and their initializers
(because we know the properties of the `ConstantRef` classes) are
known to be side-effect free.  So both the variables and their
initializers can be eliminated entirely, and the above snippet
compiles to:

    getfield System::out
    ldc `[Ljava/lang/String;`
    invokevirtual println(String)V

If the initializers were `Constable` but not `ConstantRef`, we
couldn't completely eliminate the initializers (they might have side
effects such as loading and initializing classes), but we cans still
replace the initializer with an `ldc` of the constant described by the
result of `Constable.toConstantRef()` -- which will still likely be
more efficient and compact than emitting the corresponding bytecode.

### Path optimization

If the result of `cr2` were used elsewhere, compile-time folding would
still pays dividends, because we can optimize the path by which `cr2`
is created.  The source code first creates a `ClassRef` for `String`
and then uses it to derive a `ClassRef` for `String[]`.  But if you
examine the `ConstantRef` that results from calling `toConstantRef()`
on `cr2`, you'll see that it corresponds to an invocation (via condy)
of `ClassRef.ofDescriptor("[Ljava/lang/String;")`.  So if we fold
other uses of `cr2` to an `ldc` of this `ClassRef`, not only do we get
the caching and sharing that the constant pool gives us for free, but
the initialization takes a more optimal path (going straight to
`String[]`, rather than the indirect path through `String`.)  In this
way, builder-like APIs can fold at compile time and we only need to
reproduce the end result as a constant, not the full path by which it
was computed.  (The burden is on such `@Foldable` and `Constable` APIs
to ensure that this difference is not observable, except as a
performance improvement.)

### String folding

Exposing a `constexpr`-like mechanism for `java.base` can be used as
part of our language evolution strategy.  In [JEP 326][jep326], which
adds raw (and multi-line) string literals to the Java language, the
issue of indentation trimming came up -- if we have a multi-line
snippet of HTML embedded in a Java string, the indentation of the HTML
may not be what is wanted, as it will include the indentation of the
HTML _plus_ the indentation of the Java.  Some people wanted the
compiler to implement a complex algorithm to normalize the
indentation, but the language is not the place for this; it is
unlikely than any one normalization algorithm will satisfy all comers,
and it adds complexity to the language.  Better to expose such
functionality through libraries, such as a `String.trimIndent()`
method:

    String s = `a long multi-
                line string`.trimIndent();

But then people will immediately complain that (a) the overly-long
string is what gets put in the constant pool, and (b) the trimming is
done at run time, possibly redundantly, unless the result is pulled
into a static field.  But, because `trimIndent()` is a pure function
of its receiver, we can mark `trimIndent()` as foldable and do the
trimming at compile time, and put the trimmed string in the constant
pool (and do constant propagation on it.)  Supporting compile-time
foldable libraries can reduce the pressure to have the language do
things that really should be the province of libraries.

### De-capturing of lambdas

Lambdas that capture effectively final local variables from the
enclosing lexical context are compiled differently, and are more
expensive to evaluate, than non-capturing lambdas.  Constant
propagation can move constant information into lambdas, potentially
moving them from capturing to non-capturing.  For example, given:

    String prefix = "#> ";
    strings.stream().map(s -> p + s).forEach(System.out::println);

The lambda `s -> p + s` is a capturing lambda, capturing the local
variable `p` from the enclosing context.  However, if the compiler is
able to identify it as a compile-time constant (which it now can), it
can constant-propagate the constant into the lambda, which reduces the
number of captured arguments (in this case, from 1 to 0.)

## Summary

The `ConstantRef` API is useful for intrinsics, but it is also useful
for a number of other activities, such as bytecode APIs (which must
deal in unresolved constants), bootstraps that spin bytecode (such as
`LambdaMetafactory`, and offline code analyzers (annotation
processors, `jlink` plugins.)  So while our initial target was
intrinsics for `ldc` and `invokedynamic`, the resulting API is far
more generally useful for many low-level activities.

Similarly, the language support for constant propagation and folding
were initially motiviated by the needs of intrinsics, but the
mechanism is far more general and has the potential to pay generous
dividends in the form of generating better code.





[jep303]: http://openjdk.java.net/jeps/303
[jep309]: http://openjdk.java.net/jeps/309
[jep326]: http://openjdk.java.net/jeps/326
[jvmspec]: https://docs.oracle.com/javase/specs/jvms/se8/html/index.html
