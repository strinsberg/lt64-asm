(ns lt64-asm.program
  (:require [lt64-asm.symbols :as sym]
            [clojure.edn :as edn]))

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

(defn lt64-program
  [file]
  (if (and (= (first file) 'lt64-asm-prog)
           (static? (second file))
           (main? (nth file 2)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm file"))))

(defn lt64-module
  [file]
  (if (and (= (first file) 'lt64-asm-mod)
           (every? #(or (proc? %) (include? %))
                   (rest file)))
    (rest file)
    (throw
      (Exception. "Error: Not a valid lt64-asm module"))))

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

(defn first-pass
  [main procs program-data]
  )

(defn second-pass
  [main procs program-data]
  )

(comment
  
(every? even? [2 6 4 8])
(flatten [[1 2][3 4]])

(include '(include "test/lt64_asm/test_mod1.lta"))

;
),
