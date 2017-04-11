(ns b.block
  (:require [b.crypto :as crypto]
            [b.transaction :as tx])
  (:import [b.transaction Transaction])
  (:require [clojure.string :refer [join]])
  (:require [clojure.tools.logging :as log])
  (:import (org.apache.log4j Logger Level)))

(.setLevel (Logger/getLogger (str *ns*)) Level/DEBUG)

(defrecord Header [^int height ^long timestamp ^String extradata ^String hash ^String prevhash ^String txhash])
(defrecord Block [^String forger ^String signature ^Header header ^Transaction transactions])

(defn- short-hash [h]
  (subs h 0 7))

(defn header [b]
  (:header b))

(defn block-hash [b]
  (:hash (header b)))

(defn short-block-hash [b]
  (short-hash (block-hash b)))

(defn prev-hash [b]
  (:prevhash (header b)))

(defn tx-hash [b]
  (:txhash (header b)))

(defn height [b]
  (:height (header b)))

(defn forger [b]
  (:forger b))

(defn transactions [b]
  (:transactions b))

(defn beacon-sig [b]
  (:signature b))

(defn block-sig [b]
  (:blocksig b))

(defn calc-blockhash [header]
  {:pre [(not (nil? header))]}
  (let [v (vals (select-keys header [:height :timestamp :extradata :prevhash :txhash]))]
    (crypto/hashx (clojure.string/join "_" v))))

(defn ->Header-upd [h bhash]
  {:pre [(not (nil? h))]}
  "Completes the given header h with blockhash and txhash."
  (->Header (:height h) (:timestamp h) (:extradata h) bhash (:prevhash h) (:txhash h)))

(defn ->Block [forger signature height extradata prevhash transactions]
  ; TODO: take signature function instead of signature
  "Create a new Block, auto calculating hash and timestamp."
  (let [txh (tx/merkle-hash transactions)
        preheader (->Header height (System/currentTimeMillis) extradata nil prevhash txh)
        bh (calc-blockhash preheader)
        header (->Header-upd preheader bh)]
    (Block. forger signature header transactions)))

; In order to instantiate an existing Block from map, use map->Block

(defn valid-beacon-sig? [sig forger prev-sig height]
  (crypto/verifysig sig (join "_" [prev-sig height]) forger))

(defn valid?
  "Checks if a block is valid. With one param given only block specific criteria are checked.
  If additionally the previous block and max height are given, validity against a chain can be checked."
  ([b]
   (and (>= (height b) 0)
        (= (block-hash b) (calc-blockhash (header b)))
        (= (tx-hash b) (tx/merkle-hash (transactions b)))))

  ([b pb max-height]
   (and (valid? b)
        ; no blocks from the future accepted (inc for currently open timeslot)
        (<= (height b) max-height)
        ; if there's no parent block, we need to stop here (yields true)
        (if (not (nil? pb))
          (and (valid-beacon-sig? (beacon-sig b) (forger b) (beacon-sig pb) (height b))
               (< (height pb) (height b))
               (= (prev-hash b) (block-hash pb)))
          true))))
