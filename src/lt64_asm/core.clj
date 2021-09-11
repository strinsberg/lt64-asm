(ns lt64-asm.core
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.static :as stat]
            [lt64-asm.bytes :as b]
            [lt64-asm.program :as prog]
            [lt64-asm.files :as files]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as jio])
  (:gen-class))

;;; Command Line Arg Parsing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def cli-opts
  [["-o"
    "--output-path FILE_PATH"
    "Path to use for the assembled program"
    :default "a.ltb"]
   ["-h" "--help"]])

(defn help-text
  [text]
  (println "Usage: java -jar lt64-asm.jar FILE [OPTIONS]\n")
  (println "Assemble an lt64-asm program to run on the lieutenant-64 VM.")
  (println "If no output path is provided the binary will be named a.ltb")
  (println "A '.ltb' extension is not required, it is just added to make")
  (println "identification of lt64 binaries easier.\n")
  (println text)
  (println "\nExamples:")
  (println "  java -jar lt64-asm.jar <filename>")
  (println "  java -jar lt64-asm.jar <filename> -o my_prog.ltb"))

;;; Setup and Assemble A Program ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def initial-prog-data
  (let [words (b/initial-words)]
    {:bytes words
     :counter (count words)
     :labels {}}))

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

(defn assemble
  [file]
  (let [[static main & procs-and-includes] (files/lt64-program file)
        procs (files/expand procs-and-includes)]
    (->> initial-prog-data
         (stat/process-static static)
         (prog/first-pass main procs)
         (prog/second-pass main procs)
         setup-bytes)))

;;; Main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main
  ""
  [& args]
  (let [{:keys [options summary arguments errors]}
        (parse-opts args cli-opts)]
    (cond
      errors (do (println "Errors:")
                 (println (str "  " (clojure.string/join "\n  " errors)))
                 (println "\nRun with --help for usage and examples"))
      (:help options) (help-text summary)
      (empty? arguments) (println "Error: No input file given")
      :else (b/write-bytes (:output-path options)
                            (assemble (files/get-program (first arguments)))))))

;;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
                (asm (files/get-program
                     "test/lt64_asm/new_test.lta")))
(-main "test/lt64_asm/new_test.lta")

;
),
