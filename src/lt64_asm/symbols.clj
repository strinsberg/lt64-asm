(ns lt64-asm.symbols
  (:require [clojure.java.io :as jio]))

;; Symbols and op codes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def symbol-map
  {:halt        0x00

   ;; WORDs
   :push        0x01
   :pop         0x02
   :get         0x03
   :load        0x04
   :store       0x05

   :add         0x06
   :sub         0x07
   :mult        0x08
   :div         0x09
   :div-u       0x0a

   :eq          0x0b
   :lt          0x0c
   :lt-u        0x0d
   :gt          0x0e
   :gt-u        0x0f

   ;; DWORDs
   :push-d      0x10
   :pop-d       0x11
   :get-d       0x12
   :load-d      0x13
   :store-d     0x14

   :add-d       0x15
   :sub-d       0x16
   :mult-d      0x17
   :div-d       0x18
   :div-du      0x19

   :eq-d        0x1a
   :lt-d        0x1b
   :lt-du       0x1c
   :gt-d        0x1d
   :gt-du       0x1e

   ;; QWORDs
   :push-q      0x1f
   :pop-q       0x20
   :get-q       0x21
   :load-q      0x22
   :store-q     0x23

   :add-q       0x24
   :sub-q       0x25
   :mult-q      0x26
   :div-q       0x27
   :div-qu      0x28

   :eq-q        0x29
   :lt-q        0x2a
   :lt-qu       0x2b
   :gt-q        0x2c
   :gt-qu       0x2d

   ;; Jumps
   :jump        0x2e
   :jump-im     0x2f
   :branch      0x30
   :call        0x31
   :ret         0x32

   ;; Builtin Addresses
   :sp          0x33
   :fp          0x34
   :pc          0x35
   :ra          0x36

   ;; Print
   :print       0x37
   :print-u     0x38
   :print-d     0x39
   :print-du    0x3a
   :print-q     0x3b
   :print-qu    0x3c
   :print-ch    0x3d
   :print-str   0x3e

   ;; Read
   :read        0x3f
   :read-d      0x40
   :read-q      0x41
   :read-str    0x42

   ;; Shifts
   :shift-l     0x43
   :shift-r     0x44
   :shift-ld    0x45
   :shift-rd    0x46
   :shift-lq    0x47
   :shift-rq    0x48

   ;; Logical Bitwise
   :and         0x49
   :and-d       0x4a
   :and-q       0x4b
   :or          0x4c
   :or-d        0x4d
   :or-q        0x4e
   :not         0x4f
   :not-d       0x50
   :not-q       0x51

   ;; Free Memory
   :prg         0x52
   :brk         0x53
   :brk-add     0x54
   :brk-drop    0x55

   ;; Fixed Point
   :add-f       0x56
   :sub-f       0x57
   :mult-f      0x58
   :div-f       0x59

   :eq-f        0x5a
   :lt-f        0x5b
   :gt-f        0x5c

   :print-f     0x5d
   :read-f      0x5e

   :invalid     0xff})

(defn key->op
  [key_]
  (if-let [op (key_ symbol-map)]
    op
    (:invalid symbol-map)))

;; Bytes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def WORD unchecked-byte)

(defn ->bytes
  [byte-seq]
  (byte-array (map WORD byte-seq)))

(defn write-bytes
  [filename bytes_]
    (with-open [out (jio/output-stream (jio/file filename))]
      (.write out bytes_)))

;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment

(:print-str symbol-map)
(key->op :add)
(key->op :never)

;; Maintain byte seq with just the actual numbers and transform it to
;; a byte array before passing to write-bytes
(def bxs (list 0xaa 0xbb 0xcc 0xdd))
(write-bytes "binfile.test" (->bytes bxs))
 


),
