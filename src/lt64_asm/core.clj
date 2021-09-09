(ns lt64-asm.core
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.static :as stat]
            [lt64-asm.bytes :as b]
            [lt64-asm.program :as prog]
            [lt64-asm.files :as files]
            [clojure.edn :as edn]
            [clojure.java.io :as jio])
  (:gen-class))

(def initial-prog-data
  {:bytes b/initial-words
   :counter (count b/initial-words)
   :labels {}})

(defn setup-bytes
  [program-data]
  (let [start (reverse
                (concat
                  (b/num->bytes
                    (:start-address program-data)
                    {:kind :word})
                  (b/op->bytes :push)))]
    (->> program-data
         :bytes
         flatten
         reverse
         (drop 4)
         (concat start)
         b/->bytes)))

(defn asm
  [file]
  (let [[static main & procs-and-includes] (files/lt64-program file)
        procs (files/expand procs-and-includes)]
    (->> initial-prog-data
         (stat/process-static static)
         (prog/first-pass main procs)
         (prog/second-pass main procs)
         setup-bytes)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(comment

(def test-prog (files/get-program "test/lt64_asm/new_test.lta"))
(identity test-prog)
(first test-prog)
(second test-prog)
(nth test-prog 2)

(->> initial-prog-data
     (stat/process-static (second test-prog))
     (prog/first-pass (nth test-prog 2) (drop 3 test-prog))
     (prog/second-pass (nth test-prog 2) (drop 3 test-prog))
     setup-bytes)

(b/write-bytes "test/lt64_asm/binfile.test"
                (asm test-prog))

;
),
