
# JEP 495: Simple Source Files and Instance Main Methods
## Frequently Asked Questions {.subtitle}

#### Why don't you just get rid of `void main()` too? {#get_rid_of_void_main}
- ["The semantics of a field and a local variable are very different"](https://mail.openjdk.org/pipermail/amber-dev/2024-May/008767.html)

#### Why don't you just add `readInt/readDouble` to `java.io.IO`? {#read_nums_too}
- ["Methods that are automatically imported in this way _effectively become part of the langauge_.  The bar for that is very high."](https://mail.openjdk.org/pipermail/amber-dev/2024-November/009039.html)
- ["Because it greatly complicates the "reading state" that has to be understood by the user"](https://mail.openjdk.org/pipermail/amber-dev/2024-November/009033.html)

#### Why don't you just static import all of `java.lang`? {#static_import_java_lang}
- ["It creates a perverse incentive to put MORE stuff in java.lang, just to get the importing."](https://mail.openjdk.org/pipermail/amber-dev/2025-January/009200.html)
