
## Derived Record Creation
### Frequently Asked Questions {.subtitle}

#### Why don't you just use named parameters instead? {#named_parameters}
- ["having a named parameter mechanism for records, but for nothing else \[...\] might seem "better than nothing", \[but\] having two different ways to do something, but one of them only works in narrow situations, is as likely to be frustrating than beneficial."](https://mail.openjdk.org/pipermail/amber-dev/2024-February/008643.html)
- ["The problem with named parameters is that \[...\] you \[must\] give up separate compilation if you wish to use named parameters. That's too high a price"](https://old.reddit.com/r/java/comments/137wdql/jep_441_pattern_matching_for_switch_formally/jj909dq/?context=3)
    - [Archived Link](https://web.archive.org/web/20250325013349/https://old.reddit.com/r/java/comments/137wdql/jep_441_pattern_matching_for_switch_formally/jj909dq/?context=3)
- [_\(See more here\)_](https://mail.openjdk.org/pipermail/amber-dev/2024-November/009090.html)

#### Why don't you just use default parameters instead? {#default_parameters}
- ["\[This introduces\] painful questions about binary compatibility ("surely I can add another parameter with a default?")"](https://mail.openjdk.org/pipermail/amber-dev/2024-January/008507.html)
- ["Default parameters are \[significantly\] more complicated \[...\] It would intrude into \[...\] already complex areas."](https://old.reddit.com/r/java/comments/v97r5t/with_for_records_brian_goetz/ibwfayd/)
    - [Archived Link](https://web.archive.org/web/20250325002437/https://old.reddit.com/r/java/comments/v97r5t/with_for_records_brian_goetz/ibwfayd/)
- [_\(See more here\)_](https://mail.openjdk.org/pipermail/amber-dev/2024-November/009090.html)

#### Why don't you just force unique names? The shadowing rules are confusing in this context. {#confusing_shadowing_rules}
- ["these names _must_ be accessible because they are fixed by the record declaration."](https://mail.openjdk.org/pipermail/amber-dev/2024-April/008738.html)

#### How will changing the record components affect binary compatibility? {#binary_compatibility}
- ["_Adding, deleting, changing, or reordering record components in a record class may break compatibility with pre-existing binaries that are not recompiled; such a change is not recommended for widely distributed record classes._"](https://mail.openjdk.org/pipermail/amber-dev/2024-April/008757.html)
- [\(_See more here._\)](https://mail.openjdk.org/pipermail/amber-dev/2024-April/008760.html)
