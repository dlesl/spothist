(ns spothist.payload
  (:require [octet.core :as buf]
            [octet.spec :as spec]
            [octet.buffer :as buffer]))

;; Modified version of octet.core/string*
(def ^{:doc "Arbitrary length bytes type spec."}
  bytes*
  (reify
    #?@(:clj
        [clojure.lang.IFn
         (invoke [s] s)]
        :cljs
        [cljs.core/IFn
         (-invoke [s] s)])

    spec/ISpecDynamicSize
    (size* [_ data]
      (+ 4 (count data)))

    spec/ISpec
    (read [_ buff pos]
      (let [datasize (buffer/read-int buff pos)
            data (buffer/read-bytes buff (+ pos 4) datasize)]
        [(+ datasize 4) data]))

    (write [_ buff pos value]
      (let [input value
            length (count input)]
        (buffer/write-int buff pos length)
        (buffer/write-bytes buff (+ pos 4) length input)
        (+ length 4)))))

(def record-spec (buf/spec
                  :added_at buf/string*
                  :payload bytes*))

(def records-spec (buf/vector* record-spec))

(defn write-records [records]
  (buf/into records-spec records))

(defn read-records [buffer]
  (buf/read buffer records-spec))
