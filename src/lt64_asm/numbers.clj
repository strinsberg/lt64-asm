(ns lt64-asm.numbers)

;;; Constants ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def default-scale 1000)

;;; Number Conversion ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn num->word
  "Converts a number into a signed 16 bit value.
  Throws an exception if the number given is out of range. This is used
  to ensure literalls passed to static data allocations and push ops will not
  wrap around into unexpected values."
  [number]
  (try
    (short number)
    (catch IllegalArgumentException e
      (throw (Exception. (str "Error: word literal out of range: " number))))))

(defn num->dword
  "Converts a number into a signed 32 bit value.
  Throws an exception if the number given is out of range. This is used
  to ensure literalls passed to static data allocations and push ops will not
  wrap around into unexpected values."
  [number]
  (try
    (int number)
    (catch IllegalArgumentException e
      (throw (Exception. (str "Error: word literal out of range: " number))))))

(defn num->fixed-point
  "Converts a floating point number into a signed 32 bit value and scales it
  by the given scale.
  If the scale is nil it will be set to the default of 1000
  (3 significant digits).
  Throws an exception if scaling the given number will put it out of range of
  a 32 bit int. Note that the largest fixed point value is INT_MAX / scale."
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

