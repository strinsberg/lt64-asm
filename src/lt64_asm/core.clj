(ns lt64-asm.core
  (:require [lt64-asm.symbols :as sym]
            [clojure.edn :as edn]
            [clojure.java.io :as jio])
  (:gen-class))

(defn get-program
  [filename]
  ;; need useful error if file is not readable
  (let [contents (edn/read-string (slurp filename))]
    (if (= (first contents) 'lt64-asm)
      (rest contents)
      nil)))

(defn get-static
  [prog]
  (let [stat (first prog)]
    (if (= (first stat) 'static)
      (rest stat)
      nil)))

(defn get-instruct
  [prog]
  (let [inst (second prog)]
    (if (= (first inst) 'instruct)
      (rest inst)
      nil)))

(def program
  "Program start with a jump over the static data. At the start we don't know
  know where that is, so we will reserve 2 words for the address for now. The
  addres will default to jumping to the end of memory which will cause a
  program out of bound error."
  [(key->op :jump-im)
   (0xff)
   (0xff)]

(defn first-pass
  [prog]
  {:labels {}
   :program []})

(defn second-pass
  [keys [labels program]]
  (sym/->bytes 0x00))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(comment

(def test-prog (get-program "test/lt64_asm/test.lta"))
(get-static test-prog)
(get-instruct test-prog)
(get-instruct [])

;; need to test returns from these functions to make sure they are
;; sequences and they give useful feedback or errors

;; read the file
;; get the two sections
;; first pass on sections to build label map, import, and resolve psuedo inst
;;   return the expanded program and label map
;; second pass convert all instructions to bytes and fill in addresses etc.
;;   return a seq of bytes
;; write bytes to a file

;; will need functions for converting integers given into byte arrays.
;; same for strings.
;; these will be based on what kind of number the op is expecting.
;; should maybe also check and issue a warning if the given number is
;; large enough to overflow, which might be a little tricky given the
;; way bytes etc. are always signed.

),
