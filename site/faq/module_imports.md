
## Module Import Declarations
### Frequently Asked Questions {.subtitle}

#### What about class name clashes? Like `java.util.List` and `java.sql.List`? {#class_name_clash}
- ["Compiler error \(which you can then resolve with, say, `import java.sql.Date`\)"](https://old.reddit.com/r/java/comments/1bqneuy/jep_draft_module_import_declarations_preview/kx4usz8/)
    - [Archived link](https://web.archive.org/web/20250319015257/https://old.reddit.com/r/java/comments/1bqneuy/jep_draft_module_import_declarations_preview/kx4d3dk/)

#### Why modules specifically? {#why_modules_specifically}
- [\[The\] goal is to align the dependency granularity with the source code.](https://old.reddit.com/r/java/comments/1bqneuy/jep_draft_module_import_declarations_preview/kx7uzth/)
    - [Archived link](https://web.archive.org/web/20250319015802/https://old.reddit.com/r/java/comments/1bqneuy/jep_draft_module_import_declarations_preview/kx7uzth/)
- [`import module M` in your sources should align with \[...\] `requires M` in your module-info.java]()
    - [Archived link](https://web.archive.org/web/20250319015802/https://old.reddit.com/r/java/comments/1bqneuy/jep_draft_module_import_declarations_preview/kx7uzth/)

#### Why do some modules give me an error when trying to import them, like `import module java.se`?
- ["If your program is in the unnamed module, then its 
environment is the default set of root modules for the unnamed module \[...\] java.se is no longer (since JDK 11) in 
the default set of root modules for the unnamed module."](https://mail.openjdk.org/pipermail/amber-spec-observers/2024-July/004451.html)
