(ns lt64-asm.program
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.files :as files]
            [lt64-asm.bytes :as b]
            [clojure.edn :as edn]))

;;; First Pass ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-label-from-ops
  [{:keys [ops counter labels]}]
  (if (sym/label? (first ops))
    {:ops (drop 2 ops)
     :labels (sym/set-label (second ops) counter labels)
     :counter counter}
    {:ops (rest ops)
     :labels labels
     :counter (inc counter)})) 

(defn get-op-labels
  [ops program-data]
  (loop [args {:ops ops
               :labels (:labels program-data)
               :counter (:counter program-data)}]
    (if (empty? (:ops args))
      (assoc program-data
             :labels (:labels args)
             :counter (:counter args))
      (recur (get-label-from-ops args)))))

(defn proc-label-accumulator
  [program-data proc]
   (if (sym/proc? proc)
     (get-op-labels
       (drop 2 proc)
       (assoc
         program-data
         :labels (sym/set-label (second proc)
                                (:counter program-data)
                                (:labels program-data))))
     (throw (Exception.
              (str "Error: Invalid procedure found after main: "
                   proc)))))
  
(defn get-proc-labels
  [procs program-data]
  (reduce proc-label-accumulator
          program-data
          procs))

(defn first-pass
  [main procs program-data]
  (->> program-data
       (get-op-labels (rest main))
       (get-proc-labels procs)))

;;; Second Pass ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn replace-wnum
  [[op value] program-data]
  (->> (:bytes program-data)
       (cons (b/op->bytes op))
       (cons (b/num->bytes value {:kind :word}))
       (assoc program-data :bytes)))

(defn replace-dnum
  [[op value] program-data]
  (->> (:bytes program-data)
       (cons (b/op->bytes op))
       (cons (b/num->bytes value {:kind :dword}))
       (assoc program-data :bytes)))

(defn replace-fnum
  [[op value] program-data]
  (->> (:bytes program-data)
       (cons (b/op->bytes :dpush))
       (cons (b/num->bytes value {:kind :fword}))
       (assoc program-data :bytes)))

(defn replace-label
  [op program-data]
  (->> (:bytes program-data)
       (cons (b/num->bytes
               (sym/get-label op (:labels program-data))
               {:kind :word}))
       (assoc program-data :bytes)))

(defn replace-op
  [op program-data]
  (->> (:bytes program-data)
       (cons (b/op->bytes op))
       (assoc program-data :bytes)))

(defn replace-labels
  [ops program-data]
  (cond
    (empty? ops) '()

    (sym/label? (first ops))
    (lazy-seq
      (replace-labels (drop 2 ops) program-data))

    (symbol? (first ops))
    (cons
      (sym/get-label (first ops) (:labels program-data))
      (lazy-seq
        (replace-labels (rest ops) program-data)))

    :else
    (cons
      (first ops)
      (lazy-seq
        (replace-labels (rest ops) program-data)))))

(defn replace-ops
  [ops program-data]
  (let [op (first ops)]
    (cond
      (empty? ops) program-data
      (sym/wnum-op? op) (recur (drop 2 ops) (replace-wnum (take 2 ops) program-data))
      (sym/dnum-op? op) (recur (drop 2 ops) (replace-dnum (take 2 ops) program-data))
      (= :fpush op) (recur (drop 2 ops) (replace-fnum (take 2 ops) program-data))
      (keyword? op) (recur (rest ops) (replace-op op program-data))
      :else (throw
              (Exception. (str "Error: Invalid operation: " op))))))

(defn second-pass
  [main procs program-data]
  (reduce #(-> (drop 2 %2)
               (replace-labels program-data)
               (replace-ops %1))
          (-> (rest main)
              (replace-labels program-data)
              (replace-ops program-data))
          procs))

;;; REPL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment
  
;;; Test Data
(def test-main
  '(main
    ;; Load A[0]
    :push A
    :load-lb

    ;; Loop over nums
    :label loop

    ;; Load num at A[i]
    :push i
    :load-lb
    :push A
    :add
    :load-lb

    ;; call max function
    :push max
    :call

    ;; Check if we have run them all
    :push i
    :load-lb
    :push 10
    :eq
    :push end-loop
    :branch
    
    ;; not finished increment i and loop
    :push i
    :push inc-addr
    :call
    :push loop
    :jump

    ;; finished
    :label end-loop
    :wprn
    :push 10
    :prnch
    :halt))

  ;; find max of two numbers on the stack
(def test-procs
  '((proc max
    :gt
    :push second-larger
    :branch
    :swap
    :label second-larger
    :pop
    :ret)
  
  (proc inc-addr
    :load-lb
    :first
    :push 1
    :add
    :store-lb
    :ret)))

(def test-prog-data
  {:bytes '()
   :counter 11
   :labels {'A 0 'i 10}})

;;; First Pass Tests
(get-label {:ops '(:label test-label :push 4)
            :counter 3
            :labels {}})
; {:ops (:push 4), :labels {test-label 3}, :counter 3}

(get-label {:ops '(:push 13 :label test-label :push 4)
            :counter 3
            :labels {}})
; {:ops (13 :label test-label :push 4), :labels {}, :counter 4}

(get-op-labels (rest test-main) test-prog-data)
; {:bytes (), :counter 42, :labels {A 0, i 10, loop 14, end-loop 37}}

(get-proc-labels test-procs test-prog-data)
; {:bytes (),
;  :counter 24,
;  :labels {A 0, i 10, max 11, second-larger 15, inc-addr 17}}

(def labelled-prog-data
  (first-pass test-main
              test-procs
              test-prog-data))
; {:bytes (),
;  :counter 55,
;  :labels
;  {A 0,
;   i 10,
;   loop 14,
;   end-loop 37,
;   max 42,
;   second-larger 46,
;   inc-addr 48}}

;;; Second Pass Tests
(replace-wnum '(:push 0xaa) test-prog-data)
(replace-dnum '(:dpush 0x00bbccdd) test-prog-data)
(replace-fnum '(:fpush 10.123) test-prog-data)
(replace-op :branch test-prog-data)
(replace-label 'i test-prog-data)

(rest test-main)
(def main-replaced-labels
  (replace-labels (rest test-main)
                  labelled-prog-data))

(replace-ops main-replaced-labels
             labelled-prog-data)

(second-pass test-main
             test-procs
             labelled-prog-data)

;
),
