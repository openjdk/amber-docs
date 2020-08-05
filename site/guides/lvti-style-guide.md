# Local Variable Type Inference: Style Guidelines

#### Stuart W. Marks\
March 2018

## Introduction

Java SE 10 introduced type inference for local variables. Previously,
all local variable declarations required an explicit (manifest) type
on the left-hand side. With type inference, the explicit type can be
replaced by the reserved type name `var` for local variable
declarations that have initializers. The type of the variable is
inferred from the type of the initializer.

There is a certain amount of controversy over this feature. Some
welcome the concision it enables; others fear that it deprives readers
of important type information, impairing readability.  And both groups
are right. It can make code more readable by eliminating redundant
information, and it can also make code less readable by eliding useful
information.  Another group worries that it will be overused,
resulting in more bad Java code being written.  This is also true, but
it's also likely to result in more *good* Java code being written.
Like all features, it must be used with judgment.  There's no blanket
rule for when it should and shouldn't be used.

Local variable declarations do not exist in isolation; the surrounding
code can affect or even overwhelm the effects of using `var`. The goal
of this document is to examine the impact that surrounding code has on
`var` declarations, to explain some of the tradeoffs, and to provide
guidelines for the effective use of `var`.

## Principles

### P1. Reading code is more important than writing code.

Code is read much more often than it is written. Further, when writing
code, we usually have the whole context in our head, and take our
time; when reading code, we are often context-switching, and may be in
more of a hurry.  Whether and how particular language features are
used ought to be determined by their impact on *future readers* of the
program, not its original author. Shorter programs can be preferable
to longer ones, but shortening a program too much can omit information
that's useful for understanding the program. The central issue here is
to find the right size for the program such that understandability is
maximized.

We are specifically unconcerned here with the amount of keyboarding
that's necessary to input or to edit a program. While concision may be
a nice bonus for the author, focusing on it misses the main goal,
which is to improve the understandability of the resulting program.

### P2. Code should be clear from local reasoning.

The reader should be able to look at a `var` declaration, along with
uses of the declared variable, and understand almost immediately
what's going on. Ideally, the code should be readily understandable
using only the context from a snippet or a patch. If understanding a
`var` declaration requires the reader to look at several locations
around the code, it might not be a good situation in which to use
`var`. Then again, it might indicate a problem with the code itself.

### P3. Code readability shouldn't depend on IDEs.

Code is often written and read within an IDE, so it's tempting to rely
heavily on code analysis features of IDEs. For type declarations, why
not just use `var` everywhere, since one can always point at a
variable to determine its type?

There are two reasons. The first is that code is often read outside an
IDE. Code appears in many places where IDE facilities aren't
available, such as snippets within a document, browsing a repository
on the internet, or in a patch file. It is counterproductive to have
to import code into an IDE simply to understand what the code does.

The second reason is that even when one is reading code within an IDE,
explicit actions are often necessary to query the IDE for further
information about a variable. For instance, to query the type of a
variable declared using `var`, one might have to hover the pointer
over the variable and wait for a popup. This might take only a moment,
but it disrupts the flow of reading.

Code should be self-revealing. It should be understandable on its
face, without the need for assistance from tools.

### P4. Explicit types are a tradeoff.

Java has historically required local variable declarations to include
the type explicitly. While explicit types can be very helpful, they
are sometimes not very important, and are sometimes just in the
way. Requiring an explicit type can add clutter that crowds out useful
information.

Omitting an explicit type can reduce clutter, but only if its omission
doesn't impair understandability. The type isn't the only way to
convey information to the reader. Other means include the variable's
name and the initializer expression. We should take all the available
channels into account when determining whether it's OK to mute one of
these channels.

## Guidelines

### G1. Choose variable names that provide useful information.

This is good practice in general, but it's much more
important in the context of `var`. In a `var` declaration, information
about the meaning and use of the variable can be conveyed using the
variable's name. Replacing an explicit type with var should often be
accompanied by improving the variable name.  For example:

    // ORIGINAL
    List<Customer> x = dbconn.executeQuery(query);

    // GOOD
    var custList = dbconn.executeQuery(query);

In this case, a useless variable name has been replaced with a name
that is evocative of the type of the variable, which is now implicit
in the `var` declaration.

