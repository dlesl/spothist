(ns spothist.crypto
  (:require [clojure.string :as str]
            [goog.object :as obj]
            [sodium]
            [pako]))

(set! *warn-on-infer* true)

;; keypair

(def key-len 32)

(defn generate-keypair []
  (let [kp (sodium/crypto_box_keypair)]
    {:public-key (obj/get kp "publicKey")
     :private-key (obj/get kp "privateKey")}))

;; note: this is only for integrity checking of the keypair, and
;; therefore does not need to be secret!
(def hash-key
  (js/Uint8Array.
   (array 89 203 19 105 75 36 51 69 158 65 198 72 23 107 52 66)))

(defn ^:private hash-keyparts
  [^js/Uint8Array public ^js/Uint8Array private]
  (let [conc (doto (js/Uint8Array. (* key-len 2))
               (.set public 0)
               (.set private key-len))]
    (sodium/crypto_shorthash conc hash-key)))

(defn encode-keypair
  "Convert to 'human-readable' <public>:<private>:<checksum> form."
  [keypair]
  (if keypair
    (let [keyparts (map keypair [:public-key :private-key])]
      (->> (concat keyparts [(apply hash-keyparts keyparts)])
           (map sodium/to_base64)
           (str/join ":")))
    ""))

(defn ^:private abs-equal
  "Compares js Uint8Arrays. Not performant!"
  [& abs]
  (->> abs
       (map js/Array.from)
       (map js->clj)
       (apply =)))

(defn parse-keypair
  "Parse a <public>:<private>:<checksum> keypair string, returning nil if invalid"
  [key-as-string]
  (try
    (let [parts (map sodium/from_base64 (str/split key-as-string ":"))]
      (when (and (= 3 (count parts))
                 (= (map #(.-length ^js/Uint8Array %) parts) [32 32 8])
                 (abs-equal (last parts) (apply hash-keyparts (butlast parts))))
        (zipmap [:public-key :private-key] (butlast parts))))
    (catch js/Error _ nil)))

;; payload decryption and parsing

(defn ^:private decrypt [keypair payload]
  (js/sodium.crypto_box_seal_open
   (js/Uint8Array. payload)
   (:public-key keypair)
   (:private-key keypair)))

(defn ^:private parse [data]
  (as-> data %
    (pako/ungzip %)
    (.decode (js/TextDecoder.) %)
    (js/JSON.parse %) ;; for performance reasons we don't js->clj this
    ))

(defn decode [payloads keypair]
  (->> payloads
       (map :payload)
       (map (partial decrypt keypair))
       (mapcat parse)))
