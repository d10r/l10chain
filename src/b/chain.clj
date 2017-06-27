(ns b.chain
  (:require [b.block :as b]
            [b.transaction :as t]
            [b.crypto :as crypto])
  (:require [clojure.tools.logging :as log])
  (:require [clojure.tools.logging :as log])
  (:import (org.apache.log4j Logger Level))
  (:import java.util.Date java.security.MessageDigest java.math.BigInteger))

(.setLevel (Logger/getLogger (str *ns*)) Level/DEBUG)

(defn genesis-block [ch]
  "Returns the genesis block."
  (let [first-bl (last ch)]
    (if (and (= (b/height first-bl) 0)
             (not (nil? (b/beacon-sig first-bl))))
      first-bl
      nil)))

(defn last-block [ch]
  "Returns the last (newest) block."
  (first ch))

(defn prev-block [ch]
  (second ch))

(defn blocktime [ch]
  "Reads the blocktime from chain config."
;  (let [conf (meta (genesis-block ch))]
;    (:blocktime conf)))
  4)

(defn max-txns-per-block [ch]
  (let [conf (meta (genesis-block ch))]
    (:block-max-txns conf)))

(def testing-time-override nil)
(defn max-height [ch]
  "Max height currently allowed based on the genesis block of the chain."
  (if (nil? (genesis-block ch))
    0
    (let [gen-ts (:timestamp (b/header (genesis-block ch)))
          cur-ts (if (not (nil? testing-time-override))
                   testing-time-override
                   (System/currentTimeMillis))]
      ; time difference between genesis and now divided by blocktime
      ; inc in order to include the currently open time window for a new block
      (quot (quot (- cur-ts gen-ts) 1000) (blocktime ch)))))

(defn height [ch]
  "Height of the chain. If the chain lags behind, this may be lower then max-height."
  (b/height (last-block ch)))

(defn blocks-behind [ch]
  (- (max-height ch) (height ch)))

