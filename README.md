# Assembler

Assembler for the [lieutenant-64](https://github.com/strinsberg/lieutenant-64) virtual machine.

**NOTE**: Currently there are no formal tests. All functions have been tested in the REPL with code in a comment block at the end of each module file. This goes a long way, but I am not sure it can guarantee there are not still a few significant errors. There is also a test program and subroutine module in the test folders. These have been assembled and run on the current version of the VM and performed as expected. However, they only test a subset of the VM operations.

# Purpose

The purpose of this assembler is to allow creation of programs for the lieutenant-64 virtual machine. Without it programs would have to be written in hex code in a hex editor or equivalent. Low level programming can be difficult enough without having to use hex code for programming.

The other application of this is as a target for other compilers or interpreters I might write for fun. The can produce lt64 assembly much easier than binary files or different hardware assembly languages. This allows me to experiment with low level programming and compilers without worrying about actual hardware. Though in the real world llvm is pretty intuitive and powerful.

If nothing else it is just Fun!

# Usage

The standalone jar for the program can be downloaded from the release page. This should work on any computer with a JVM.

The following commands will assemble a correct lt64-asm file into and run it on the lieutenant-64 VM.

```
$ java -jar lt64-asm.jar <program_file>
$ lt64 <assembled_binary>
```

The assembler does it's best to catch some errors, but these are mostly syntactic. It is an assembly language so there is not much chance of catching semantic errors. Also the low level nature of the VM means that mistakes may not produce intuitive output. The VM can be built in debug mode to help. See the lieutenant-64 README fir more information on building for debugging.

# Assembly Programs

**TODO:** Give more in-depth explanation of the sytax and available operations
than is given below

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
I.e. **push** -> `:push`, **daddu** -> `:daddu`

In the example there are two operations that do not follow this
rule: `:load-lb` `:store-lb`. In the VM there are **load** and **store**,
but these operations operate in relation to the free memory pointer (**fmp**).
In order to access labels(raw addresses) instead of treating the top of the
stack as an offset from **fmp** the operation needs a flag set in the top byte
of the op code. Adding `-lb` assembles the op code to pass this flag so that
it will access the address given directly. It can be thought of as **load-label**.
Also, in the module example below `:second` is used for **sec** and for **fst**
`:first` would be used.

**TODO:** Give a complete description of any operations with names that
differ from VM op names, along with any pseudo or compound instructions that
are added by the assembler and not present in the VM.

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
program and should contain the main routine of the program. Currently all
operations are written without brackets and only `:push` `:dpush` and `:label`
are followed by an argument. Other operations expect arguments on the stack
as defined by the VM. `:label` takes a plain name for a label that can be
referenced by any other part of the program. Labels do not have to be declared
before they are accessed. The assembler does a pass to resolve all existing
labels before replacing their uses with addresses.

After the main there are avariable number of optional `(proc)` and `(include)`
lists. The subroutine `(proc)` element takes as it's first argument a label
name and then is filled with the instructions to execute. It can be run
from `(main)` or anther subroutine by frist pushing the label
`:push subroutine-label` followed by `:call`. Subroutines do not automatically
return so they should always end with `:ret`. The assembler does not check this
as you may have more than one path, so don't for get it. Each subroutine in the
declared in the program file has direct access to the labels in the `(main)` and
any other subroutines.

The `(include)` elements take a single argument of a file path relative to the
program path. During the initial assembler path these are completely replaced
with all the subroutines in the included file. Files must be module files that
are like the following example. The can only contain `(include)` and `(proc)`
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

