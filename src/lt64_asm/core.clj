(ns lt64-asm.core
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.static :as stat]
            [lt64-asm.bytes :as b]
            [lt64-asm.program :as prog]
            [lt64-asm.files :as files]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :as jio])
  (:gen-class))

;;; Command Line Arg Parsing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def cli-opts
  [["-o"
    "--output-path FILE_PATH"
    "Path to use for the assembled program"
    :default "a.ltb"]
   ["-c"
    "--cfile OUTPUT_PATH"
    (str "After assembly generates a standalone C file that can be compiled"
         " to an executable that does not depend on the VM directly."
         " Of course this is because the VM is included in the C file.\n"
         "C file will be named with given path with a .c extension."
         "I.e. -c some/path  ->  some/path.c")]
   ["-m"
    "--memdump-cfile OUTPUT_PATH"
    (str "Take a memory dump file with a map containing the program length "
         "and the numeric representation of each memory word and create "
         "a standalone cfile for it. This will bundle the program the same "
         "way that the -c option does, except it will not do any assembly "
         "because the input file is already a representation of an assembled "
         "program, but with the entire contents of memory rather than just "
         "assembled program operations.")]
   ["-h" "--help"]])

(defn help-text
  "Prints command line help text to stdout."
  [text]
  (println "Usage: java -jar lt64-asm.jar FILE [OPTIONS]\n")
  (println "Assemble an lt64-asm program to run on the lieutenant-64 VM.")
  (println "If no output path is provided the binary will be named 'a.ltb'.")
  (println "The '.ltb' extension is not required, it is only added to make")
  (println "identification of lt64 binaries easier.\n")
  (println text)
  (println "\nExamples:")
  (println "  java -jar lt64-asm.jar <filename>")
  (println "  java -jar lt64-asm.jar <filename> -o my_prog.ltb"))

;;; Setup and Assemble A Program ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Setup the initial program data with the instructions that are placed
;; before the static data
(def initial-prog-data
  (let [words (b/initial-words)]
    {:bytes words
     :counter (count words)
     :labels {}
     :user-macros {}}))

(defn setup-bytes
  "Given program data returns the bytes as a byte array in the correct order
  and format for the VM.
  This reverses and flattens the bytes returned from all the byte functions
  to get them in the right order. It also sets up the address for the initial
  jump to get past the static data and combines all bytes together. Finally
  returning them as a byte array."
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
  "Given a list representing an lt64-asm program return the assembled
  byte array."
  [file]
  (let [[static main & procs-and-includes] (files/lt64-program file)
        {:keys [procs data]} (files/expand-all procs-and-includes
                                               initial-prog-data)]
    (->> data
         (stat/process-static static)
         (prog/first-pass main procs)
         (prog/second-pass main procs)
         setup-bytes)))

(defn assemble-cfile
  [infile outfile]
  (try
    (files/create-standalone-cfile
      (assemble (files/get-program infile))
      outfile)
    (catch Exception e
      (binding [*out* *err*]
        (println)
        (println "*** Assembly Failed ***")
        (println (.getMessage e))))))

;;; Main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main
  "Process command line arguments and assemble the file given as the first
  argument.
  Produces either a binary file that can be run on the VM or a C file
  containing the VM and the assembled program as binary data. The C file
  can be compiled and run as a standalone program."
  [& args]
  (let [{:keys [options summary arguments errors]}
        (parse-opts args cli-opts)]
    (cond
      errors (do (println "Errors:")
                 (println (str "  " (clojure.string/join "\n  " errors)))
                 (println "\nRun with --help for usage and examples"))

      (:help options)
      (help-text summary)

      (empty? arguments)
      (println "Error: No input file given")

      (:cfile options)
      (assemble-cfile (first arguments)
                      (:cfile options))

      (:memdump-cfile options)
      (files/create-cfile-from-memdump (first arguments)
                                       (:memdump-cfile options))

      :else
      (b/write-bytes (:output-path options)
                     (assemble (files/get-program (first arguments)))))))

;;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(def test-prog (files/get-program "test/lt64_asm/stopwatch.lta"))
(identity test-prog)
(first test-prog)
(second test-prog)
(nth test-prog 2)

(def procs (files/expand (drop 3 test-prog)))
(->> initial-prog-data
     (stat/process-static (second test-prog))
     (prog/first-pass (nth test-prog 2) procs)
     (prog/second-pass (nth test-prog 2) procs)
     setup-bytes)

(assemble test-prog)

(-main "test/lt64_asm/lta_programs/coldputer.lta" "-c" "coldputer.c")
(-main "test/lt64_asm/lta_programs/stopwatch.lta" "-o" "stopwatch.ltb")

;
),
