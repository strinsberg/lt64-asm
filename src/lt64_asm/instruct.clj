(ns lt64-asm.instruct
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.static :as stat]))

(def unary-ops #{:print-str})

(defn get-addr
  [sym labels]
  ((keyword sym) labels))

(defn set-label
  [sym prog-data]
  (assoc (:labels prog-data)
         (keyword sym) (:counter prog-data)))

(defn expand-arg
  [arg prog-data]
  (cond
    (symbol? arg) {:program (cons (list arg :push-d) (:program prog-data))
                   :counter (+ (:counter prog-data) 3)}  ;; push op and dword addr
    :else prog-data))

(defn expand-unary-op
  [op args prog-data]
  (let [a (expand-arg (first args) prog-data)]
    (assoc prog-data
         :counter (inc (:counter a))
         :program (cons (list op) (:program a)))))

(defn expand-instr
  [[op & args] prog-data]
  (cond
    (= op :label) (assoc prog-data
                         :labels (set-label (first args) prog-data))
    (op unary-ops) (expand-unary-op op args prog-data)
    :else (assoc prog-data
                 :counter (inc (:counter prog-data))
                 :program (cons (list op) (:program prog-data)))))

(defn expand-all
  [instruct prog-data]
  (reduce #(expand-instr %2 %1)
          prog-data
          instruct))

(defn replace-label
  [instr labels]
  (map (fn [arg]
         (cond
           (symbol? arg) (stat/num->bytes (get-addr arg labels) 2)
           (keyword? arg) (sym/key->op arg)
           :else arg))
       instr))

(defn replace-all
  [prog-data]
  (assoc prog-data
         :program
         (flatten (map #(replace-label % (:labels prog-data))
                       (:program prog-data)))))

(comment

(def prog '())
(def prog-data
  {:program '()
   :counter 0
   :labels {:hello 3}})
(def instruct '(instruct
                 (:print-str hello)
                 (:halt)))

(expand-arg 'hello prog-data)
(expand-instr '(:print-str hello) prog-data)
(replace-all
  (expand-all (rest instruct) prog-data))

),
