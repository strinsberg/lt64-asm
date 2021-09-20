(ns lt64-asm.symbols
  (:require [clojure.java.io :as jio]))

;; Symbols and op codes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def CR 0x0A)  ;; Ascii value for linefeed

;; Map of op keywords to their hex code equivilent used by the VM
(def symbol-map
  {:halt           0x00

   ;; Word Stack Manip
   :push           0x01
   :pop            0x02
   :load           0x03
   :load-lb        0x0103
   :store          0x04
   :store-lb       0x0104

   :first          0x05
   :second         0x06
   :nth            0x07
   :swap           0x08
   :rot            0x09

   :rpush          0x0a
   :rpop           0x0b
   :rgrab          0x0c

   ;; Double Word Stack Manip
   :dpush          0x0d
   :dpop           0x0e
   :dload          0x0f
   :dload-lb       0x010f
   :dstore         0x10
   :dstore-lb      0x0110

   :dfirst         0x11
   :dsecond        0x12
   :dnth           0x13
   :dswap          0x14
   :drot           0x15

   :drpush         0x16
   :drpop          0x17
   :drgrab         0x18

   ;; Word Arithmetic
   :add            0x19
   :sub            0x1a
   :mult           0x1b
   :div            0x1c
   :mod            0x1d

   :eq             0x1e
   :lt             0x1f
   :gt             0x20

   :multu          0x21
   :divu           0x22
   :modu           0x23
   :ltu            0x24
   :gtu            0x25

   ;;; Word Bit Ops
   :sl             0x26
   :sr             0x27
   :and            0x28
   :or             0x29
   :not            0x2a

   ;; Double Word Arithmetic
   :dadd            0x2b
   :dsub            0x2c
   :dmult           0x2d
   :ddiv            0x2e
   :dmod            0x2f

   :deq             0x30
   :dlt             0x31
   :dgt             0x32

   :dmultu          0x33
   :ddivu           0x34
   :dmodu           0x35
   :dltu            0x36
   :dgtu            0x37

   ;;; Double Word Bit Ops
   :dsl             0x38
   :dsr             0x39
   :dand            0x3a
   :dor             0x3b
   :dnot            0x3c

   ;;; Movement
   :jump            0x3d
   :branch          0x3e
   :call            0x3f
   :ret             0x40

   ;;; Addresses
   :dsp             0x41
   :pc              0x42
   :bfp             0x43
   :fmp             0x44

   ;;; Write
   :wprn            0x45
   :dprn            0x46
   :wprnu           0x47
   :dprnu           0x48
   :fprn            0x49
   :fprnsc          0x4a

   :prnch           0x4b
   :prn             0x4c
   :prnln           0x4d
   :prnsp-unused    0x4e
   :prnmem          0x4f
   :prnmem-lb       0x014f

   ;;; Read
   :wread           0x50
   :dread           0x51
   :fread           0x52
   :freadsc         0x53
   :readch          0x54
   :read-unused     0x55
   :readln          0x56
   :readsp-unused   0x57

   ;;; Buffer and Chars
   :bufstore       0x58
   :bufload        0x59
   :high           0x5a
   :low            0x5b
   :unpack         0x5c
   :pack           0x5d

   ;;; Memory
   :mem-to-buf     0x005e
   :buf-to-mem     0x015e
   :str-to-buf     0x005f
   :str-to-mem     0x015f

   ;;; Fixed Point Arithmetic
   :fmult          0x60
   :fdiv           0x61
   :fmultsc        0x62
   :fdivsc         0x63

   ;; Late additions
   :prnpk          0x64
   :readch-buf     0x65
   :streq          0x66
   :memeq          0x67
   :eof?           0x68
   :reset-eof      0x69
   :BREAK          0x6a

   ;; Pseudo ops that will be replaced or signal an error
   :fpush          0xff
   :invalid        0xff})

