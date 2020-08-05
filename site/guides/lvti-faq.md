# Local Variable Type Inference: Frequently Asked Questions

#### Brian Goetz and Stuart Marks\
August 2019

## Why have `var` in Java?

Local variables are the workhorse of Java. They allow methods to
compute significant results by cheaply storing intermediate
values. Unlike a field, a local variable is declared, initialized, and
used in the same block. The name and initializer of a local variable
are often more important for a reader's understanding than the
type. Commonly, the name and initializer carry just as much
information as the type: `Person person = new Person();`

The role of `var` in a local variable declaration is to stand in for
the type, so that the name and initializer stand out: `var person =
new Person();` The compiler infers the type of the local variable from
the initializer. This is especially worthwhile if the type is
parameterized with wildcards, or if the type is mentioned in the
initializer. Using `var` can make code more concise without
sacrificing readability, and in some cases it can improve readability
by removing redundancy. 

## Does this make Java dynamically typed? Is this like `var` in JavaScript?

No and no. Java is still a statically typed language, and the addition
of `var` doesn't change this. `var` can be used in a local
variable declaration instead of the variable's type. With `var`, the
Java compiler _infers_ the type of the variable at compile time, using
type information obtained from the variable's initializer. The
inferred type is then used as the static type of the variable.
Typically, this is the same as the type you would have written
explicitly, so a variable declared with `var` behaves exactly as if
you had written the type explicitly.

Java compilers have performed type inference for many years. For
example, in Java 8, the parameters of a lambda expression do not need
explicit types because the compiler infers their types from how the
lambda expression is used:

    List<Person> list = ...
    list.stream().filter(p -> p.getAge() > 18) ...

In the code snippet above, the lambda parameter `p` is inferred to
have the static type `Person`. If the `Person` class is changed so that it
no longer has a `getAge` method, or if the list is changed to be a
list of type other than `Person`, type inference will fail with a
compile-time error.

## Is a `var` variable final?

No. Local variables declared with `var` are non-final by
default. However, the `final` modifier can be added to `var`
declarations:

    final var person = new Person();

There is no shorthand for `final var` in Java. Languages such as Scala
use `val` to declare immutable (final) variables. This works well in
Scala because all variables - locals and fields alike -
are declared using a syntax of the form

    val name : type

or

    var name : type

You can include or omit the `": type"` part of the declaration depending
on whether or not you want type inference. In Scala, the choice
between mutability and immutability is orthogonal to type inference.

In Java, `var` can be used only where type inference is desired; it
cannot be used where a type is declared explicitly. If `val` were added,
it too could be used only where type inference is used. The use of `var`
or `val` in Java could not be used to control immutability if the type
were declared explicitly.

In addition, Java allows the use of `var` only for local variables,
not for fields. Immutability is much more significant for fields,
whereas immutable local variables are comparatively rarely used.

Using `var`/`val` keywords to control immutability is a feature that
seems like it ought to carry over cleanly from Scala to Java. In Java,
however, it would be much less useful than it is in Scala.

## Won't bad developers misuse this feature to write terrible code?

Yes, bad developers will write terrible code no matter what we
do. Withholding a feature won't prevent them from doing so. But, when
used properly, using type inference allows developers to also write
better code.

One way that `var` may encourage developers to write better code is that
it lowers the overhead of declaring a new variable.  If the overhead
of declaring a variable is high, developers will often avoid doing so,
and create complex nested or chained expressions that impair
readability solely in order to avoid declaring more variables.  With
`var`, the overhead of pulling a subexpression into a named variable is
lower, so developers are more likely to do so, resulting in more
cleanly factored code.

When a feature is introduced, it is common that at first, programmers
will use, overuse, and maybe even abuse that feature, and it takes
some time for the community to converge on a reasonable set of
guidelines for what uses are reasonable and what uses are not. It's
probably reasonable to use `var` fairly frequently though not for the
majority of local variable declarations.

Starting with Local Variable Type Inference (LVTI), we're publishing
material about its intent and recommended usage (such as this FAQ, and
the [LVTI Style Guidelines][1]) around the same time the feature is
delivered. We hope that this will accelerate the community's
convergence on what constitutes reasonable usage, and that it will
help avoid most cases of abuse.

