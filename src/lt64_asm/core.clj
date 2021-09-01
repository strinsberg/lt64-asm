(ns lt64-asm.core
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.static :as stat]
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

(sym/write-bytes "test/lt64_asm/binfile.test"
             (sym/->bytes (assemble test-prog)))

;; read the file
;; get the two sections
;; first pass on sections to build label map, import, and resolve psuedo inst
;;   return the expanded program and label map
;; second pass convert all instructions to bytes and fill in addresses etc.
;;   return a seq of bytes
;; write bytes to a file



),
