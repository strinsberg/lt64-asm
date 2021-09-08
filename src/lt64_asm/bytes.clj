(ns lt64-asm.bytes
  (:require 
    [lt64-asm.numbers :as nums]
    [lt64-asm.symbols :as sym]
    [clojure.java.io :as jio]))

;;; Constants ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def byte-bits 8)
(def word-size 2)
(def double-word-size 4)

(def initial-bytes
  "Program start with a jump over the static data. At the start we don't know
  know where that is, so we will reserve 2 words for the address for now. The
  addres will default to jumping to the end of memory which will cause a
  program out of bound error."
  [(sym/key->op :invalid)
   (sym/key->op :invalid)
   (sym/key->op :jump-im)])

;;; Byte Manipulation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-bytes
  "list of bytes from high byte to low byte"
  [number num-bytes]
  (loop [x number
         n num-bytes
         res '()]
    (if (= n 0)
      res
      (recur (bit-shift-right x byte-bits)
             (dec n)
             (cons (bit-and x 0xff) res)))))

(defn flip-dword-bytes
  [dword]
  (concat (drop 2 dword) (take 2 dword)))

(defn num->bytes
  [number args]
  (case (:kind args)
    :word (get-bytes (nums/num->word number) word-size)
    :dword (flip-dword-bytes
             (get-bytes (nums/num->dword number) double-word-size))
    :fword (flip-dword-bytes
             (get-bytes (nums/num->fixed-point number (:scale args))
                        double-word-size))
    nil))

(defn pad
  [value times seq_]
  (loop [i times
         s seq_]
    (if (<= i 0)
      s
      (recur (dec i) (cons value s)))))

(def pad-zero (partial pad 0x00))

(defn adjust
  [new-len len col]
  (cond
    (< new-len len) (drop (- len new-len) col)
    (> new-len len) (pad-zero (- new-len len) col)
    :else col))

(def WORD unchecked-byte)

(defn ->bytes
  [byte-seq]
  (byte-array (map WORD byte-seq)))

(defn write-bytes
  [filename bytes_]
    (with-open [out (jio/output-stream (jio/file filename))]
      (.write out bytes_)))

;;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment
  
(num->bytes 0xffffffff {:kind :word})
(num->bytes 0x11aabbcc {:kind :dword})
(reverse (flip-dword-bytes '(4 3 2 1)))
;(3 4 1 2) is how it would be when the vm reads it highword lowword and
; each word is lowbyte highbyte.

;; Maintain byte seq with just the actual numbers and transform it to
;; a byte array before passing to write-bytes
(def bxs (list 0xaa 0xbb 0xcc 0xdd))
(write-bytes "test/lt64-asm/binfile.test" (->bytes bxs))

;
),
