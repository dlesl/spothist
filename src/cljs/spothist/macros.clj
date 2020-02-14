(ns spothist.macros)

(defmacro static-slurp [file]
  (let [res (slurp file)]
    `~res))


