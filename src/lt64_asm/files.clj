(ns lt64-asm.files
  (:require [clojure.edn :as edn]))

;; program predicates ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lt64-prog?
  [file]
  (= (first file) 'lt64-asm-prog))

(defn lt64-mod?
  [file]
  (= (first file) 'lt64-asm-mod))

(defn static?
  [instr]
  (= (first instr) 'static))

(defn main?
  [instr]
  (= (first instr) 'main))

(defn include?
  [instr]
  (= (first instr) 'include))

(defn proc?
  [instr]
  (= (first instr) 'proc))

;; file type identifiers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lt64-program
  [file]
  (if (and (lt64-prog? file)
           (static? (second file))
           (main? (nth file 2)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm file"))))

(defn lt64-module
  [file]
  (if (and (lt64-mod? file)
           (every? #(or (proc? %) (include? %))
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
    (mapcat #(if (include? %)
               (trampoline include %)
               (list %))
            procs-and-includes)
    (catch StackOverflowError _
      (throw (Exception.
               (str "Error: Stack overflow while expanding includes. "
                    "Most likely there are circular dependencies."))))))

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(include '(include "test/lt64_asm/test_mod1.lta"))

;
),
