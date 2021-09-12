(ns lt64-asm.bytes
  (:require 
    [lt64-asm.numbers :as nums]
    [lt64-asm.symbols :as sym]
    [clojure.java.io :as jio]))

;;; Constants ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def byte-bits 8)         ;; The number of BITS in a byte
(def word-size 2)         ;; The number of BYTES in a word
(def double-word-size 4)  ;; The number of BYTES in a double word

(declare op->bytes)
(defn initial-words
  "Program start with a jump over the static data. At the start we don't know
  know where that is, so we use reserve space for a push and start address and
  add a jump"
  []
  [(op->bytes :jump)
   (op->bytes :invalid)
   (op->bytes :invalid)])

;;; Byte Manipulation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-bytes
  "Returns a list of the bytes in a given number from high to low.
  I.e. 0xaabb -> (0xaa 0xbb). Note this returns actual numbers and not hex."
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
  "Flips the bytes of a dword so that they will be in the right order when
  reversed.
  I.e. The VM expects 'cc dd  bb aa' for a double word. The bytes given from
  most byte functions are reversed. For double words 0xddccbbaa would be
  returned as (dd cc bb aa). To order them properly before they are revesered
  the must be (bb aa dd cc) so that reversed they are (cc dd aa bb) as
  expected by the vm."
  [dword]
  (concat (drop 2 dword) (take 2 dword)))

(defn num->bytes
  "Given a number returns the bytes of that number in reverse order of what
  the vm expects.
  Takes an argument map with :kind the type of number and :scale for the
  scaling factor for fixed point numbers (i.e. 1000 for 3 significant digits).
  Reverse order for words in high to low byte. For double and fixed point
  it is as returned by flip-dword-bytes.
  Throws an Exception if the :kind is invlaid."
  [number args]
  (case (:kind args)
    :word (get-bytes (nums/num->word number) word-size)
    :dword (flip-dword-bytes
             (get-bytes (nums/num->dword number) double-word-size))
    :fword (flip-dword-bytes
             (get-bytes (nums/num->fixed-point number (:scale args))
                        double-word-size))
    (throw (Exception.
             (str "Error: Invalid number type for num->bytes: " args)))))

(defn pad
  "Given a sequence of bytes cons the given value onto it the given number
  of times."
  [value times seq_]
  (loop [i times
         s seq_]
    (if (<= i 0)
      s
      (recur (dec i) (cons value s)))))

;; Pad a sequence with n zeros
(def pad-zero (partial pad 0x00))

(defn adjust
  "Adjusts the size of a given collection from its expected length to a new
  length, and pads with zeros if the new length is greater.
  So if a collection is larger than the new length it will have elements from
  the front dropped. If it is smaller it will have zeros added to the front.
  Length is passed so that the collection does not have to be counted. If this
  length does not match the collection size the result is undefined."
  [new-len len col]
  (cond
    (< new-len len) (drop (- len new-len) col)
    (> new-len len) (pad-zero (- new-len len) col)
    :else col))

(defn op->bytes
  "Given an op symbol will return a list of the bytes that represent its
  op code. These bytes will be reverse of what the vm expects."
  [op]
  (num->bytes (sym/op->code op) {:kind :word}))

(defn ->bytes
  "Turn a sequence of numbers representing bytes to an actual byte array.
  If numbers in the byte-seq are to large or small they will wrap around
  to fit in a byte."
  [byte-seq]
  (byte-array (map unchecked-byte byte-seq)))

(defn write-bytes
  "Given a filename and a byte array from ->bytes writes the bytes to the
  file as binary data."
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

(op->bytes :push)

;; Maintain byte seq with just the actual numbers and transform it to
;; a byte array before passing to write-bytes
(def bxs (list 0xaa 0xbb 0xcc 0xdd))
(write-bytes "test/lt64-asm/binfile.test" (->bytes bxs))

;
),
