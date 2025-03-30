
## Flexible Constructor Bodies
### Frequently Asked Questions {.subtitle}

#### Why do we need this feature? What is so important about it? {#why_is_this_a_jep}
- [_Value Classes depend on this feature, so enabling this feature for all classes allows Value Classes to not be as much of an exception._](https://openjdk.org/jeps/401#Early--late-construction)

#### Why don't you just use a Factory Method? {#factory_methods}
- ["Constructors were designed to allow validation. This change makes supporting that use-case easier"](https://old.reddit.com/r/java/comments/12rvfjz/jep_447_statements_before_super/jgwq8v1/?context=3)
    - [Archived Link](https://web.archive.org/web/20250330013638/https://old.reddit.com/r/java/comments/12rvfjz/jep_447_statements_before_super/jgwq8v1/?context=3)

#### How is this related to the `this-escape` warning? {#this-escape}
- ["\[Both attempt\] to prevent a superclass constructor from invoking an overridden method that would then see uninitialized subclass fields \[while granting different forms of freedom\]."](https://mail.openjdk.org/pipermail/amber-dev/2025-March/009266.html)
    - [_(See here to learn more about `this-escape`)_](https://bugs.openjdk.org/browse/JDK-8299995)