(defn txlog [ch]
  "Provides a contiguous view onto the transactions in the chain. Explicitly adds block reward as tx.
  In case of block reward, signature and sender are nil. Returns a lazy sequence."
  ; for every block, the forger gets one coin. TODO: make that configurable
  ; TODO: Rewrite this such that the order of transactions is preserved (reward and non-reward)
  (let [reward-txs (map #(let [{receiver :forger} %] (t/->Transaction receiver 1)) ch)
        other-txs (mapcat #(let [{txs :transactions} %] txs) ch)
        all-txs (concat reward-txs other-txs)]
    (into [] (remove empty? all-txs))))

(defn balance [ch addr]
  "Calculates the balance of an account by traversing the txlog and subtracting sent from received amount."
  (-
    (->> (txlog ch)
         (filter #(= (:receiver %) addr))
         (map #(let [{v :value} %] v))
         (reduce +))
    (->> (txlog ch)
         (filter #(= (:sender %) addr))
         (map #(let [{v :value} %] v))
         (reduce +))))

; TODO: delme
(defn beacon-distance [ch]
  "Returns the distance of the beacon from the optimum for the last block."
  (crypto/distance (b/forger (last-block ch)) (b/beacon-sig (prev-block ch))))

(defn beacon-quality [ch]
  "Returns a value between 0 and 1 where 0 stands for the worst possible and 1 for the best possible forger address
  (specific to this block)."
  (let [dist (crypto/distance (b/forger (last-block ch)) (b/beacon-sig (prev-block ch)))
        add-inv (- (crypto/max-distance) dist)]
    (/ add-inv (crypto/max-distance))))

(defn weight [ch]
  "Returns the cumulated weight (based on beacon quality) of the chain, subtracting the penalties for missing blocks.
  The penalties are such as if all missing blocks were present, but forged by the worst possible forger.
  The weight is thus a function of the given chain and the current time."
  (if (not (genesis-block ch))
    -1
    (if (= 0 (height ch))
      0
      (let [cum-w (loop [ch ch
                         w 0]
                    (if (= (last-block ch) (genesis-block ch))
                      w
                      (recur (pop ch) (+ w (beacon-quality ch)))))
            blk-beh (blocks-behind ch)
            cum-penalty 0;(* (crypto/max-distance) blk-beh)
            corr-w (- cum-w cum-penalty)]
        (log/debug "cum-w " cum-w ", blk-beh " blk-beh ", cum-penalty " cum-penalty " | corrected weight " corr-w)
        (max corr-w 0)))))

(defn better? [ch1 ch2]
  "Returns true if chain 1 weights more then chain 2."
  ;{:pre (= (first chain1) (first chain2))}                  ; allow comparison only for chains with same genesis block
  (> (weight ch1) (weight ch2)))

(defn blocks [ch starth endh]
  {:pre [(and (>= starth 0) (<= endh (height ch)) (>= endh starth))]}
  (filter #(>= (b/height %) starth)
          (filter #(<= (b/height %) endh)
                  ch)))

(defn mrca [ch1 ch2]
  "Get the most recent common anchestor of the two chains."
  (let [maxh (min (height ch1) (height ch2))
        cut-at (fn [ch h] (filter #(<= (b/height %) h) ch))]
    (try
      (loop [ch1 (cut-at ch1 maxh)
             ch2 (cut-at ch2 maxh)
             h maxh]
        (if (= (last-block ch1) (last-block ch2))
          (last-block ch1)
          (recur (rest ch1) (rest ch2) (dec h))))
      ; if no overlap, there's an Exception. TODO: should this be checked in a less lazy manner?
      (catch Exception e
        nil))))

(defn initialized? [ch]
  (not (or (nil? ch)
           (empty? ch)
           (= nil (:forger (genesis-block ch))))))

(defn mergex [ch subch]
  "Merge the given subchain into with chain."
  ; Get the most recent common anchestor, then cut both chains there (one below, one above), then concat them.
  (let [mrca (mrca ch subch)
        mrcah (:height (:header mrca))]
    (if (nil? mrca)
      nil
      (if (= mrcah (height subch))
        ch
        (let [lower-subchain (blocks ch 0 mrcah)
              upper-subchain (blocks subch (inc mrcah) (height subch))]
          ; Once again inverted order because of the element order in the linked list
          (into () (reverse (concat upper-subchain lower-subchain))))))))

;(defn sharedchainold [chain1 chain2]
;  "Returns the highest shared sub-chain / genesis to most recent common ancestor."
  ; This impl is concise, but slightly weird, because it converts the chain into a set for intersecting.
  ; Since it's not this functions responsibility to guarantee validity of a chain, that should be ok.
  ; TODO: does this perform well enough?
;  (let [mrcaheight (reduce max (map #(get-in % [:header :height]) (clojure.set/intersection (set chain1) (set chain2))))]
;    (filter #(<= (get-in % [:header :height]) mrcaheight) chain1))) ; doesn't matter which chain we pick here

(defn total-supply [ch]
  "Calculates the total supply from block rewards (transactions with empty sender."
  (->> (txlog ch)
       (filter #(= (:sender %) nil))
       (map #(let [{v :value} %] v))
       (reduce +)))


(defn allowed-transaction? [ch tx]
  "Checks if the given transaction is allowed on top of this chain: not a duplicate, sender balance sufficient."
  (let [sbal (balance ch (:sender tx))]
    (if (and
          (not (contains? txlog tx))
          (>= sbal (:value tx)))
      true
      false)))

(defn transactions-valid? [ch txns]
  "Checks if the given transactions are valid in the context of the given chain.
  Includes checking for duplicates and of sender balances.
  Note that ch should NOT include the block which includes the given set of transactions."

  ; helper functions for managing the spent map
  (defn get-spent [map addr]
    (if (get map addr)
      (get map addr)
      0))

  (defn add-spent [map addr value]
    (if (get map addr)
      (update map addr #(+ % value))
      (assoc map addr value)))

  (loop [txns txns
         ; spent keeps track of sender balances changed by the transactions. Avoids one type of double spend.
         ; Receiver balance changes are ignored. Thus funds becoming available inside the same block can't
         ; immediately be spent. Since this would depend on tx order, it's not worth the hassle.
         spent {}]
    (if (empty? txns)
      true
      (let [tx (first txns)
            s (:sender tx)
            sbal (- (balance ch (:sender tx)) (get-spent spent s))]
        (if (and (t/valid? tx)
                 (not (contains? (txlog ch) tx))
                 (>= sbal (:value tx)))
          ; good transaction! recording balance changes and continuing with next...
          (recur (pop txns) (add-spent spent s (:value tx)))
          (do
            (log/debug "bad transaction detected: " tx)
            false))))))


(defn valid? [ch]
  "Checks if the given chain is valid. Uses the valid meta marker to check only previously unchecked blocks."
  (if (nil? (genesis-block ch))
    false)
  (loop [ch ch]
    ; If we reached the end or found a block with valid meta tag set, the chain is valid
    (if (or (empty? ch)
            (:valid (meta (last-block ch))))
      true
      ; As soon as an invalid block is found, return false, else continue with next block
      (let [bl (first ch)
            pbl (second ch)
            txns (b/transactions bl)]
        ; TODO: also check if forger is allowed to forge
        (if (not (and (b/valid? bl pbl (max-height ch))
                      (transactions-valid? (pop ch) txns)))
          (do
            (log/debug "Block " (b/short-block-hash bl) " at height " (b/height bl) " is invalid!")
            false)
          (recur (pop ch)))))))

(defn append-or-replace-last [ch bl]
  "Appends the given block to the chain or replaces the last block (based on height).
  Returns the new chain or nil if it fails.
"
  (let [chain-h (height ch)
        block-h (b/height bl)
        diff-h (- block-h chain-h)]
    (let [nch (cond
                (= 1 diff-h) (conj ch bl)
                (= 0 diff-h) (conj (pop ch) bl))]
      (if (valid? nch)
          nch
          nil))))
