(ns lt64-asm.static
  (:require [lt64-asm.symbols :as sym]))

(def byte-bits 8)
(def word-size 2)
(def double-word-size 4)
(def default-scale 1000)

(defn num->word
  [number]
  (try
    (short number)
    (catch IllegalArgumentException e
      (throw (Exception. (str "Error: word literal out of range: " number))))))

(defn num->dword
  [number]
  (try
    (int number)
    (catch IllegalArgumentException e
      (throw (Exception. (str "Error: word literal out of range: " number))))))

(defn num->fixed-point
  [number scale]
  (let [scale-factor (or scale default-scale)]
    (try
      (int (* number scale-factor))
      (catch IllegalArgumentException e
        (throw (Exception.
               (str "Error: given scale makes fixed point literal too large "
                    "for double word. number: "
                    number
                    ", scale: "
                    scale-factor)))))))

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
    :word (get-bytes (num->word number) word-size)
    :dword (flip-dword-bytes
             (get-bytes (num->dword number) double-word-size))
    :fword (flip-dword-bytes
             (get-bytes (num->fixed-point number (:scale args))
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

(defn alloc->nums
  [num-elems elem-size byte-args args]
  (adjust
    (* num-elems elem-size)
    (* (count args) elem-size)
    (mapcat #(num->bytes % byte-args)
       (reverse args))))

(defmulti allocate
  (fn [static-instr]
    (first static-instr)))

(defmethod allocate :word
  [[_ label size & args]]
  {:bytes
   (alloc->nums size
                word-size
                {:kind :word}
                args)
   :words size})

(defmethod allocate :dword
  [[_ label size & args]]
  {:bytes
   (alloc->nums size
                double-word-size
                {:kind :dword}
                args)
   :words (* size 2)})

(defmethod allocate :fword
  [[_ label size & args]]
  {:bytes
   (alloc->nums size
                double-word-size
                {:kind :fword :scale default-scale}
                args)
   :words (* size 2)})

(defmethod allocate :fword-sc
  [[_ label size scale & args]]
  {:bytes
   (alloc->nums size
                double-word-size
                {:kind :fword :scale scale}
                args)
   :words (* size 2)})

(defmethod allocate :str
  [[_ label arg]]
  (let [bytes_ (pad-zero 1 (reverse (map byte arg)))
        result (if (even? (count bytes_))
                 bytes_
                 (pad-zero 1 bytes_))]
    {:bytes result
     :words (/ (count result) 2)}))

(defmethod allocate :char
  [[_ label size arg]]
  (let [bytes_ (adjust size (count arg) (reverse (map byte arg)))
        result (if (even? size)
                 (if (= 0 (first bytes_))
                   bytes_
                   (pad-zero 1 (rest bytes_)))
                 (pad-zero 1 bytes_))]
    {:bytes result
     :words (/ (count result) 2)}))

(defmethod allocate :default
  [[kind & _]]
  (throw (Exception. (str "Error: invalid static data type: " kind))))

(defn set-label
  [label value labels]
  (if (get labels label)
    (throw (Exception. (str "Error: label has already been declared: " label)))
    (assoc labels label value)))

(defn process-static
  [static program-data]
  (if (empty? static)
    program-data
    (let [{:keys [bytes labels counter]} program-data
          instr (first static)
          instr-data (allocate instr)]
        (recur (rest static)
             {:bytes (concat (:bytes instr-data) bytes)
              :labels (set-label (second instr) counter labels)
              :counter (+ counter (:words instr-data))}))))

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

(num->bytes 0xffffffff {:kind :word})
(num->bytes 0x11aabbcc {:kind :dword})
(reverse (flip-dword-bytes '(4 3 2 1)))
;(3 4 1 2) is how it would be when the vm reads it highword lowword and
; each word is lowbyte highbyte.


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

