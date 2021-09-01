(ns lt64-asm.instruct
  (:require [lt64-asm.symbols :as sym]
            [lt64-asm.static :as stat]))

(def special-ops
  {:label 0 :print-cr 3
   :call 4 :ret 2 :jump-im 3 :branch 3
   :push 2 :push-d 3 :push-q 5 :push-f 5
   :get 2 :get-d 2 :get-q 2})

(def unary-ops
  #{:load :load-d :load-q

    :jump
    :print :print-ch :print-str})

(def binary-ops
  #{:store :store-d :store-q

    :add :add-d :add-q
    :eq :eq-d :eq-q
    :lt :lt-d :lt-q
    :lt-u :lt-du :lt-qu

    :call})

(defn get-addr
  [sym labels]
  (cond
    (not (keyword sym)) (throw (Exception. (str "Error: Cannot be a label: " sym)))
    ((keyword sym) labels) ((keyword sym) labels)
    :else (throw (Exception.  (str "Error: No address for label: " sym)))))

(defn set-label
  [sym prog-data]
  (assoc (:labels prog-data)
         (keyword sym) (:counter prog-data)))

(defn get-op-size
  [op]
  (cond
    (or (clojure.string/ends-with? (str op) "-d")
        (clojure.string/ends-with? (str op) "-du"))
    2

    (or (clojure.string/ends-with? (str op) "-q")
        (clojure.string/ends-with? (str op) "-qu"))
    4

    :else
    1))

(defn op->push
  [op]
  (->> (str op)
       (drop-while #(not= % \-))
       (apply str)
       (str "push")
       keyword))

(defn get-deref-label
  [deref-sym]
  (->> deref-sym
      str
      (drop 1)
      (apply str)
      symbol))

(defn deref-word?
  [sym]
  (-> sym
      str
      (clojure.string/starts-with? "$")))

(defn deref-dword?
  [sym]
  (-> sym
      str
      (clojure.string/starts-with? "+")))

(defn deref-qword?
  [sym]
  (-> sym
      str
      (clojure.string/starts-with? "*")))

(defn expand-deref
  [arg op prog-data]
  {:program (concat (list (list op)
                          (list (get-deref-label arg) :push-d))
                  (:program prog-data))
    :counter (+ (:counter prog-data) 4)})

(defn expand-num
  [arg op prog-data]
  (let [arg-size (get-op-size op)
        push-op (op->push op)]
    {:program (cons (list (stat/num->bytes arg arg-size)
                          push-op)
                    (:program prog-data))
     :counter (+ (:counter prog-data) (inc arg-size))}))

(defn expand-arg
  [arg op prog-data]
  (cond
    (deref-word? arg) (expand-deref arg :load prog-data)
    (deref-dword? arg) (expand-deref arg :load-d prog-data)
    (deref-qword? arg) (expand-deref arg :load-q prog-data)
    (symbol? arg) {:program (cons (list arg :push-d) (:program prog-data))
                   :counter (+ (:counter prog-data) 3)}  ;; push op and dword addr
    (number? arg) (expand-num arg op prog-data)
    :else prog-data))  ;; throw if invalid?

(defn expand-special
  [op args prog-data]
  (assoc prog-data
         :program (cons (reverse (cons op args))
                        (:program prog-data))
         :counter (+ (:counter prog-data)
                     (op special-ops))))

(defn expand-unary-op
  [op args prog-data]
  (let [a (expand-arg (first args) op prog-data)]
    (assoc prog-data
         :counter (inc (:counter a))
         :program (cons (list op) (:program a)))))

(defn expand-binary-op
  [op args prog-data]
  (cond
    (= 0 (count args))
    (assoc prog-data
           :counter (inc (:counter prog-data))
           :program (cons (list op) (:program prog-data)))

    (= 2 (count args))
    (let [a (expand-arg (first args) op prog-data)
          b (expand-arg (second args) op a)]
      (assoc prog-data
             :counter (inc (:counter b))
             :program (cons (list op) (:program b))))

    :else
    prog-data))  ;; should throw because number of args are wrong?

(defn push-op?
  [op]
  (op #{:push :push-d :push-q :push-f}))

(defn expand-push-op
  [op args prog-data]
  (let [arg (if (number? (first args))
              (stat/num->bytes (first args) (get-op-size op))
              (first args))]
    (assoc prog-data
         :program (cons (list arg op)
                        (:program prog-data))
         :counter (+ (:counter prog-data) (op special-ops)))))

(defn expand-instr
  [[op & args] prog-data]
  (cond
    (= op :label) (assoc prog-data
                         :labels (set-label (first args) prog-data))
    (= op :print-cr) (assoc prog-data
                            :program (concat (list (list :print-ch)
                                                   (list sym/CR :push))
                                             (:program prog-data))
                            :counter (+ (:counter prog-data) 3))
    (push-op? op) (expand-push-op op args prog-data)
    (op special-ops) (expand-special op args prog-data)
    (op unary-ops) (expand-unary-op op args prog-data)
    (op binary-ops) (expand-binary-op op args prog-data)
    :else (assoc prog-data
                 :counter (inc (:counter prog-data))
                 :program (cons (list op) (:program prog-data)))))

(defn expand-all
  [instruct prog-data]
  (reduce #(expand-instr %2 %1)
          prog-data
          instruct))

(defn replace-label
  [arg labels]
  (cond
    (keyword? arg) (sym/key->op arg)
    (symbol? arg) (stat/num->bytes (get-addr arg labels) 2)
    ;; can there be others? if there are handle them or throw an error?
    :else arg)) ;; numbers right now

(defn replace-all
  [prog-data]
  (assoc prog-data
         :program
         (flatten
           (map #(replace-label % (:labels prog-data))
                (flatten (:program prog-data))))))

(comment

(def prog '())
(def prog-data
  {:program '()
   :counter 0
   :labels {:hello 3}})
(def instruct '(instruct
                 (:print-str hello)
                 (:halt)))

(expand-arg 'hello :add prog-data)
(expand-instr '(:print-str hello) prog-data)
(replace-all
  (expand-all (rest instruct) prog-data))

(deref-word? '$symbol)
(deref-word? 'abc$symbol)
(deref-dword? '+symbol)
(deref-dword? '$symbol)
(deref-qword? '*symbol)

(get-deref-label '$hello)
(get-deref-label '+hello)
(get-deref-label '*hello)

(op->push :add-du)
(op->push :lt-q)

(expand-num 10 :add-d prog-data)

(expand-arg '+hello prog-data)
(expand-instr '(:load) prog-data)
(expand-instr '(:load label) prog-data)
(expand-instr '(:pop) prog-data)
(expand-instr '(:add-d +nums 1) prog-data)
(expand-instr '(:add-d nums +i) prog-data)
(expand-instr '(:add-q *nums *i) prog-data)
(expand-instr '(:add $nums 3) prog-data)
(expand-instr '(:lt nums i) prog-data)
(expand-instr '(:lt) prog-data)
(expand-instr '(:eq-d i 10) prog-data)
(expand-instr '(:store-d) prog-data)
(expand-instr '(:print) prog-data)
(expand-instr '(:halt) prog-data)
(expand-instr '(:jump) prog-data)

(expand-instr '(:print-cr) prog-data)
(expand-instr '(:branch label) prog-data)
(expand-instr '(:get 3) prog-data)
(expand-instr '(:call 3 label) prog-data)
(expand-instr '(:ret 3) prog-data)
(expand-instr '(:push 3) prog-data)
(expand-instr '(:push-d 3) prog-data)
(expand-instr '(:push-q 3) prog-data)
(expand-instr '(:push-d label) prog-data)

),
