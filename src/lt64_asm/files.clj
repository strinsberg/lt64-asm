(ns lt64-asm.files
  (:require 
    [lt64-asm.symbols :as sym]
    [lt64-asm.stdlib :as stdlib]
    [clojure.java.io :as jio]
    [clojure.edn :as edn]))

;; Load ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-program
  "Given a filename open the file and read its contents as edn.
  Throws if there is a problem opening the file or the contents cannot be
  correctly parsed as edn."
  [filename]
  (try
    (edn/read-string (slurp filename))
    (catch Exception e
      (throw
        (Exception. (str
                      "Error: Problem with program file: "
                      filename
                      "\n"
                      (.getMessage e)))))))


;; File Type Identifiers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lt64-program
  "Checks to ensure that a list representation of an lt64-asm file have the
  required elements to be an lt64-asm program.
  Returns the file if it is valid otherwise throws an Exception."
  [file]
  (if (and (sym/lt64-prog? file)
           (sym/static? (second file))
           (sym/main? (nth file 2)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm file"))))

(defn lt64-module
  "Checks to ensure that a list representation of an lt64-asm file have the
  required elements to be an lt64-asm module.
  Returns the file if it is valid otherwise throws an Exception."
  [file]
  (if (and (sym/lt64-mod? file)
           (every? #(or (sym/proc? %) (sym/include? %))
                   (rest file)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm module"))))

;; Include and expand proc section ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare expand)

(defn include-stdlib
  "Include selected subroutines from the stdlib.
  Throws if any of the given subroutine names are present."
  [procs]
  (if (empty? procs)
    (stdlib/include-all)
    (stdlib/include-procs procs)))

(defn include
  "Loads and expands the file in an include directive.
  The expansion may load and expand submodules in the included file.
  Throws if the file is cannot be processed or is not a valid lt64-asm module."
  [[_ filename & procs]]
  (if (= filename "stdlib")
    (include-stdlib procs)
    (trampoline
      expand
      (->> filename
           get-program
           lt64-module))))

(defn expand
  "Given a list of subroutine and include directives returns them all as a
  valid list of subroutine directives. This means loading and expanding all
  includes into the subroutine directives they contain.
  Throws an Exception for stack overflow errors. The function is mutually
  recursive with include and if there are circular dependencies it will
  overflow the stack."
  [procs-and-includes]
  (try
    (mapcat #(if (sym/include? %)
               (trampoline include %)
               (list %))
            procs-and-includes)
    (catch StackOverflowError _
      (throw (Exception.
               (str "Error: Stack overflow while expanding includes. "
                    "Most likely there are circular dependencies."))))))

(defn process-user-macros
  [procs-and-includes program-data]
  (let [{:keys [macros procs]}
        (group-by #(if (sym/macro? %) :macros :procs)
                  procs-and-includes)]
    [procs
     (assoc program-data
            :user-macros
            (reduce #(assoc %1 (second %2) (rest (rest %2)))
                    {}
                    macros))]))

;;; C file creation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn wrap-prog
  "Given an assembled byte array for a program converts it to a string
  and wraps it in the C code necessary to append it to the end of the single
  file VM."
  [program-bytes]
  (str "size_t prog_length() { return " (count program-bytes) ";  }\n"
       "void set_program(WORD* mem, size_t length) {\n"
       "  char program[] = { "
       (clojure.string/join ", " program-bytes)
       " };\n"
       "  memcpy(mem, program, length);\n"
       "}\n"))

(defn create-standalone-cfile
  "Given an assembled byte array for a program combines it with the single
  file VM to create a standalone single file C program.
  The produced program does not need a compiled VM to run, as it contains the
  VM inside of it. Greatly increases program size in order to package the VM
  and program, but allows easier portability of the program."
  [program-bytes path]
  (spit path
        (str (slurp (jio/resource "lt64.c"))
             (wrap-prog program-bytes))))

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(get-program "test/lt64_asm/max_of_list.lta")
(include '(include "test/lt64_asm/max_proc.lta"))
(include '(include "stdlib" even?))
(include '(include "stdlib" even))
(include '(include "stdlib"))

(def test-procs
  '((proc name :push :pop)
    (macro :!my-op :push 1 :pop)
    (include "somefile.lta")
    (macro :!mop :dpush 22 :dpop)))
(process-user-macros test-procs {})
;
),
