(ns lt64-asm.files
  (:require 
    [lt64-asm.symbols :as sym]
    [clojure.edn :as edn]))

;; file type identifiers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(include '(include "test/lt64_asm/test_mod1.lta"))

;
),