Encoding the variable's type in its name, taken to its logical
conclusion, results in "Hungarian Notation." Just as with explicit
types, this is sometimes helpful, and sometimes just clutter.  In this
example the name `custList` implies that a `List` is being
returned. That might not be significant. Instead of the exact type,
it's sometimes better for a variable's name to express the role or the
nature of the variable, such as "customers":

    // ORIGINAL
    try (Stream<Customer> result = dbconn.executeQuery(query)) {
        return result.map(...)
                     .filter(...)
                     .findAny();
    }

    // GOOD
    try (var customers = dbconn.executeQuery(query)) {
        return customers.map(...)
                        .filter(...)
                        .findAny();
    }
            
### G2. Minimize the scope of local variables.

Limiting the scope of local variables is good practice in
general. This practice is described in *Effective Java (3rd edition)*,
Item 57. It applies with extra force if `var` is in use.

In the following example, the `add` method clearly adds the special
item as the last list element, so it's processed last, as expected.

    var items = new ArrayList<Item>(...);
    items.add(MUST_BE_PROCESSED_LAST);
    for (var item : items) ...

Now suppose that in order to remove duplicate items, a programmer were
to modify this code to use a `HashSet` instead of an `ArrayList`:

    var items = new HashSet<Item>(...);
    items.add(MUST_BE_PROCESSED_LAST);
    for (var item : items) ...

This code now has a bug, since sets don't have a defined iteration
order. However, the programmer is likely to fix this bug immediately,
as the uses of the `items` variable are adjacent to its declaration.

Now suppose that this code is part of a large method, with a
correspondingly large scope for the `items` variable:

    var items = new HashSet<Item>(...);

    // ... 100 lines of code ...

    items.add(MUST_BE_PROCESSED_LAST);
    for (var item : items) ...

The impact of changing from an `ArrayList` to a `HashSet` is no longer
readily apparent, since `items` is used so far away from its
declaration. It seems likely that this bug could survive for much
longer.

If `items` had been declared explicitly as `List<String>`, changing
the initializer would also require changing the type to
`Set<String>`. This might prompt the programmer to inspect the rest of
the method for code that would be impacted by such a change. (Then
again, it might not.)  Use of `var` would remove this prompting,
possibly increasing the risk of a bug being introduced in code like
this.

This might seem like an argument against using `var`, but it really
isn't. The initial example that uses `var` is perfectly fine. The
problem only occurs when the variable's scope is large. Instead of
simply avoiding `var` in these cases, one should change the code to
reduce the scope of the local variables, and only then declare them
with `var`.

### G3. Consider `var` when the initializer provides sufficient information to the reader.

Local variables are often initialized with constructors. The name of
the class being constructed is often repeated as the explicit type on
the left-hand side. If the type name is long, use of `var` provides
concision without loss of information:

    // ORIGINAL
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    // GOOD
    var outputStream = new ByteArrayOutputStream();

It's also reasonable to use `var` in cases where the initializer is a
method call, such as a static factory method, instead of a
constructor, and when its name contains enough type information:

    // ORIGINAL
    BufferedReader reader = Files.newBufferedReader(...);
    List<String> stringList = List.of("a", "b", "c");

    // GOOD
    var reader = Files.newBufferedReader(...);
    var stringList = List.of("a", "b", "c");

In these cases, the methods' names strongly imply a particular return type, which is then used for inferring the type of the variable.

### G4. Use `var` to break up chained or nested expressions with local variables.

Consider code that takes a collection of strings and finds the string
that occurs most often. This might look like the following:

    return strings.stream()
                  .collect(groupingBy(s -> s, counting()))
                  .entrySet()
                  .stream()
                  .max(Map.Entry.comparingByValue())
                  .map(Map.Entry::getKey);

This code is correct, but it's potentially confusing, as it looks like
a single stream pipeline. In fact, it's a short stream, followed by a
second stream over the result of the first stream, followed by a
mapping of the `Optional` result of the second stream. The most readable
way to express this code would have been as two or three statements;
first group entries into a map, then reduce over that map, then
extract the key from the result (if present), as shown below:

    Map<String, Long> freqMap = strings.stream()
                                       .collect(groupingBy(s -> s, counting()));
    Optional<Map.Entry<String, Long>> maxEntryOpt = freqMap.entrySet()
                                                           .stream()
                                                           .max(Map.Entry.comparingByValue());
    return maxEntryOpt.map(Map.Entry::getKey);

