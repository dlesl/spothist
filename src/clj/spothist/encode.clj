(ns spothist.encode
  (:require [cheshire.core :refer [generate-stream]]
            [clojure.java.io :refer [writer]]
            [caesium.crypto.box :refer [box-seal]])
  (:import java.util.zip.GZIPOutputStream
           java.io.ByteArrayOutputStream))

(defn- to-json-gzip [data]
  (with-open [b (ByteArrayOutputStream.)
              g (GZIPOutputStream. b)]
    (generate-stream data (writer g))
    (.flush g)
    (.close g)
    (.toByteArray b)))

(defn encode [public-key data]
  (-> data
      to-json-gzip
      (box-seal public-key)))
