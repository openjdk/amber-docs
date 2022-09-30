# Paving the on-ramp
#### Brian Goetz {.author}
#### September 2022 {.date}


Java is one of the most widely taught programming languages in the world.  Tens
of thousands of educators find that the imperative core of the language combined
with a straightforward standard library is a foundation that students can
comfortably learn on.  Choosing Java gives educators many degrees of freedom:
they can situate students in `jshell` or Notepad or a full-fledged IDE; they can
teach imperative, object-oriented, functional, or hybrid programming styles; and
they can easily find libraries to interact with external data and services.  

No language is perfect, and one of the most common complaints about Java is that
it is "too verbose" or has "too much ceremony."  And unfortunately, Java imposes
its heaviest ceremony on those first learning the language, who need and
appreciate it the least.  The declaration of a class and the incantation of
`public static void main` is pure mystery to a beginning programmer.  While
these incantations have principled origins and serve a useful organizing purpose
in larger programs, they have the effect of placing obstacles in the path of
_becoming_ Java programmers. Educators constantly remind us of the litany of
complexity that students have to confront on Day 1 of class -- when they really
just want to write their first program.  

As an amusing demonstration of this, in her JavaOne keynote appearance in 2019,
[Aimee Lucido](https://www.youtube.com/watch?v=BkPPFiXUwYk) talked about when
she learned to program in Java, and how her teacher performed a rap song
to help students memorize `"public static void main"`.  Our hats are off to
creative educators everywhere for this kind of dedication, but teachers
shouldn't have to do this.

Of course, advanced programmers complain about ceremony too.  We will never be
able to satisfy programmers' insatiable appetite for typing fewer keystrokes,
and we shouldn't try, because the goal of programming is to write programs that
are easy to read and are clearly correct, not programs that were easy to type.
But we can try to better align the ceremony commensurate with the value it
brings to a program -- and let simple programs be expressed more simply.  

## Concept overload

The classic "Hello World" program looks like this in Java:

```
public class HelloWorld { 
    public static void main(String[] args) { 
        System.out.println("Hello World");
    }
}
```

It may only be five lines, but those lines are packed with concepts that are
challenging to absorb without already having some programming experience and
familiarity with object orientation. Let's break down the concepts a student
confronts when writing their first Java program:

  - **public** (on the class).  The `public` accessibility level is relevant
    only when there is going to be cross-package access; in a simple "Hello
    World" program, there is only one class, which lives in the unnamed package.
    They haven't even written a one-line program yet; the notion of access
    control -- keeping parts of a program from accessing other parts of it -- is
    still way in their future.

  - **class**.  Our student hasn't set out to write a _class_, or model a
    complex system with objects; they want to write a _program_.  In Java, a
    program is just a `main` method in some class, but at this point our student
    still has no idea what a class is or why they want one.

  - **Methods**.  Methods are of course a key concept in Java, but the mechanics
    of methods -- parameters, return types, and invocation -- are still
    unfamiliar, and the `main` method is invoked magically from the `java`
    launcher rather than from explicit code.  

  - **public** (again).  Like the class, the `main` method has to be public, but
    again this is only relevant when programs are large enough to require
    packages to organize them.  

  - **static**.  The `main` method has to be static, and at this point, students
    have no context for understanding what a static method is or why they want
    one.  Worse, the early exposure to `static` methods will turn out to be a
    bad habit that must be later unlearned.  Worse still, the fact that the
    `main` method is `static` creates a seam between `main` and other methods;
    either they must become `static` too, or the `main` method must trampoline
    to some sort of "instance main" (more ceremony!)  And if we get this wrong,
    we get the dreaded and mystifying `"cannot be referenced from a static
    context"` error.

  - **main**.  The name `main` has special meaning in a Java program, indicating
    the starting point of a program, but this specialness hides behind being an
    ordinary method name.  This may contribute to the sense of "so many magic
    incantations."

  - **String[]**.  The parameter to `main` is an array of strings, which are the
    arguments that the `java` launcher collected from the command line.  But our
    first program -- likely our first dozen -- will not use command-line
    parameters. Requiring the `String[]` parameter is, at this point, a mistake
    waiting to happen, and it will be a long time until this parameter makes
    sense.  Worse, educators may be tempted to explain arrays at this point,
    which further increases the time-to-first-program.

  - **System.out.println**.  If you look closely at this incantation, each
    element in the chain is a different thing -- `System` is a class (what's a
    class again?), `out` is a static field (what's a field?), and `println` is
    an instance method.  The only part the student cares about right now is
    `println`; the rest of it is an incantation that they do not yet understand
    in order to get at the behavior they want.

That's a lot to explain to a student on the first day of class.  There's a good
chance that by now, class is over and we haven't written any programs yet, or
the teacher has said "don't worry what this means, you'll understand it later"
six or eight times.  Not only is this a lot of _syntactic_ things to absorb, but
each of those things appeals to a different concept (class, method, package,
return value, parameter, array, static, public, etc) that the student doesn't
have a framework for understanding yet.  Each of these will have an important
role to play in larger programs, but so far, they only contribute to "wow,
programming is complicated."  

It won't be practical (or even desirable) to get _all_ of these concepts out of
the student's face on day 1, but we can do a lot -- and focus on the ones that
do the most to help beginners understand how programs are constructed.

## Goal: a smooth on-ramp

As much as programmers like to rant about ceremony, the real goal here is not
mere ceremony reduction, but providing a graceful _on ramp_ to Java programming.
This on-ramp should be helpful to beginning programmers by requiring only those
concepts that a simple program needs.  

Not only should an on-ramp have a gradual slope and offer enough acceleration
distance to get onto the highway at the right speed, but its direction must
align with that of the highway.  When a programmer is ready to learn about more
advanced concepts, they should not have to discard what they've already learned,
but instead easily see how the simple programs they've already written
generalize to more complicated ones, and both the syntatic and conceptual
transformation from "simple" to "full blown" program should be straightforward
and unintrusive.  It is a definite non-goal to create a "simplified dialect of
Java for students".

We identify three simplifications that should aid both educators and students in
navigating the on-ramp to Java, as well as being generally useful to simple
programs beyond the classroom as well:

 - A more tolerant launch protocol
 - Unnamed classes
 - Predefined static imports for the most critical methods and fields

## A more tolerant launch protocol

The Java Language Specification has relatively little to say about how Java
"programs" get launched, other than saying that there is some way to indicate
which class is the initial class of a program (JLS 12.1.1) and that a public
static method called `main` whose sole argument is of type `String[]` and whose
return is `void` constitutes the entry point of the indicated class.  

We can eliminate much of the concept overload simply by relaxing the
interactions between a Java program and the `java` launcher:

 - Relax the requirement that the class, and `main` method, be public.  Public
   accessibility is only relevant when access crosses packages; simple programs
   live in the unnamed package, so cannot be accessed from any other package
   anyway.  For a program whose main class is in the unnamed package, we can
   drop the requirement that the class or its `main` method be public,
   effectively treating the `java` launcher as if it too resided in the unnamed
   package.

 - Make the "args" parameter to `main` optional, by allowing the `java` launcher to
   first look for a main method with the traditional `main(String[])`
   signature, and then (if not found) for a main method with no arguments.

 - Make the `static` modifier on `main` optional, by allowing the `java` launcher to
   invoke an instance `main` method (of either signature) by instantiating an
   instance using an accessible no-arg constructor and then invoking the `main`
   method on it.

This small set of changes to the launch protocol strikes out five of the bullet
points in the above list of concepts: public (twice), static, method parameters,
and `String[]`.  

At this point, our Hello World program is now:

```
class HelloWorld { 
    void main() { 
        System.out.println("Hello World");
    }
}
```

It's not any shorter by line count, but we've removed a lot of "horizontal
noise" along with a number of concepts.  Students and educators will appreciate
it, but advanced programmers are unlikely to be in any hurry to make these
implicit elements explicit either.  

Additionally, the notion of an "instance main" has value well beyond the first
day.  Because excessive use of `static` is considered a code smell, many
educators encourage the pattern of "all the static `main` method does is
instantiate an instance and call an instance `main` method" anyway.  Formalizing
the "instance main" protocol reduces a layer of boilerplate in these cases, and
defers the point at which we have to explain what instance creation is -- and
what `static` is.  (Further, allowing the `main` method to be an instance method
means that it could be inherited from a superclass, which is useful for simple
frameworks such as test runners or service frameworks.)

## Unnamed classes

In a simple program, the `class` declaration often doesn't help either, because
other classes (if there are any) are not going to reference it by name, and we
don't extend a superclass or implement any interfaces.  If we say an "unnamed
class" consists of member declarations without a class header, then our Hello
World program becomes:

```
void main() { 
    System.out.println("Hello World");
}
```

Such source files can still have fields, methods, and even nested classes, so
that as a program evolves from a few statements to needing some ancillary state
or helper methods, these can be factored out of the `main` method while still
not yet requiring a full class declaration:

```
String greeting() { return "Hello World"; }

void main() {
    System.out.println(greeting());
}
```

This is where treating `main` as an instance method really shines; the user has
just declared two methods, and they can freely call each other.  Students need
not confront the confusing distinction between instance and static methods yet;
indeed, if not forced to confront static members on day 1, it might be a while
before they do have to learn this distinction.  The fact that there is a
receiver lurking in the background will come in handy later, but right now is
not bothering anybody.

[JEP 330](https://openjdk.org/jeps/330) allows single-file programs to be
launched directly without compilation; this streamlined launcher pairs well with
unnamed classes. 

## Predefined static imports

The most important classes, such as `String` and `Integer`, live in the
`java.lang` package, which is automatically on-demand imported into all
compilation units; this is why we do not have to `import java.lang.String` in
every class.  Static imports were not added until Java 5, but no corresponding
facility for automatic on-demand import of common behavior was added at that
time.  Most programs, however, will want to do console IO, and Java forces us to
do this in a roundabout way -- through the static `System.out` and `System.in`
fields.  Basic console input and output is a reasonable candidate for
auto-static import, as one or both are needed by most simple programs.  While
these are currently instance methods accessed through static fields, we can
easily create static methods for `println` and `readln` which are suitable for
static import, and automatically import them.  At which point our first program
is now down to:

```
void main() {
    println("Hello World");
}
```

## Putting this all together

We've discussed several simplifications:

 - Update the launcher protocol to make public, static, and arguments optional
   for main methods, and for main methods to be instance methods (when a
   no-argument constructor is available); 
 - Make the class wrapper for "main classes" optional (unnamed classes);
 - Automatically static import methods like `println`

which together whittle our long list of day-1 concepts down considerably.  While
this is still not as minimal as the minimal Python or Ruby program -- statements
must still live in a method -- the goal here is not to win at "code golf".  The
goal is to ensure that concepts not needed by simple programs need not appear in
those programs, while at the same time not encouraging habits that have to be
unlearned as programs scale up. 

Each of these simplifications is individually small and unintrusive, and each is
independent of the others.  And each embodies a simple transformation that the
author can easily manually reverse when it makes sense to do so: elided
modifiers and `main` arguments can be added back, the class wrapper can be added
back when the affordances of classes are needed (supertypes, constructors), and
the full qualifier of static-import can be added back.  And these reversals are
independent of one another; they can done in any combination or any order.

This seems to meet the requirements of our on-ramp; we've eliminated most of the
day-1 ceremony elements without introducing new concepts that need to be
unlearned. The remaining concepts -- a method is a container for statements, and
a program is a Java source file with a `main` method -- are easily understood in
relation to their fully specified counterparts.  

## Alternatives

Obviously, we've lived with the status quo for 25+ years, so we could continue
to do so.  There were other alternatives explored as well; ultimately, each of
these fell afoul of one of our goals.

### Can't we go further?

Fans of "code golf" -- of which there are many -- are surely right now trying to
figure out how to eliminate the last little bit, the `main` method, and allow
statements to exist at the top-level of a program.  We deliberately stopped
short of this because it offers little value beyond the first few minutes, and
even that small value quickly becomes something that needs to be unlearned.  

The fundamental problem behind allowing such "loose" statements is that
variables can be declared inside both classes (fields) and methods (local
variables), and they share the same syntactic production but not the same
semantics.  So it is unclear (to both compilers and humans) whether a "loose"
variable would be a local or a field.  If we tried to adopt some sort of simple
heuristic to collapse this ambiguity (e.g., whether it precedes or follows the
first statement), that may satisfy the compiler, but now simple refactorings
might subtly change the meaning of the program, and we'd be replacing the
explicit syntactic overhead of `void main()` with an invisible "line" in the
program that subtly affects semantics, and a new subtle rule about the meaning
of variable declarations that applies only to unnamed classes.  This doesn't
help students, nor is this particularly helpful for all but the most trivial
programs.  It quickly becomes a crutch to be discarded and unlearned, which
falls afoul of our "on ramp" goals.  Of all the concepts on our list, "methods"
and "a program is specified by a main method" seem the ones that are most worth
asking students to learn early.

### Why not "just" use `jshell`?  

While JShell is a great interactive tool, leaning too heavily on it as an onramp
would fall afoul of our goals.  A JShell session is not a program, but a
sequence of code snippets.  When we type declarations into `jshell`, they are
viewed as implicitly static members of some unspecified class, with
accessibility is ignored completely, and statements execute in a context where
all previous declarations are in scope.  This is convenient for experimentation
-- the primary goal of `jshell` -- but not such a great mental model for
learning to write Java programs.  Transforming a batch of working declarations
in `jshell` to a real Java program would not be sufficiently simple or
unintrusive, and would lead to a non-idiomatic style of code, because the
straightforward translation would have us redeclaring each method, class, and
variable declaration as `static`.  Further, this is probably not the direction
we want to go when we scale up from a handful of statements and declarations to
a simple class -- we probably want to start using classes as classes, not just
as containers for static members. JShell is a great tool for exploration and
debugging, and we expect many educators will continue to incorporate it into
their curriculum, but is not the on-ramp programming model we are looking for.  

### What about "always local"?

One of the main tensions that `main` introduces is that most class members are
not `static`, but the `main` method is -- and that forces programmers to
confront the seam between static and non-static members.  JShell answers this
with "make everything static". 

Another approach would be to "make everything local" -- treat a simple program
as being the "unwrapped" body of an implicit main method.  We already allow
variables and classes to be declared local to a method.  We could add local
methods (a useful feature in its own right) and relax some of the asymmetries
around nesting (again, an attractive cleanup), and then treat a mix of
declarations and statements without a class wrapper as the body of an invisible
`main` method. This seems an attractive model as well -- at first.

While the syntactic overhead of converting back to full-blown classes -- wrap
the whole thing in a `main` method and a `class` declaration -- is far less
intrusive than the transformation inherent in `jshell`, this is still not an
ideal on-ramp.  Local variables interact with local classes (and methods, when
we have them) in a very different way than instance fields do with instance
methods and inner classes: their scopes are different (no forward references),
their initialization rules are different, and captured local variables must be
effectively final.  This is a subtly different programming model that would then
have to be unlearned when scaling up to full classes. Further, the result of
this wrapping -- where everything is local to the main method -- is also not
"idiomatic Java".  So while local methods may be an attractive feature, they are
similarly not the on-ramp we are looking for.