## Where can `var` be used?

`var` can be used for declaring local variables, including index
variables of for-loops and resource variables of the
try-with-resources statement.

`var` cannot be used for fields, method parameters, and method return
types. The reason is that types in these locations appear explicitly
in class files and in javadoc specifications. With type inference,
it's quite easy for a change to an initializer to cause the variable's
inferred type to change. For local variables, this is not a problem,
because local variables are limited in scope, and their types are not
recorded directly into class files. However, type inference could
easily cause a problem if types for fields, method parameters, and
method return types were inferred.

For example, suppose that the return type of a method were inferred
from the expression in the method's `return` statement. A change to the
method's implementation might end up changing the type of the
expression in the `return` statement. This in turn might change the
method's return type. This could result in a source or binary
incompatibility. Such incompatible changes should not arise from
harmless-looking changes to the implementation.

Suppose a field's type were inferred. A change to the field's
initializer could change the field's type, which might unexpectedly
break reflective code.

Type inference is ok within the implementation, but not in APIs. API
contracts should be declared explicitly.

What about private fields and methods, which are not part of APIs? In
theory, we could have chosen to support `var` for private fields and for
the return type of private methods, without worry that this would
cause incompatibilities due to separate compilation and dynamic
linkage.  We chose to limit the scope of type inference in this way
for simplicity. Trying to push the boundary to include some fields and
some method returns makes the feature considerably more complex and
harder to reason about, but only marginally more useful.

## Why is an initializer required on the right-hand side of `var`?

The type of the variable is inferred from the type of the initializer.
This means, of course, that `var` can only be used when there is an
initializer.  We could have chosen to infer the type from the
assignments to the variable, but that would have made the feature
considerably more complex, and it could potentially lead to misleading
or hard-to-diagnose errors. In order to keep things simple, we've
defined `var` so that only local information is used for type
inference.

Suppose that we allowed type inference based on assignment in multiple
locations, separate from the variable declaration. Consider this
example:

    var order;
    ...
    order = "first";
    ...
    order = 2;

If a type were chosen based on (say) the first assignment, it might
cause an error at another statement that's quite distant from the
cause of the error. (This is sometimes referred to as the
"action-at-a-distance" problem.)

Alternatively, a type could be chosen that's compatible with all
assignments. In this case one might expect that the inferred type
would be `Object`, because that's the common superclass of `String` and
`Integer`. Unfortunately, the situation is more complicated than
that. Since both `String` and `Integer` are `Serializable` and `Comparable`,
the common supertype would be an odd intersection type that's
something like

    Serializable & Comparable<? extends Serializable & Comparable<...>>

(Note that it isn't possible to declare a variable of this type
explicitly.) Also note that this results in a boxing conversion when
17 is assigned to `x`, which might be unexpected and undesirable.

To avoid these problems, it seems preferable to require that the type
be inferred using an explicit initializer.

## Why can't you use `var` with `null`?

Consider this declaration (which is illegal):

    var person = null; // ERROR

The `null` literal denotes a value of a special _null type_ ([JLS
4.1][2]) that is the subtype of all reference types in Java. The only
value of the _null type_ is `null` itself, therefore, the only value
that could ever be assigned to a variable of the _null type_ is
`null`. This isn't very useful.

A special rule could be made so that a `var` declaration initialized
to `null` is inferred to have type `Object`. This could be done, but
it raises the question of what the programmer intended. Presumably the
variable is initialized to `null` so that it can be assigned to some
other value later. In that case it seems unlikely that inferring the
variable's type as `Object` is the correct choice.

Instead of creating some special rules to handle this case, we've
disallowed it. If you want a variable of type `Object`, declare it
explicitly.

## Can you use `var` with a diamond on the right-hand side?

Yes, it works, but it's probably not what you want. Consider:

    var list = new ArrayList<>();

This will infer the type of list to be `ArrayList<Object>`. In general,
it's preferable use an explicit type on the left with diamond on the
right, or use `var` on the left with an explicit type on the
right. See the [LVTI Style Guidelines][1], guideline G6, for further
information.

[1]: http://openjdk.java.net/projects/amber/LVTIstyle.html

[2]: https://docs.oracle.com/javase/specs/jls/se11/html/jls-4.html#jls-4.1
