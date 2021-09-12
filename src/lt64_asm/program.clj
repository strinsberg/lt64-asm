(ns lt64-asm.program
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.files :as files]
            [lt64-asm.bytes :as b]
            [clojure.edn :as edn]))

;;; First Pass ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-label-from-ops
  "Given first pass program data records any labels with the current
  counter value or increments the counter according to the op.
  Returns a new map which updates labels, drops the op and any arguments,
  and updates the counter."
  [{:keys [ops counter labels]}]
  (cond
    (sym/label? (first ops))
    {:ops (drop 2 ops)
     :labels (sym/set-label (second ops) counter labels)
     :counter counter}

    (sym/dpush-op? (first ops))
    {:ops (drop 2 ops)
     :labels labels
     :counter (+ 3 counter)}

    :else
    {:ops (rest ops)
     :labels labels
     :counter (inc counter)})) 

(defn get-op-labels
  "Given a list of ops and program data processes the ops for labels and
  returns updated program data with new labels added and the counter
  increased to the position after the final op."
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
  "Accumulator for reducing subroutines to find their labels.
  Takes program data and a subroutine directive and returns updated program
  data after processing the subroutine with get-op-labels."
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
  "Get label data for a list of subroutine directives and return updated
  program data."
  [procs program-data]
  (reduce proc-label-accumulator
          program-data
          procs))

(defn first-pass
  "Process all labels in the main program and all subroutine directives.
  Returns the updated program data with all all labels and accociated
  addresses along with a the counter pointing past the final instruction."
  [main procs program-data]
  (->> program-data
       (get-op-labels (rest main))
       (get-proc-labels procs)))

;;; Second Pass ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn replace-push
  "Replace a push op and its value with byte lists and cons them onto the
  :bytes member of program data and return it."
  [[op value] program-data]
  (let [out-op (if (= op :fpush) :dpush op)
        num-type (case op
                   :push :word
                   :dpush :dword
                   :fpush :fword
                   :word)]
    (->> (:bytes program-data)
         (cons (b/op->bytes out-op))
         (cons (b/num->bytes value {:kind num-type}))
         (assoc program-data :bytes))))

(defn replace-op
  "Replace an op symbol with the byte of its associated address and cons it
  onto :bytes member of program data and return it."
  [op program-data]
  (->> (:bytes program-data)
       (cons (b/op->bytes op))
       (assoc program-data :bytes)))

(defn replace-labels
  "Filters all labels declarations out of a list of program operations and
  replaces any used labels with their numeric addres value.
  Since the only place labels are used is with push ops to push the address
  onto the stack the numbers will get converted to bytes in the replace-ops
  pass from replace-push-op."
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
  "Cons the bytes for each op symbol given onto the :bytes member of
  program-data and return it."
  [ops program-data]
  (let [op (first ops)]
    (cond
      (empty? ops) program-data

      (sym/push-op? op)
      (recur (drop 2 ops) (replace-push (take 2 ops) program-data))

      (keyword? op)
      (recur (rest ops) (replace-op op program-data))

      :else (throw
              (Exception. (str "Error: Invalid operation: " op))))))

(defn second-pass
  "Replace all labels and op symbols with their bytes and cons them onto the
  :bytes member of program-data. Return the updated program data."
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
(replace-push '(:push 0xaa) test-prog-data)
(replace-push '(:dpush 0x00bbccdd) test-prog-data)
(replace-push '(:fpush 10.123) test-prog-data)
(replace-op :branch test-prog-data)

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
