(ns lt64-asm.static
  (:require [lt64-asm.symbols :as sym]))

(defn zero-list
  [size]
  (for [x (range size)] 0x00))

(defn get-byte
  [number pos]
  (bit-and (bit-shift-right number (* pos 8))
           0xff))

(defn dwords->bytes
  [nums]
  (mapcat #(list (get-byte % 1)
                 (get-byte % 0))
          nums))

(defn qwords->bytes
  [nums]
  (mapcat #(list (get-byte % 3)
                 (get-byte % 2)
                 (get-byte % 1)
                 (get-byte % 0))
          nums))

(defn get-inst-data
  [kind [s & args]]
  (case kind
    :str (conj (mapv byte s) 0x00)
    :char (conj
            (vec
              (take (dec s)
                    (concat
                      (map byte (first args))
                      (zero-list (- s (count (first args)))))))
              0x00)
    :word (vec (concat args
                       (zero-list (- s (count args)))))
    :word-d (vec (concat (dwords->bytes args)
                         (zero-list (- s (count args)))))
    :word-q (vec (concat (qwords->bytes args)
                         (zero-list (- s (count args)))))
    :word-f (vec (concat (qwords->bytes (mapv #(int (* 1000 %)) args))
                         (zero-list (- s (count args)))))
    [0x00]))  ;; if no args are given. maybe throw?

(defn alloc
  [inst prog-data]
  (let [{:keys [counter bytes labels]} prog-data
        kind (first inst)
        name_ (keyword (second inst))
        data (get-inst-data kind (drop 2 inst))]
    (assoc prog-data
           :labels (assoc labels name_ counter)
           :bytes (concat bytes data)
           :counter (+ counter (count data)))))
  

(comment

(keyword 'hello) 
(mapv byte "hello world")

(def test-data {:counter 3 :bytes sym/initial-bytes :labels {}})

(alloc '(:str hello "Hello, World!") test-data)
;  (47 255 255 72 101 108 108 111 44 32 87 111 114 108 100 33 0)
(alloc '(:char hello 20 "Hello, World!") test-data)
; (47 255 255 72 101 108 108 111 44 32 87 111 114 108 100 33 0 0 0 0 0 0 0)
(alloc '(:char hello 10 "Hello, World!") test-data)
; (47 255 255 72 101 108 108 111 44 32 87 111 0)

(alloc '(:word arr 10 0xaa 0xbb 0xcc) test-data)
(alloc '(:word arr 0xaa 0xbb 0xcc) test-data)
(alloc '(:word-d arr 5 0xaabb 0xccdd) test-data)
(alloc '(:word-q arr 5 0xaabbccdd) test-data)
(alloc '(:word-f arr 5 10.123) test-data)


),
