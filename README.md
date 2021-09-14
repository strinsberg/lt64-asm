# Assembler

Assembler for the [lieutenant-64](https://github.com/strinsberg/lieutenant-64) virtual machine.

**NOTE**: Currently there are no formal tests. All functions have been tested in the REPL with code in a comment block at the end of each module file. This goes a long way, but I am not sure it can guarantee there are not still a few significant errors. There is also a test program and subroutine module in the test folders. These have been assembled and run on the current version of the VM and performed as expected. However, they only test a subset of the VM operations.

# Purpose

The purpose of this assembler is to allow creation of programs for the lieutenant-64 virtual machine. Without it programs would have to be written in hex code in a hex editor or equivalent. Low level programming can be difficult enough without having to use hex code for programming.

The other application of this is as a target for other compilers or interpreters I might write for fun. The can produce lt64 assembly much easier than binary files or different hardware assembly languages. This allows me to experiment with low level programming and compilers without worrying about actual hardware. Though in the real world llvm is pretty intuitive and powerful.

If nothing else it is just Fun!

# Usage

The standalone jar for the program can be downloaded from the release page. This should work on any computer with a JVM. Note that the jar has a bit of a startup wait for the JVM. The actual compilation of small programs is fast, but the startup is slow.

The following command will assemble a correct lt64-asm file into a binary that can run on the VM. The `-o` flag gives the output name of the binary. If it is not provided the file will be named `a.ltb`.
```
$ java -jar lt64-asm.jar <program_file> -o <output_file>
```
The next command will assemble a correct lt64-asm file into a standalone C file that includes the VM and the assembled program. It can be compiled with a C compiler and run without directly calling the VM. Because it includes the whole VM this increases file size by a lot, but is useful in some situation, such as submiting a solution to a contest programming judge written in lt64-asm. If the `-c` flag is given without an argument the output file will be named `a.c`.
```
$ java -jar lt64-asm.jar <program_file> -c [<output_file>]
```

### Errors

The assembler does it's best to catch some errors, but these are mostly syntactic. For example if a value is given to a `:push` command that is invalid. This is an assembly language so there is not much chance of catching semantic errors. Also the low level nature of the VM means that mistakes may not produce intuitive output. The VM can be built in debug mode to help. See the lieutenant-64 README for more information on building or debugging.

# Assembly Programs

The below example gives an idea of what an lt64-asm file looks like. Following
the example is some explanation of the syntax and organization of a program. After
that is some information on modules for declaring groups of subroutines that
can be included in programs.

### Example

```clojure
(lt64-asm-prog
  (static
    (:word A 10 1 8 3 2 7 -2 6 3 4 0)
    (:word i 1 1))

  (main
    ;; Load A[0]
    :push A
    :load-lb

    ;; Loop over nums
    :label loop

    ;; Load num at A[i]
    :push i
    :load-lb
    :push A
    :add
    :load-lb

    ;; call max function
    :push max
    :call

    ;; Check if we have run them all
    :push i
    :load-lb
    :push 9
    :eq
    :push end-loop
    :branch
    
    ;; not finished increment i and loop
    :push i
    :push inc-at
    :call
    :push loop
    :jump

    ;; finished
    :label end-loop
    :wprn
    :push 10
    :prnch
    :halt)

  ;; Procedure to load an address on the stack increment and store it
  (proc inc-at
    :first
    :load-lb
    :push 1
    :add
    :swap
    :store-lb
    :ret)

  ;; Import the max procedure
  (include "test/lt64_asm/max_proc.lta")
)
```

### Keywords and Operations

Most assembly operations have the same name as the vm operations
(these ops can be found in the lieutenant-64 VM). To convert an op name to an
assembley keyword add ':' to the front of the lower case version.
I.e. **push** -> `:push`, **daddu** -> `:daddu`. Since each portion of the
program is stored as a LISP list extra whitespace and newlines are ignored.
As seen in the above program any line starting with a `;` will be ignored as
a comment, I used `;;` only out of habit, only one semi-colon is required.

In the example there are two operations that do not follow this
rule: `:load-lb` `:store-lb`. In the VM there are **load** and **store**,
but these operations operate in relation to the free memory pointer (**fmp**).
In order to access labels(raw addresses) instead of treating the top of the
stack as an offset from **fmp** the operation needs a flag set in the top byte
of the op code. Adding `-lb` assembles the op code to pass this flag so that
it will access the address given directly. It can be thought of as **load-label**.
Also, in the module example below `:second` is used for **sec** and for **fst**
`:first` would be used.

### Label Naming Conventions

Lables can be used to set labels for static data, program addresses, and
procedure names. They can include almost any characters except for the
following: `#, ', :, ;`. It is also not possible to start them with a number.
```
Valid: hello mod/subroutine +-2-83!_
Invalid: :hello help:me 'hello help;me 34apples
```

### Program Organization

The program must be organized as in the example. The whole program must be
enclosed inside () with the first element `lt64-asm-prog`. It must then have
`(static)` section even if it does not have any elements. Elements that are
added specify an allocation of static labeled memory. This has the structure
```
(:data-type label-name number-of-elements ...)
```
The `...` is for any initial values to store in the allocated memory, which
can be seen in the example. If too many elements are given they will be discarded
and if not enough are given (or none) the memory will contain zeros.

After the static portion is the `(main)` list. This is the entry point of the
program and should contain the main routine of the program. All
operations are written without brackets and only `:push` `:dpush` and `:label`
are followed by an argument. All other operations expect arguments on the stack
as defined by the VM.

Labels are declared with `:label` which is followed by a plain name for the 
label that can be
referenced by any other part of the program. Labels do not have to be declared
before they are accessed. The assembler does a pass to resolve all existing
labels before replacing their uses with addresses.

After the main there are avariable number of optional `(proc)` and `(include)`
lists. The subroutine `(proc)` element takes as it's first argument a label
name and then is filled with the instructions to execute. It can be run
from `(main)` or anther subroutine by first pushing the label
`:push subroutine-label` followed by `:call`. Subroutines do not automatically
return so they should always end with `:ret`. The assembler does not check this
as you may have more than one path, so don't for get it. Each subroutine in the
declared in the program file has direct access to the labels in the `(main)` and
any other subroutines.

The `(include)` elements take a single argument of a file path relative to the
program path. During the initial assembler path these are completely replaced
with all the subroutines in the included file. Files must be module files that
are like the following example. They can only contain `(include)` and `(proc)`
elements. Because these are directly substituted into the program in place of
the `(include)` any labels will be include as they are. This means that it is
a good thing in module files to prefix names with something to make them
unlikely to clash. One way to do this is to use the module name like so
`mod-name/label-name`. The example shows a very simple module file that is
included in the above program example.

### Module Example
```clojure
(lt64-asm-mod
  ;; find max of two numbers on the stack
  (proc max
    :second
    :second
    :gt
    :push max/second-larger
    :branch
    :swap
    :label max/second-larger
    :pop
    :ret))
```

# Subroutines and Macros

## User Defined Subroutines

As mentioned above it is possible to define subroutines in a program or a module.
These subroutines can be called by pushing their name and using the **call** op.
```
:push sub-name :call
```
Any sbroutine definition **must** include `:ret` to ensure it returns to the next
instruction after the call.

Subroutine names should be plain-text like other labels and should not be started
with a colon `:`. Colons are reserved for the beginning of operations only.

The use of these subroutines is to allow larger groups of instructions to be
collected and reused. They can include labels and calls to other
other subroutines. Pretty much anything that can be done in the main section
can also be done in a subroutine definition.

The main problem with subroutines is that they are not efficient to replace
small groups of instructions. The subroutine call takes `3` instructions and
another one for the return. This means replacing `3` or fewer elements
(instructions and literals) will add to the program length and add some extra
indirection. Subroutine calls are very cheap because no register saving or
argument passing is needed, but are still not ideal for really small replacements.
For small replacements macros can be used.

A collection of standard library subroutines exists that can be included into
any program. To include these procedures one can either include the whole stdlib
`(include "stdlib")` or give names of the subroutines to include
`(include "stdlib" odd? even?)`. If names that are not in the stdlib are given
an error will be thrown.

When referencing standard library labels it is necessary to prefix them with
`std/`. For example `:push std/odd? :call`. This prefix should not be added to
the include list, but this is how they are labeled in the stdlib module. The
prefixes are to help prevent name clashes.

## Macros

Macos are a way to create a new operation that contains multiple elements. They
allow a simpler syntax than subroutines and can improve readability and reduce
typing. However, unlike subroutines macros are replaced by their bodies. This
means that if they are longer than `3` elements their use will increase program
size. This may be desireable in certain cases as they are much nicer to
read and use compared to the call for a subroutine, but most often if a macro
will be used a lot and is `4` or more elments long it should be a subroutine.

There are various builtin macros that are designed to make up for missing
operations in the VM or to make some common operations more readable and
reduce typing time.

Macros are expected to be named like operations, but with a `!` following the
colon. I.e. `:!inc`. This is how all builin macros will be named and should
be followed by user macros. Similar to subroutines, it is desirable in modules
that macros are named with the module name before the macro name.
I.e. `:!my-mod/macro`. This is not enforced, but is good practice to avoid
naming clashes. It should also be noted that user macros are always checked
before builtin macros, and so declaring a user macro with the same name as a
builtin macro will shadow it. This is by design and builtin macros do not have
a prefix other than adding the `!`. The purpose of the `!` is so that it is
clear that an operation is a macro and not a direct VM operation. As these are
added by the assembler to make programs a little simpler and to make up for
some simple but missing ops from the VM they are meant to look almost exactly
like normal operations.

Macros also have the disadvantage of not being able to contain labels or call
subroutines. This is because of when in the assembly process their elements are
processed and added to the byte code. The macros are stripped from the proc and
include section before labels are processed, and then macro ops are assembled
at the same time as other ops, after labels have been replaced. This prevents
them from being used for complex sets of instructions, but as their purpose is
only to replace small collections of operations these limitations are intentional.


## Builtin Subroutines and Macros

### Stdlib Subroutines
- `even?` pops the top word on the stack and pushes a `1` if it is even and a `0` otherwise.
- `odd?` pops the top word on the stack and pushes a `1` if it is odd and a `0` otherwise.

### Buitin Macros
- `:!inc` increments the word on top of the stack by `1`
- `:!dinc` increments the double word on top of the stack by `1`
- `:!->word` pops the top double word on the stack and returns only least significant word. I.e. `[0xaabb, 0xccdd, ->` becomes `[0xccdd, ->`
- `:!->dword` pops the top word on the stack and adds `0` as the most significant word. I.e. `[0xccdd, ->` becomes `[0x0000, 0xccdd, ->`