But the author probably resisted doing that because writing the types
of the intermediate variables seemed too burdensome, so instead they
distorted the control flow.  Using `var` allows us to express the the
code more naturally without paying the high price of explicitly
declaring the types of the intermediate variables:

    var freqMap = strings.stream()
                         .collect(groupingBy(s -> s, counting()));
    var maxEntryOpt = freqMap.entrySet()
                             .stream()
                             .max(Map.Entry.comparingByValue());
    return maxEntryOpt.map(Map.Entry::getKey);

One might legitimately prefer the first snippet with its single long
chain of method calls. However, in some cases it's better to break up
long method chains. Using `var` for these cases is a viable approach,
whereas using full declarations of the intermediate variables in the
second snippet makes it an unpalatable alternative.  As with many
other situations, the correct use of `var` might involve both taking
something out (explicit types) and adding something back (better
variable names, better structuring of code.)

### G5. Don't worry too much about "programming to the interface" with local variables.

A common idiom in Java programming is to construct an instance of a
concrete type but to assign it to a variable of an interface
type. This binds the code to the abstraction instead of the
implementation, which preserves flexibility during future maintenance
of the code. For example:

    // ORIGINAL
    List<String> list = new ArrayList<>();

If `var` is used, however, the concrete type is inferred instead of the interface:

    // Inferred type of list is ArrayList<String>.
    var list = new ArrayList<String>();

It must be reiterated here that `var` can *only* be used for local
variables. It cannot be used to infer field types, method parameter
types, and method return types. The principle of "programming to the
interface" is still as important as ever in those contexts.

The main issue is that code that uses the variable can form
dependencies on the concrete implementation. If the variable's
initializer were to change in the future, this might cause its
inferred type to change, causing errors or bugs to occur in subsequent
code that uses the variable.

If, as recommended in guideline G2, the scope of the local variable is
small, the risks from "leakage" of the concrete implementation that
can impact the subsequent code are limited. If the variable is used
only in code that's a few lines away, it should be easy to avoid
problems or to mitigate them if they arise.

In this particular case, `ArrayList` only contains a couple of methods
that aren't on `List`, namely `ensureCapacity` and `trimToSize`. These
methods don't affect the contents of the list, so calls to them don't
affect the correctness of the program. This further reduces the impact
of the inferred type being a concrete implementation rather than an
interface.

### G6. Take care when using `var` with diamond or generic methods.

Both `var` and the "diamond" feature allow you to omit explicit type
information when it can be derived from information already
present. Can you use both in the same declaration?

Consider the following:

    PriorityQueue<Item> itemQueue = new PriorityQueue<Item>();

This can be rewritten using either diamond or `var`, without losing
type information:

    // OK: both declare variables of type PriorityQueue<Item>
    PriorityQueue<Item> itemQueue = new PriorityQueue<>();
    var itemQueue = new PriorityQueue<Item>();

It is legal to use both `var` and diamond, but the inferred type will
change:

    // DANGEROUS: infers as PriorityQueue<Object>
    var itemQueue = new PriorityQueue<>();

For its inference, diamond can use the target type (typically, the
left-hand side of a declaration) or the types of constructor
arguments. If neither is present, it falls back to the broadest
applicable type, which is often `Object`. This is usually not what was
intended.

Generic methods have employed type inference so successfully that it's
quite rare for programmers to provide explicit type arguments.
Inference for generic methods relies on the target type if there are
no actual method arguments that provide sufficient type
information. In a `var` declaration, there is no target type, so a
similar issue can occur as with diamond. For example,

    // DANGEROUS: infers as List<Object>
    var list = List.of();

With both diamond and generic methods, additional type information can
be provided by actual arguments to the constructor or method, allowing
the intended type to be inferred. Thus,

    // OK: itemQueue infers as PriorityQueue<String>
    Comparator<String> comp = ... ;
    var itemQueue = new PriorityQueue<>(comp);

    // OK: infers as List<BigInteger>
    var list = List.of(BigInteger.ZERO);

If you decide to use `var` with diamond or a generic method, you
should ensure that method or constructor arguments provide enough type
information so that the inferred type matches your intent. Otherwise,
avoid using both `var` with diamond or a generic method in the same
declaration.

### G7. Take care when using `var` with literals.

Primitive literals can be used as initializers for `var`
declarations. It's unlikely that using `var` in these cases will
provide much advantage, as the type names are generally short.
However, `var` is sometimes useful, for example, to align variable
names.

