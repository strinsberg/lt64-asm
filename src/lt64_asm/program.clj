(ns lt64-asm.program
  (:require [lt64-asm.symbols :as sym]
            [clojure.edn :as edn]))

(defn label?
  [op]
  (= :label op))

(defn proc?
  [proc]
  (= (first proc) 'proc))

(defn get-label
  [{:keys [ops counter labels]}]
  (if (label? (first ops))
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
      (recur (get-label args)))))

(defn proc-label-accumulator
  [program-data proc]
   (if (proc? proc)
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
       (get-op-labels main)
       (get-proc-labels procs)))

(defn second-pass
  [main procs program-data]
  )

(comment
  
(def test-main
  '(main
    ;; Load A[0]
    :push A
    :load-a

    ;; Loop over nums
    :label loop

    ;; Load num at A[i]
    :push i
    :load-a
    :push A
    :add
    :load-a

    ;; call max function
    :call max

    ;; Check if we have run them all
    :push i
    :load-a
    :push 10
    :eq
    :branch end-loop
    
    ;; not finished increment i and loop
    :push i
    :call inc-addr
    :jump loop

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
    :branch second-larger
    :swap
    :label second-larger
    :pop
    :ret)
  
  (proc inc-addr
    :load-a
    :dup
    :push 1
    :add
    :store
    :ret)))

(def test-prog-data
  {:bytes '()
   :counter 11
   :labels {'A 0 'i 10}})

(proc? (first test-procs))
(proc? test-main)

(label? :label)
(label? :push)

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
  (first-pass (rest test-main) test-procs test-prog-data))
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

(second-pass (rest test-main) test-procs labelled-prog-data)

;
),
