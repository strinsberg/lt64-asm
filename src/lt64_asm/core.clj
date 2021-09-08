(ns lt64-asm.core
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.static :as stat]
            [lt64-asm.bytes :as b]
            [lt64-asm.program :as prog]
            [lt64-asm.instruct :as instr]
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

(defn concat-bytes
  [prog-data]
  (concat
    (:program prog-data)
    (:bytes prog-data)
    ;; this should probably be a function in sym incase the initial bytes change
    (concat (stat/num->bytes (:prog-start prog-data) 2)
          (drop 2 sym/initial-bytes))))

(defn assemble
  [prog]
  (->> prog
       get-static
       stat/process-static
       (instr/expand-all (get-instruct prog))
       instr/replace-all
       concat-bytes
       reverse))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(comment

(def test-prog (get-program "test/lt64_asm/test.lta"))
(get-static test-prog)
(get-instruct test-prog)
(get-instruct [])

(b/write-bytes "test/lt64_asm/binfile.test"
                 (b/->bytes (assemble test-prog)))


(def test-max (get-program "test/lt64_asm/max.lta"))
(b/write-bytes "test/lt64_asm/binfile.test"
                 (b/->bytes (assemble test-max)))

;; read the file
;; get the two sections
;; first pass on sections to build label map, import, and resolve psuedo inst
;;   return the expanded program and label map
;; second pass convert all instructions to bytes and fill in addresses etc.
;;   return a seq of bytes
;; write bytes to a file

(defn asm
  [file]
  (let [[static main & procs-and-includes] (lt64-file file)
        procs (files/expand procs-and-includes)]
    (->> static
         stat/process-static
         (prog/first-pass main procs)
         (prog/second-pass main procs)
         :bytes
         flatten
         reverse)))

),
