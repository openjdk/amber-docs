### String Tapas Redux: Beyond mere string interpolation

#### Jim Laskey and Brian Goetz September 2021

Some time ago, we talked about all the things we might want to do with strings:
multi-line strings, raw strings, interpolated strings.    At the time, we sated
our appetite with the first course -- text blocks -- and now are ready to talk
about what we can do next.

It is one of the most commonly requested features to support some sort of
_string interpolation_, which is useful for formatting log messages and snippets
of HTML, JSON, XML, or SQL.  While Java already has many ways to combine
constant strings with non-constant values (concatenation, `String::format`,
`MessageFormat`), developers would prefer something more direct, for several
reasons:

 - **Ceremony**.  Writing string interpolation expressions, rather than calls to
   template-formatting libraries, is less work.
 - **Readability**.  In many cases (though not all) a string interpolation
   expression like `"My name is ${name}, I am ${age} years old"` is more
   readable than its equivalent with `String::format`, because the labels ("My
   name is") and the corresponding parameters are right next to each other.
 - **Safety**.  A long format string or a long list of interpolants invites
   mistakes, such as the arity of parameters not matching that of format
   specifiers, or the types of the parameters not matching the corresponding
   format specifiers.

However, there are reasons we've been hesitant to do such a feature, including:

  - **Injection attacks**.  Constructing SQL queries or JSON expressions with string
    interpolation is convenient, but is at risk for [injection
    attacks](https://xkcd.com/327/).  Improving mechanisms for constructing
    composite strings without similarly improving or enabling safer mechanisms
    for constructing queries would surely widen the attack surface.  This is
    asking users to choose between convenience and security.
  - **Localization**.  Java has a strong commitment to internationalization;
    introducing a more convenient but less localizable mechanism for
    constructing messages will result in fewer applications being localized.
    This is asking users to choose between convenience and flexibility.
  - **Formatting**.  A naive interpretation of string interpolation deprives us of
    the ability to format with format specifiers such as field widths,
    locale-sensitive numeric formatting, etc.  This is asking users to choose
    between convenience and expressiveness.

The versions of this feature implemented by many popular languages offer the
desired convenience in the simple cases, but fall afoul of many of these
downsides.  We may want the convenience of string interpolation, but we also
want safety and flexibility across a range of domains.

| Language&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Example |
|:---|:---|
| C#            | `$"{x} plus {y} equals {x + y}"`      |
| Groovy        | `"$x plus $y equals ${x + y}"`        |
| Haskell       | `[i\|#{x} plus #{y} equals #{x + y}\|]` |
| JavaScript    | &#96;`${x} plus ${y} equals ${x + y}`&#96; |
| Kotlin        | `"$x plus $y equals ${x + y}"`        |
| Scala         | `f"$x%d plus $y%d equals ${x + y}%d"` |
| Python        | `f"{x} plus {y} equals {x + y}"`      |
| Ruby          | `"#{x} plus #{y} equals #{x + y}"`    |
| Swift         | `"\(x) plus \(y) equals \(x + y)"`    |
| VisualBasic   | `$"{x} plus {y} equals {x + y}"`      |

We’re not interested in merely doing “string interpolation” as it has been
interpreted by other languages. We would like to do better.

#### What's wrong with string interpolation?

The only case handled by most other languages that support string interpolation
is the simplest one -- uninterpreted concatenation.

```
// Scala
val greeting = s"Hello, $name, I am $age years old"
```

The feature illustrated here is constrained in many ways: the format string is
not validated, the parameters are not validated or transformed in any way, the
parts are combined by a very constrained mechanism (the result must be exactly
the segments of the format string concatenated with the string value of the
parameters), and finally, the result must be a `String`.  While these might be
convenient defaults, not being able to customize any of these behaviors is a
severe limitation.

In addition, the surfacing of the feature in the language is confusingly ad-hoc;
it requires a different delimiter from "regular" strings, as well as a different
set of rules for separating verbatim content from embedded expressions.  In the
first course (text blocks), an important goal was that string literals and text
blocks be different stackings of the same basic feature, rather than wholly
separate features (this is one reason "raw string literals" was withdrawn.)  We
would like to follow the same discipline here; embedded parameters should be
part of the overall string expression feature, not a separate thing.

#### Another level of indirection

We can meet our diverse goals by separating _mechanism_ from _policy_.
How we introduce parameters into string expressions is mechanism; how we combine
the parameters and the string into a final result (e.g., concatenation) is
policy.  The language may need to have an opinion about how a templatized
expression is expressed, but the semantics of how parameters are validated,
transformed, and combined should remain in the hands of ordinary library code.
Users should be able to select the templating policy they want, and be able to
capture templating policies in libraries for reuse.

A templating policy might be described by an interface like:

```
interface TemplatePolicy<T> {
    T apply(String templateString, List<Object> parameters);
}
```

An implementation of a template policy is an ordinary object that implements
some parameterization of `TemplatePolicy`.  The simplest template policy is what
every other language does -- concatenation -- and can be exposed by the standard
libraries.

We can express template processing as _instance behavior_ on a policy object:

```
String s = STR."Hello \{name}, I am \{age} years old.";
```

where `STR` is a static field of `TemplatePolicy` which captures the obvious
policy.

The escape sequence `\{` is currently unused (and therefore currently illegal in
string literals and text blocks), so this choice of parameter carrier is
compatible with the existing string literal and text block features.  (Swift
uses `\(...)`, which would also be a valid choice.)  This means we do not need
to invent a new form (or two) of "string template expression" with a different
delimiter or prefix character.

The policy object has the flexibility to validate the format string and
parameters, interpret the format string and parameters as it sees fit, combine
them as it sees fit (not just sequential concatenation), and produce a result
that is not even a `String`.  The compiler shreds a parameterized string
expression into the constant and non-constant parts, and arranges for the
combination method on the policy object to be invoked.

#### Examples

Delegating control to a policy object dramatically expands the expressiveness
and safety of the feature.

**String formatting.**  Formatting libraries like `String::format` offer more
than just interpolation; they offer rich formatting options such as field-width
management, leading-zero fills, hex conversion, locale-specific presentation,
etc.  Making straight interpolation easier but no improvement for formatting
libraries leaves users with an unpleasant choice of convenience or rich
formatting.  If we wanted to format the number `age` using the various modifiers
supported by the `%d` format specifier, we wouldn't want to abandon the convenience
of the straightforward expression.

On the other hand, it would be folly to bake the `String::format` descriptor
language into the Java language; representation and interpretation of the format
specifiers should be under the control of the template policy.  But we can
encapsulate this in a library that implements this set of format specifiers, and
exposes a constant policy object.  Here, `FMT` is a policy object that
interprets a set of format specifiers that are similar to `printf` /
`String::format`, using the convention that the format specifier goes right
before the "hole":

```
String s = FMT."Hello %s\{name}, I am %10d\{age} years old.";
```

When the format string is shredded into constant and variable parts, the end of
each constant part should contain a format descriptor which is used to condition
the formatting of the following parameter (and the policy object can validate
this).  The Java language knows nothing of the format descriptor language; this
is interpreted solely by the formatter library.

Even ignoring the choice of format descriptor language, library methods like
`String::format` often embody difficult choices, such as whether or not to use
the currently selected `Locale` to format numeric quantities.  Some users like
the flexibility they get from such automatic localization; others resent the
performance overhead of `Locale` processing.  By exposing a mechanism by which
users and libraries can implement their own formatters, users are not
constrained by these choices made by libraries on their behalf -- there could be
both locale-sensitive and locale-insensitive formatters for the same domain, and
the user can choose the one they want.

**Validation and normalization.**  SQL statements are often parameterized by
some dynamic data value.  Unfortunately, the data being interpolated is often
tainted by user input.  The JDBC framework includes builders for _prepared
statements_, which sanitize inputs and compose the query in a SQL-aware manner:

```
PreparedStatement ps
    = connection.prepareStatement("SELECT * FROM Person p where p.last_name = ?");
ps.setString(1, name);
```

This will escape any `'` characters in `name` and surround it with `'`
characters before performing the interpolation.  If `name` is `"Bobby"`, the
resulting query will be `SELECT * FROM Person p where p.last_name = 'Bobby'`.

With a convenient string interpolation feature, it is sorely tempting to
construct SQL queries with:

```
String query = "SELECT * FROM Person p where p.last_name = '$name'";
ResultSet rs = connection.createStatement().executeQuery(query);
```

Unfortunately, this now exposes the application to potentially disastrous SQL
injection attacks unless `name` has been previously sanitized.  Trading
security for convenience is not a good trade.

We can get the best of both worlds with a SQL-specific policy object that
performs the sanitization that `PreparedStatement` does, and more:

 - Enforce that any quotes in the format string itself are balanced.
 - Enforce that interpolation points do not occur in "quoted" parts of the
   format string.
 - Wrap parameters with quotes.
 - Escape any quote characters in parameters.

SQL databases generally follow a common set of rules around single-quotes, but
some databases also have other supported forms of quotes.  To the extent that a
given database has its own nonstandard quoting rules, we would like to defend
against attacks that exploit those as well.  This means that we don't just need
a SQL-specific policy object; we need a `Connection`-specific policy
object, because the `Connection` comes from the JDBC driver for the specific
database we're talking to.

While there are many API choices that JDBC might select, one might be to make
`Connection` also be a policy object; then we could ask the connection
to format the query directly:

```
var query = connection."SELECT * FROM \{table}";
```

**Non-string results.**  One could easily imagine a JSON or XML library
providing a similar level of quote discipline and injection protection in those
domains (they are vulnerable to injection attacks too):

```
String s = JSON."""
                {
                   "a": \{a},
                   "b": \{b}
                }
                """;
```

The policy referred to by `JSON` would perform the proper validation of the
format string, and quoting and escaping of the parameters `a` and `b` before
composing the final string.

But, do we even want to produce a string at all?  Many JSON libraries allow us
to represent JSON documents through a `Json` type; it might be more efficient
for the JSON policy object to go directly to that representation rather than
first constructing a (potentially large) string and then parsing the resulting
string.  While some policy objects will surely want to produce strings,
there's no reason all of them do.  Our policy interface can be parameterized
by the type it returns, as `TemplatePolicy` illustrated.  So this JSON example
could be:

```
Json j = JSON."""
              {
                 "a": \{a},
                 "b": \{b}
              }
              """;
```

which is more direct and potentially more efficient.

Another use for non-string results is when formatting messages for logging.
Many logging calls are for debug information, and often debug logging is turned
off.  Many frameworks allow you to provide a `Supplier<String>` for log messages
that is only invoked if the message is actually going to be logged, to avoid the
overhead of formatting a string that is going to be thrown away.  A lazy
policy object could produce `Supplier<String>` rather than `String` itself.

**Localization.**  The examples so far have been about interpolation enhanced
with validation and transformation, but this can be taken further.  The JDK
contains APIs such as `ResourceBundle` to support localization of messages. A
resource bundle is a mapping from key names to localizable template strings.
(These template strings use a different format than `String::format`, in part
because they must support changing the order of parameters as part of the
localization process; the interpolation "hole" in the localized template
contains the index of the corresponding parameter.)

If resource bundles had a `TemplatePolicy`, then they could use the format
string as a key to look up the localized string, and then perform the
interpolation, all in one go:

```
String message = resourceBundle."error: file \{filename} not found";
```

which would have the effect of using the string `"error: file \{} not found"` as
the key, mapping it to an appropriate localized error message for the current
locale, reordering the parameters according to the `{nn}` holes in the
localized messages, and formatting the result using the `MessageFormat` rules.

## Templated Strings

A reasonable question is what should a templated string expression _without_ a
policy evaluate to?   For those who "just" want string interpolation, the
"obvious" answer is to use the concatenation policy, but there is a better
choice: evaluate to an "unprocessed" string template, which can be passed to a
library for later processing.  We can model an unprocessed template as:

```
interface TemplatedString {
    String formatString();
    List<Object> parameters();
    // more
}
```

and say that the following:

```
var s = "Hello, \{name}, I am \{age} years old";
```

evaluates to a `TemplatedString`.  Libraries like `String::format` can provide
overloads that accept templated strings, so templated strings can be passed
directly to libraries:

```
String format(String formatString, Object... parameters);
String format(TemplatedString ts);
```

We can now recast our policy interface to take a templated string:

```
interface TemplatingPolicy<T, E extends Exception> {
    T apply(TemplatedString ts) throws E;
}
```

(We've also snuck in another parameter, that allows policies to declare that
they throw checked exceptions that callers would have to deal with, such as
`SQLException`, though most will likely instantiate `E` with
`RuntimeException`.)

## Restrictions

We may wish to place some syntactic restrictions on the parameters to limit
readability and safety hazards (at the expense of expressiveness).  At one
extreme of the spectrum, we could restrict to only allowing identifiers, as
`bash` does, but this is surely too restrictive.  At the other extreme, we could
allow arbitrary expressions.  But, Java's expressions cover a broad range,
including string literals (which could create confusion over what is part of the
format string and what is parameter), switch expressions (which can contain
statements), and auto-incrementing expressions (which have side-effects); we may
want to prune this back to eliminate puzzlers-in-waiting.

A possible middle ground is the subset of expressions generated from numeric
literals, variables, field selection, arithmetic operators, and array
dereference.  This is rich enough to describe parameters like `a.b[i-1]` or
`fooCount+barCount`, but is guaranteed side-effect-free and does not contain
embedded string literals.

## Translation

The policy APIs shown here have the drawback of primitive and array boxing;
further, for formatting such as that performed by `String::format`, much of the
work is in scanning the format string, which is usually a constant at a given
invocation site.  There are opportunities for more efficient translation with
`invokedynamic` that avoids these pitfalls.  We wish to achieve a balance
between making it easy for libraries to implement templating policies, and
allowing policies (such as the `String::format` equivalent) to support a more
efficient translation; the details of this will be covered separately.
