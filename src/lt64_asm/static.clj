(ns lt64-asm.static
  (:require 
    [lt64-asm.bytes :as b]
    [lt64-asm.numbers :as nums]
    [lt64-asm.symbols :as sym]))

;;; Allocation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn alloc->nums
  "Creates a list of bytes large enough to hold the bytes for the number of
  elements desired.
  The total bytes is num-elements * elem-size. The byte args are those
  required to convert a number to bytes, most likely {:kind <wordtype>}.
  The args are a list of values to store from the beginning of the
  allocated list. If the args are too short, or there are none, the remaining
  memory will be 0s. If there are too many args extras will be discarded.
  Retruns the bytes in reverse order to what the VM will read."
  [num-elems elem-size byte-args args]
  (b/adjust
    (* num-elems elem-size)
    (* (count args) elem-size)
    (mapcat #(b/num->bytes % byte-args)
       (reverse args))))

;; Allocate methods
(defmulti allocate
  "Allocate a list of bytes for the static instruction.
  Switches on the type of the instruction to give the right data size.
  All data will be zeroed where default arguments have not been provided.
  If the list of arguments is too long then extra elements will be discarded.
  For :str and :char the final byte or word will be zeroed to null terminate
  it like a C string. The size of :str depends on the string given + the
  space for null terminating. All character based memory is sized to the number
  of words needed to fit packed characters (2 per word)."
  (fn [static-instr]
    (first static-instr)))

; Allocate numbers
(defmethod allocate :word
  [[_ label size & args]]
  {:bytes
   (alloc->nums size
                b/word-size
                {:kind :word}
                args)
   :words size})

(defmethod allocate :dword
  [[_ label size & args]]
  {:bytes
   (alloc->nums size
                b/double-word-size
                {:kind :dword}
                args)
   :words (* size 2)})

(defmethod allocate :fword
  [[_ label size & args]]
  {:bytes
   (alloc->nums size
                b/double-word-size
                {:kind :fword :scale nums/default-scale}
                args)
   :words (* size 2)})

(defmethod allocate :fword-sc
  [[_ label size scale & args]]
  {:bytes
   (alloc->nums size
                b/double-word-size
                {:kind :fword :scale scale}
                args)
   :words (* size 2)})

; Allocate Characters
(defmethod allocate :str
  [[_ label arg]]
  (let [bytes_ (b/pad-zero 1 (reverse (map byte arg)))
        result (if (even? (count bytes_))
                 bytes_
                 (b/pad-zero 1 bytes_))]
    {:bytes result
     :words (/ (count result) 2)}))

(defmethod allocate :char
  [[_ label size arg]]
  (let [bytes_ (b/adjust size (count arg) (reverse (map byte arg)))
        result (if (even? size)
                 (if (= 0 (first bytes_))
                   bytes_
                   (b/pad-zero 1 (rest bytes_)))
                 (b/pad-zero 1 bytes_))]
    {:bytes result
     :words (/ (count result) 2)}))

; Unknown Allocation Type
(defmethod allocate :default
  [[kind & _]]
  (throw (Exception. (str "Error: invalid static data type: " kind))))

;;; Static Processing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn process-all
  "Given a list of allocation instructions add the allocated byte lists to the
  :bytes field of the given program-data and return it.
  The resulting bytes will be in reverse order from how they were declared.
  The program counter will also be updated to the position right after the
  last static allocated word."
  [instructions program-data]
  (if (empty? instructions)
    program-data
    (let [{:keys [bytes labels counter user-macros]} program-data
          instr (first instructions)
          instr-data (allocate instr)]
        (recur (rest instructions)
             {:bytes (concat (:bytes instr-data) bytes)
              :labels (sym/set-label (second instr) counter labels)
              :counter (+ counter (:words instr-data))
              :user-macros user-macros}))))

(defn set-prog-start
  "Set the starting address to the current counter value of program-data
  and return the updated data."
  [program-data]
  (assoc program-data
         :start-address (:counter program-data)))

(defn process-static
  "Process the static portion of a program and return updated program data
  with the static bytes and updated program counter."
  [static program-data]
  (set-prog-start
    (process-all (rest static) program-data)))

;;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment
(def test-data {:counter (count sym/initial-bytes) :bytes '() :labels {}})
(def test-static
  '(static
     (:str Str "Hello, World")
     (:str OddStr "Hello, World!")
     (:char Chars 15 "Hello, World")
     (:word A 10 -2 -1 0 1 2)
     (:dword B 5 1 2)
     (:word C 2)
     (:fword D 2 1.23 4.45 8.238)
     (:fword-sc E 4 10 1.23 4.45)))

(allocate '(:word name 1 0x0001 0x0002 0x0003))
(allocate '(:word name 2 0x0001 0x0002 0x0003))
(allocate '(:word name 3 0x0001 0x0002 0x0003))
(allocate '(:word name 5 0x0001 0x0002 0x0003))

(allocate '(:dword name 5 0x11223344 0x0002 0x55667788))
(allocate '(:fword name 5 10.123 5.456 20.789))
(allocate '(:fword-sc name 5 100 10.123 5.456 20.789))

(process-static (rest test-static) test-data)

;
),

