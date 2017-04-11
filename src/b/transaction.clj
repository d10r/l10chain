(ns b.transaction
  (:require [b.crypto :as crypto])
  (:require [clojure.string :as string]))

; TODO: Add nonce (needed to avoid transaction replay)
(defrecord Transaction [^String signature ^String sender ^String receiver value])

(defn merkle-hash [txns]
  "Calculates the hash of a merkle tree of the given transactions.
  The tree is left aligned."
  (if (empty? txns)
    nil
    ; h: height of the binary tree. Calculated as the log2 of the nr of transactions, rounded up
    (let [h (int (Math/ceil (/ (Math/log (count txns)) (Math/log 2))))]
      (if (= (count txns) 1)
        ; we've reached a leaf
        (crypto/hashx (str (into {} (first txns))))
        ; else descend to the next level of the tree by splitting up the txns we carry
        (let [split-point (int (Math/pow 2 (dec h)))
              tuple (split-at split-point txns)]
          (crypto/hashx (string/join "_" (list (merkle-hash (first tuple))
                                               (merkle-hash (second tuple))))))))))

(defn serialize-sig-data [m]
  (clojure.string/join "_" (vals m)))

(defn ->Transaction
  ; Parameters for constructing a new tx
  ([sender receiver value sig-fn]
   (let [sig-data (serialize-sig-data {:receiver receiver :value value})
         sig (sig-fn sig-data)]
     (Transaction. sig sender receiver value)))
  ; Params for creating a reward tx)
  ([receiver value]
   (Transaction. nil nil receiver value)))

(defn valid? [tx]
  "Checks if a transaction is valid. Double spend in the context of a specific chain needs to be checked elsewhere!"
  (let [sig-data (serialize-sig-data (select-keys tx [:receiver :value]))]
    (crypto/verifysig (:signature tx) sig-data (:sender tx))))

(defn higher-prio? [tx1 tx2]
  "Determines priority of transactions from mempool. At the moment it's first come, first serve. Easy to change."
  (let [ts1 (:ts-added (meta tx1))
        ts2 (:ts-added (meta tx2))]
    (< ts1 ts2)))