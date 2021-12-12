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
           (every? #(or (sym/proc? %) (sym/include? %) (sym/macro? %))
                   (rest file)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm module"))))

;; Include and expand proc section ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn group-directives
  [directives]
  (let [{:keys [macros prs-incs]}
        (group-by #(if (sym/macro? %) :macros :prs-incs)
            directives)
        {:keys [procs includes]}
        (group-by #(if (sym/include? %) :includes :procs)
            prs-incs)]
  {:subroutines procs :macros macros :includes includes}))

(defn include-stdlib
  "Include selected subroutines from the stdlib.
  Throws if any of the given subroutine names are present."
  [procs]
  (if (empty? procs)
    (stdlib/include-all)
    (stdlib/include-procs procs)))

(declare expand-all)
(defn include
  "Loads and expands the file in an include directive.
  The expansion may load and expand submodules in the included file.
  Throws if the file cannot be processed or is not a valid lt64-asm module."
  [[_ filename & proc-names] program-data]
  (if (= filename "stdlib")
    {:procs (include-stdlib proc-names) :data program-data}
    (expand-all
      (->> filename
           get-program
           lt64-module)
      program-data)))

(defn process-includes
  [includes program-data]
  (reduce #(let [{:keys [procs data]}
                 (include %2 (:data %1))]
             (assoc %1
                    :data data
                    :procs (concat procs (:procs %1))))
          {:data program-data :procs '()}
          includes))

(defn process-macros
  [macros program-data]
  (assoc program-data
         :user-macros
         ;; TODO put a cond here and check if the macro is valid
         ;; i.e. cannot have other macros or labels in it, name must
         ;; be :! to start
         (reduce #(if (contains? %1 (second %2))
                    (throw (Exception.
                             (str "Error: Macro has already been declared: "
                                  (second %2))))
                    (assoc %1 (second %2) (rest (rest %2))))
                 (:user-macros program-data)
                 macros)))

(defn expand-all
  [directives program-data]
  (try
    (let [{:keys [macros subroutines includes]}
          (group-directives directives)
          {:keys [procs data]}
          (process-includes includes program-data)]
      {:procs (concat subroutines procs)
       :data (process-macros macros data)})
    (catch StackOverflowError _
      (throw (Exception.
               (str "Error: Stack overflow while expanding includes. "
                    "Most likely there are circular dependencies."))))))

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

(defn wrap-prog-memdump
  "Given memory dump map wraps it in the C code necessary to add it to the
  standalone VM C file. The difference from wrap-program is that it copies
  all memory, and not just up to length. Also, the array is a WORD array,
  because we are not using bytes, but words."
  [memory-dump]
  (str "size_t prog_length() { return " (:length memory-dump) ";  }\n"
       "void set_program(WORD* mem, size_t length) {\n"
       "// length is not used here, but is a required param\n"
       "  WORD program[] = { "
       (clojure.string/join ", " (:memory memory-dump))
       " };\n"
       "  memcpy(mem, program, " (* 2 (count  (:memory memory-dump))) ");\n"
       "}\n"))

(defn create-cfile-from-memdump
  "Take a file for a memory dump and create a standalone cfile that contains
  the memory from the dump."
  [infile outfile]
  (spit outfile
        (str (slurp (jio/resource "lt64.c"))
             (wrap-prog-memdump (edn/read-string (slurp infile))))))

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(def test-procs
  '((proc name :push :pop)
    (macro :!my-op :push 1 :pop)
    (include "somefile.lta")
    (macro :!mop :dpush 22 :dpop)))
(process-user-macros test-procs {})

(process-includes '((include "stdlib" odd?)) {:user-macros {}})

(def test-file (get-program "../ltsp/ltsp.lta"))
(def directives (map #(if (sym/include? %)
                        (list (first %)
                              (str "../ltsp/" (second %)))
                        %)
                     (rest (rest (rest test-file)))))
(identity directives)

(expand-all directives {:user-macros {}})
;
(create-cfile-from-memdump "../ltsp/mem.dump" "../ltsp/test.c")
),
