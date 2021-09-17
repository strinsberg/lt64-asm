(ns lt64-asm.builtin-test
  (:require [clojure.test :refer :all]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :refer [file]]
            [lt64-asm.core :refer :all]
            [lt64-asm.files :refer :all]))

;; Helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn execute
  "Create a standalone program with the given ops list as main,
  compile, and run it. This is a little inefficient due to compiling a
  full VM instance for a few instructions so all tests for simple macros should
  be combined when possible."
  [& ops] 
  (create-standalone-cfile
    (assemble ['lt64-asm-prog '(static) (cons 'main ops) '(include "stdlib")])
    "test.c")
  (if (not (.exists (file "test.c")))
    "*** failed to assemble ***"
    (if (= 0 (:exit (sh "gcc" "test.c" "-o" "test.out")))
      (clojure.string/trim (:out (sh "./test.out")))
      "*** failed to compile ***")))

(defn clean-up
  "Remove the testing files created by setup."
  []
  (sh "rm" "-rf" "test.out" "test.c"))

(defn join-nl
  [& nums]
  (clojure.string/join "\n" (map str nums)))

;; Macro Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest inc-dec
  (is (= (join-nl 3 3 3 3)
         (execute :push 2 :!inc :wprn :!prn-nl
                  :dpush 2 :!dinc :dprn :!prn-nl
                  :push 4 :!dec :wprn :!prn-nl
                  :dpush 4 :!ddec :dprn)))
  (clean-up))

(deftest equals-zero
  (is (= (join-nl 1 0 1 0)
         (execute :push 0 :!zero? :wprn :!prn-nl
                  :push 2 :!zero? :wprn :!prn-nl
                  :dpush 0 :!dzero? :dprn :!prn-nl
                  :dpush 2 :!dzero? :dprn)))
  (clean-up))

(deftest is-positive
  (is (= (join-nl 0 0 1 0 0 1)
         (execute :push -2 :!pos? :wprn :!prn-nl
                  :push 0 :!pos? :wprn :!prn-nl
                  :push 2 :!pos? :wprn :!prn-nl
                  :dpush -2 :!dpos? :dprn :!prn-nl
                  :dpush 0 :!dpos? :dprn :!prn-nl
                  :dpush 2 :!dpos? :dprn)))
  (clean-up))

(deftest is-negative
  (is (= (join-nl 1 0 0 1 0 0)
         (execute :push -2 :!neg? :wprn :!prn-nl
                  :push 0 :!neg? :wprn :!prn-nl
                  :push 2 :!neg? :wprn :!prn-nl
                  :dpush -2 :!dneg? :dprn :!prn-nl
                  :dpush 0 :!dneg? :dprn :!prn-nl
                  :dpush 2 :!dneg? :dprn)))
  (clean-up))

(deftest size-conversion
  (is (= (join-nl 209 29)
         (execute :push 9 :push 2 :!->dword :wprn :wprn :wprn :!prn-nl
                  :push 9 :dpush 2 :!->word :wprn :wprn :!prn-nl)))
  (clean-up))

;; STL tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest odd-even
  (is (= (join-nl 1 0 0 1)
         (execute :push 9 :push 'std/odd? :call :wprn :!prn-nl
                  :push 8 :push 'std/odd? :call :wprn :!prn-nl
                  :push 9 :push 'std/even? :call :wprn :!prn-nl
                  :push 8 :push 'std/even? :call :wprn)))
  (clean-up))

;; Run Tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(run-tests 'lt64-asm.builtin-test)
