(ns spothist.util)

(defn enumerate [coll]
  (map-indexed vector coll))

(defn static-html [html]
  [:div {:dangerouslySetInnerHTML {:__html html}}])