;;; Builtin Macros ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def macro-map
  {:!inc           [:push 1 :add]
   :!dinc          [:dpush 1 :dadd]
   :!dec           [:push 1 :sub]
   :!ddec          [:dpush 1 :dsub]

   :!zero?         [:push 0 :eq]
   :!dzero?        [:dpush 0 :deq]
   :!pos?          [:push 0 :gt]
   :!dpos?         [:dpush 0 :dgt]
   :!neg?          [:push 0 :lt]
   :!dneg?         [:dpush 0 :dlt]

   :!not           [:push 0 :eq]   ;; logical not, just checks for 0
   :!dnot          [:dpush 0 :deq]

   :!->word        [:swap :pop]
   :!->dword       [:push 0 :swap]
   
   :!prn-nl        [:push 10 :prnch]
   :!eat-ch        [:readch :pop]

   ;; to use top of return stack as a loop counter in simple looping situations
   :!init-rcount   [:push 0 :rpush]
   :!inc-rcount    [:rpop :push 1 :add :rpush]
   :!end-rcount    [:rpop :pop]
   :!eq-rcount     [:rgrab :eq]
   :!add-rcount    [:rgrab :add]
   })

;;; Predicates ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lt64-prog?
  "Checks if a list is a valid lt64 assembly program."
  [file]
  (= (first file) 'lt64-asm-prog))

(defn lt64-mod?
  "Checks if a list is a valid lt64 assembly subroutine module"
  [file]
  (= (first file) 'lt64-asm-mod))

(defn static?
  "Checks if a list is the static protion of an lt64 assembly program."
  [instr]
  (= (first instr) 'static))

(defn main?
  "Checks if a list is the main protion of an lt64 assembly program."
  [instr]
  (= (first instr) 'main))

(defn include?
  "Checks if a list is an include directive of an lt64 assembly program."
  [instr]
  (= (first instr) 'include))

(defn proc?
  "Checks if a list is a subroutine directive of an lt64 assembly program."
  [proc]
  (= (first proc) 'proc))

(defn macro?
  "Checks if a list is a subroutine directive of an lt64 assembly program."
  [macro]
  (= (first macro) 'macro))

(defn label?
  "Checks if an op is a label op symbol"
  [op]
  (= :label op))

(defn dpush-op?
  "Checks if an op is one that will push a double word.
  I.e. has a double word argument following it in the instruction list."
  [op]
  (contains? #{:dpush :fpush} op))

(defn push-op?
  "Checks if an op is one that will push an following argument."
  [op]
  (or (= op :push)
      (dpush-op? op)))

(defn builtin-macro?
  [op]
  (contains? macro-map op))

;; Functions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-label
  "Sets a label in a given label map with the given value.
  Throws an Exception if the label already exist in the map. The main
  purpose for this function is to wrap associations for the label pass
  of assembly so that they will not allow duplicate label declarations."
  [label value labels]
  (if (get labels label)
    (throw (Exception. (str "Error: label has already been declared: " label)))
    (assoc labels label value)))

(defn get-label
  "Gets the associate value for label in a given label map.
  Throws an Exception if the label is not in the map. The main
  purpose for this function is to wrap map get for the label pass
  of assembly so that missing labels cannot be referenced in a program."
  [label labels]
  (if-let [value (get labels label)]
    value
    (throw
      (Exception. (str "Error: Label has not been declared: " label)))))

(defn op->code
  "Given an op symbol returns the associated op code from the symbol-map."
  [op]
  (if-let [code (op symbol-map)]
    code
    (throw (Exception.
             (str "Error: Invalid operation: " op)))))

(defn get-macro-ops
  [op]
  (if-let [ops (get macro-map op)]
    ops
    (throw (Exception.
             (str "Error: Not a macro: " op)))))

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(:print-str symbol-map)
(key->op :add)
(key->op :never)

(proc? '(proc some-proc :push 1))
(proc? '(main :push 3))

(label? :label)
(label? :push)

(println :!jkls)
(builtin-macro? :!inc)
(builtin-macro? :!notamacro)

(get-macro-ops :!inc)
(get-macro-ops :!ffffinc)
;
),
