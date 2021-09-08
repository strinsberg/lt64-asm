(ns lt64-asm.numbers)

;;; Constants ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def default-scale 1000)

;;; Number Conversion ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

