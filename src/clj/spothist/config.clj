(ns spothist.config
  (:require
   [cprop.source :as source]))

(def env
  (source/from-env))
