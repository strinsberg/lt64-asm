(ns lt64-asm.symbols
  (:require [clojure.java.io :as jio]))

;; Symbols and op codes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def CR 0x0A)

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
   :bufload        0x58
   :bufstore       0x59
   :high           0x5a
   :low            0x5b
   :pack           0x5c
   :unpack         0x5d

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

   ;; Pseudo ops that will be replaced or signal an error
   :fpush          0xff
   :invalid        0xff})

;;; Predicates ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn lt64-prog?
  [file]
  (= (first file) 'lt64-asm-prog))

(defn lt64-mod?
  [file]
  (= (first file) 'lt64-asm-mod))

(defn static?
  [instr]
  (= (first instr) 'static))

(defn main?
  [instr]
  (= (first instr) 'main))

(defn include?
  [instr]
  (= (first instr) 'include))

(defn label?
  [op]
  (= :label op))

(defn proc?
  [proc]
  (= (first proc) 'proc))

(defn push-op?
  [op]
  (contains? #{:push :dpush :fpush} op))

;; Functions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-label
  [label value labels]
  (if (get labels label)
    (throw (Exception. (str "Error: label has already been declared: " label)))
    (assoc labels label value)))

(defn get-label
  [label labels]
  (if-let [value (get labels label)]
    value
    (throw
      (Exception. (str "Error: Label has not been declared: " label)))))

(defn op->code
  [key_]
  (if-let [op (key_ symbol-map)]
    op
    (throw (Exception.
             (str "Error: Invalid operation: " key_)))))

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(:print-str symbol-map)
(key->op :add)
(key->op :never)

(proc? '(proc some-proc :push 1))
(proc? '(main :push 3))

(label? :label)
(label? :push)
),
