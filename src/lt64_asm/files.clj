(ns lt64-asm.files
  (:require 
    [lt64-asm.symbols :as sym]
    [clojure.edn :as edn]))

;; Load ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-program
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
  [file]
  (if (and (sym/lt64-prog? file)
           (sym/static? (second file))
           (sym/main? (nth file 2)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm file"))))

(defn lt64-module
  [file]
  (if (and (sym/lt64-mod? file)
           (every? #(or (sym/proc? %) (sym/include? %))
                   (rest file)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm module"))))

;; Include and expand proc section ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare expand)

(defn include
  [[_ filename]]
  (trampoline
    expand
    (->> filename
         slurp
         edn/read-string
         lt64-module)))

(defn expand
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

;;; C file creation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn wrap-prog
  [program-bytes]
  (str "size_t prog_length() { return " (count program-bytes) ";  }\n"
       "void set_program(WORD* mem, size_t length) {\n"
       "  char program[] = { "
       (clojure.string/join ", " program-bytes)
       " };\n"
       "  memcpy(mem, program, length);\n"
       "}\n"))

(defn create-standalone-cfile
  [program-bytes path]
  (spit path
        (str (slurp "resources/lt64.c")
             (wrap-prog program-bytes))))

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(get-program "test/lt64_asm/new_test.lta")
(include '(include "test/lt64_asm/test_mod1.lta"))

;
),
