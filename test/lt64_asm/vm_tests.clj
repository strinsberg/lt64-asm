(ns lt64-asm.vm-test
  (:require [clojure.test :refer :all]
            [clojure.java.shell :refer [sh]]
            [lt64-asm.core :refer :all]))

;;; Helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def prog-dir "test/lt64_asm/lta_programs/")

(defn setup
  "Assembler given file to a standalone .c file and compile it.

  Returns a function that takes a list of strings, where each string is
  a line of input for the program. When run this function returns the
  output of the program run on the input, but trimmed to remove trailing
  whitespace."
  [lta-file]
  (-main lta-file "-c" "test.c")
  (sh "gcc" "test.c" "-o" "test.out")
  (fn [input]
    (clojure.string/trim
      (:out
        (sh "./test.out"
            :in (clojure.string/join "\n" input))))))

(defn clean-up
  "Remove the testing files created by setup."
  []
  (sh "rm" "-rf" "test.out" "test.c"))

(defn str-range
  "Give a string of space separated numbers in the given range."
  [start end step]
  (clojure.string/join
    " "
    (range start end step)))

(defn str-range-nl
  "Returns a string of newline separated integers in the given range."
  [start end step]
  (clojure.string/join
    "\n"
    (range start end step)))

;;; Kattis Problems ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest coldputer
  (let [execute (setup (str prog-dir "coldputer.lta"))]
    (is (= "3" (execute ["5" "2 -3 8 -1 -29"]))
        "When passing some negatives")
    (is (= "1" (execute ["2" "-1000000 1000000"]))
        "With just min and max values")
    (is (= "50" (execute ["100" (str-range -1000000 1000000 20000)]))
        "With the max number of temps"))
  (clean-up))


(deftest stopwatch
  (let [execute (setup (str prog-dir "stopwatch.lta"))]
    (is (= "4" (execute ["2" "7" "11"]))
        "When the watch will stop")
    (is (= "still running" (execute ["3" "0" "11" "1000000"]))
        "When the watch will keep running")
    (is (= "500000" (execute ["1000" (str-range-nl 0 1000000 1000)]))
        "With the max number of temps"))
  (clean-up))


(deftest magic-trick
  (let [execute (setup (str prog-dir "magic_trick.lta"))]
    (is (= "1" (execute ["robust\n"]))
        "When we can tell for sure")
    (is (= "0" (execute ["icpc\n"]))
        "When we can't tell")
    (is (= "1" (execute ["abcdefghijklmnopqrstuvwxyz\n"]))
        "When we can tell for sure")
  (clean-up)))


;; RUN ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(run-tests 'lt64-asm.vm-test)