There is no issue with boolean, character, `long`, and string
literals. The type inferred from these literals is precise, and so the
meaning of `var` is unambiguous:

    // ORIGINAL
    boolean ready = true;
    char ch = '\ufffd';
    long sum = 0L;
    String label = "wombat";

    // GOOD
    var ready = true;
    var ch    = '\ufffd';
    var sum   = 0L;
    var label = "wombat";

Particular care should be taken when the initializer is a numeric value,
especially an integer literal. With an explicit type on the left-hand
side, the numeric value may be silently widened or narrowed to types
other than `int`. With `var`, the value will be inferred as an
`int`, which may be unintended.

    // ORIGINAL
    byte flags = 0;
    short mask = 0x7fff;
    long base = 17;

    // DANGEROUS: all infer as int
    var flags = 0;
    var mask = 0x7fff;
    var base = 17;

Floating point literals are mostly unambiguous:

    // ORIGINAL
    float f = 1.0f;
    double d = 2.0;

    // GOOD
    var f = 1.0f;
    var d = 2.0;

Note that `float` literals can be widened silently to `double`. It is
somewhat obtuse to initialize a `double` variable using an explicit
`float` literal such as `3.0f`, however, cases may arise where a
`double` variable is initialized from a `float` field. Caution with
`var` is advised here:

    // ORIGINAL
    static final float INITIAL = 3.0f;
    ...
    double temp = INITIAL;

    // DANGEROUS: now infers as float
    var temp = INITIAL;

(Indeed, this example violates guideline G3, because there
isn't enough information in the initializer for a reader to see
the inferred type.)

## Examples

This section contains some examples of where `var` can be used to
greatest benefit.

The following code removes at most `max` matching entries from a
Map. Wildcarded type bounds are used for improving the flexibility of
the method, resulting in considerable verbosity. Unfortunately, this
requires the type of the Iterator to be a nested wildcard, making its
declaration more verbose. This declaration is so long that the header
of the for-loop no longer fits on a single line, making the code even
harder to read.

    // ORIGINAL
    void removeMatches(Map<? extends String, ? extends Number> map, int max) {
        for (Iterator<? extends Map.Entry<? extends String, ? extends Number>> iterator =
                 map.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<? extends String, ? extends Number> entry = iterator.next();
            if (max > 0 && matches(entry)) {
                iterator.remove();
                max--;
            }
        }
    }

Use of `var` here removes the noisy type declarations for the local
variables. Having explicit types for the Iterator and Map.Entry locals
in this kind of loop is largely unnecessary. This also allows the
for-loop control to fit on a single line, further improving
readability.

    // GOOD
    void removeMatches(Map<? extends String, ? extends Number> map, int max) {
        for (var iterator = map.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (max > 0 && matches(entry)) {
                iterator.remove();
                max--;
            }
        }
    }

Consider code that reads a single line of text from a socket using
the try-with-resources statement. The networking and I/O APIs use an
object wrapping idiom. Each intermediate object must be declared as a
resource variable so that it will be closed properly if an error
occurs while opening a subsequent wrapper. The conventional code for
this requires the class name to be repeated on the left and right
sides of the variable declaration, resulting in a lot of clutter:

    // ORIGINAL
    try (InputStream is = socket.getInputStream();
         InputStreamReader isr = new InputStreamReader(is, charsetName);
         BufferedReader buf = new BufferedReader(isr)) {
        return buf.readLine();
    }

Using `var` reduces the noise considerably:

    // GOOD
    try (var inputStream = socket.getInputStream();
         var reader = new InputStreamReader(inputStream, charsetName);
         var bufReader = new BufferedReader(reader)) {
        return bufReader.readLine();
    }

## Conclusion

Using `var` for declarations can improve code by reducing clutter,
thereby letting more important information stand out. On the other
hand, applying `var` indiscriminately can make things worse. Used
properly, `var` can help improve good code, making it shorter and
clearer without compromising understandability.

## References

[JEP 286: Local-Variable Type Inference](http://openjdk.java.net/jeps/286)

[Wikipedia: Hungarian Notation](https://en.wikipedia.org/wiki/Hungarian_notation)

[Bloch, Joshua. Effective Java, 3rd Edition. Addison-Wesley
Professional, 2018.](https://www.pearson.com/us/higher-education/program/Bloch-Effective-Java-3rd-Edition/PGM1763855.html)
