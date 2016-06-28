(ns kale.platform)

(defn parse-int
  "Parse a string into a long.
   Throws `NumberFormatException` for an invalid integer."
  [s]
  (Long. s))
