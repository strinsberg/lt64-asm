(ns lt64-asm.stdlib)

(def stdlib-subroutines
  {'even? '(proc std/even? :push 2 :mod :push 0 :eq :ret)
   'odd? '(proc std/odd? :push 2 :mod :push 1 :eq :ret)})

(defn contains-all
  "Check to see if the stdlib contains all of the given subroutines."
  [procs]
  (loop [ps procs
         not-contained nil]
    (cond
      (empty? ps) not-contained
      (contains? stdlib-subroutines (first ps)) (recur (rest ps) not-contained)
      :else (recur (rest ps) (cons (first ps) not-contained)))))

(defn include-procs
  "Return a list of all the subroutines for the given symbols.
  Throws if any of the symbols are not in the stdlib."
  [procs]
  (let [invalid (contains-all procs)]
    (if (not invalid)
      (vals (select-keys stdlib-subroutines procs))
      (throw (Exception.
               (str "Error: Some subroutines are not a member of the stdlib: "
                    (clojure.string/join ", " invalid)))))))

(defn include-all
  "Return a list of all of the subroutines in the stdlib"
  []
  (vals stdlib-subroutines))

(comment
(contains-all ['even? 'odd?]) 
(include-procs ['even? 'over 'hello])
(include-procs ['even?]) 
;
),

