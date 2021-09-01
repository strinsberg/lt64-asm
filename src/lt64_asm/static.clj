(ns lt64-asm.static
  (:require [lt64-asm.symbols :as sym]))

(def char-types #{:str :char})
(def number-types #{:word :word-d :word-q :word-f})

(defn get-byte
  [number pos]
  (bit-and (bit-shift-right number (* pos 8))
           0xff))

(defn num->bytes
  [x n]
  (for [i (range n)]
    (get-byte x i)))

(defn words->bytes
  [words n]
  (->> words
       (map #(num->bytes % n))
       reverse
       flatten))

(defn string-bytes
  [string]
  (reverse (map byte string)))

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

(defn static-chars
  [kind args]
  (case kind
    :str {:bytes (pad-zero 1 (->> args first reverse (map byte)))
          :count (-> args first count inc)}
    :char {:bytes
           (let [[size string] args
                length (count string)
                bytes_ (reverse (map byte string))]
            (if (< length size)
              (adjust size length bytes_)
              (adjust size
                      (dec size)
                      (drop (inc (- length size)) bytes_))))
           :count (first args)}
    (list 0x00)))

(defn static-nums
  [kind [size & args]]
  (let [len (count args)]
    (case kind
      :word {:bytes (adjust size len (reverse args))
             :count size}
      :word-d {:bytes (adjust (* 2 size) (* 2 len) (words->bytes args 2))
               :count size}
      :word-q {:bytes (adjust (* 4 size) (* 4 len) (words->bytes args 4))
               :count size}
      :word-f {:bytes
               (adjust (* 4 size) (* 4 len) (words->bytes
                                             (map #(int (* 1000 %)) args)
                                             4))
               :count size}
      (list 0x00))))  ;; invalid kind throw?

(defn get-static-data
  [kind args]
  (cond
    (kind char-types) (static-chars kind args)
    (kind number-types) (static-nums kind args)
    :else (list 0x00)))  ;; invalid throw?

(defn alloc
  [inst prog-data]
  (let [{:keys [counter bytes labels]} prog-data
        kind (first inst)
        name_ (keyword (second inst))
        data (get-static-data kind (drop 2 inst))]
    (assoc prog-data
           :labels (assoc labels name_ counter)
           :bytes (concat (:bytes data) bytes)
           :counter (+ counter (:count data)))))
  
(defn process-static
  [static]
  (let [prog-data
        (reduce #(alloc %2 %1)
                {:counter (count sym/initial-bytes)
                 :bytes '()
                 :labels {}}
                static)]
    (assoc prog-data
           :prog-start (:counter prog-data))))

(comment

(def test-data {:counter (count sym/initial-bytes) :bytes '() :labels {}})

(pad 0 5 (list 1 2 3))

(num->bytes 0xaabb 2)

(get-static-data :str (list "12345"))
(get-static-data :str (list "123456789"))
(get-static-data :char (list 3 "12345"))
(get-static-data :char (list 5 "12345")) ; (0 52 51 50 49)
(get-static-data :char (list 10 "12345")) ; (0 0 0 0 0 53 52 51 50 49)

(get-static-data :word (list 10 1 2 3 4 5)) ; (0 0 0 0 0 5 4 3 2 1)
(get-static-data :word-d (list 10 1 2 3 4 5)) ; (5 0 4 0 3 0 2 0 1 0)
(get-static-data :word-q (list 3 1 2 3 4 5)) ; (3 0 0 0 2 0 0 0 1 0 0 0)
(get-static-data :word-f (list 3 10.123)) ; (3 0 0 0 2 0 0 0 1 0 0 0)

(alloc '(:str hello "Hello, World!") test-data)
; (0 33 100 108 114 111 87 32 44 111 108 108 101 72)
(alloc '(:char hello 20 "Hello, World!") test-data)
; (0 0 0 0 0 0 0 33 100 108 114 111 87 32 44 111 108 108 101 72)
(alloc '(:char hello 10 "Hello, World!") test-data)
; (0 111 87 32 44 111 108 108 101 72)

(alloc '(:word arr 10 0xaa 0xbb 0xcc) test-data)
; (0 0 0 0 0 0 0 204 187 170)
(alloc '(:word-d arr 5 0xaabb 0xccdd) test-data)
; (0 0 0 0 0 0 221 204 187 170)
(alloc '(:word-q arr 5 0xaabbccdd) test-data)
; (0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 221 204 187 170)
(alloc '(:word-f arr 5 10.123) test-data)
; (0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 139 39 0 0)


(def static '(static
               (:str hello "hello")
               (:char world 6 "world")
               (:word-d addresses 5 0xaabb 0xccdd)))

(process-static (rest static))

),
