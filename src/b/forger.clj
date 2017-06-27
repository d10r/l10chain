(ns b.forger
  (:require [b.chain :as chain])
  (:require [b.crypto :as crypto])
  (:require [b.protocol :as protocol])
  (:require [clojure.string :refer [join]])
  (:require [clojure.tools.logging :as log]
            [b.block :as b])
  (:import (org.apache.log4j Logger Level)))

(.setLevel (Logger/getLogger (str *ns*)) Level/DEBUG)

(defn cheat-process-block [block _ chainref]
  (reset! chainref (conj @chainref block)))

(defn block-tx-list [ch mp max]
  "Creates a list of transactions to be included into the next block, respecting the limit."
  ; var log {:sender :value}
  ; var txns []
  ; txlog is needed as set in order to make contains? available
  (let [txlog (set (chain/txlog ch))]
    (loop [mp mp
           block-txns []]
      (log/debug "looping with " (count mp) " entries in mempool, " (count block-txns) " entries in tx list")
      ; if we emptied the mempool or reached the limit of allowed txns per block, return the current list
      (if (or (empty? mp)
              (> (count block-txns) max))
        block-txns
        ; else continue with next tx from pool...
        ; TODO: the following code is partly redundant with the validity check in chain module. May be improved.
        (let [tx (first mp)
              sender-balance (chain/balance ch (:sender tx))]
          (if (and
                ; make sure the tx isn't included in the current chain
                (not (contains? txlog tx))
                ; make sure the sender balance allows this tx
                (>= sender-balance (:value tx)))
            ; all right -> recur with the tx removed from the input set and added to the output vector
            (recur (disj mp tx) (conj block-txns tx))
            ; bad tx: only remove from input
            (do
              ; TODO: invalid txns should probably also be removed from mempool
              (log/debug "ignoring invalid tx " tx)
              (recur (disj mp tx) block-txns))))))))

; the forger relies on the caller to init crypto and self with a matching key / address pair
(def task nil)
(defn start [config chainref mempoolref block-callback sig-fn]
  "Starts the forger: will propose a block every blocktime seconds, based on the given chain"
  (def task (future
              (do
                ; if we have no genesis block, wait for it. (TODO: Is there a more elegant way to do this?)
                (while (not (chain/genesis-block @chainref))
                  (log/warn "can't forge without genesis block!")
                  (Thread/sleep 10000))

                (loop []
                  ; todo: for every height: block until reached
                  (if (>= (chain/height @chainref) (chain/max-height @chainref))
                    ; if the chain has fallen back, forge with 1 Hz, otherwise adjust frequency to blocktime
                    (Thread/sleep (* (- (:blocktime config) 1) 1000)))
                  (try
                    (let [ch @chainref
                          prevblock (chain/last-block ch)
                          height (inc (chain/height ch))    ;(inc (:height prevheader))
                          signature (sig-fn (join "_" [(b/beacon-sig prevblock) height]))
                          transactions (block-tx-list ch @mempoolref (:max-txns config))
                          block (b/->Block (:address config) signature height nil (b/block-hash prevblock) transactions)]
                      ;(process-block block block-callback chainref)
                      (if (not (empty? transactions))
                        (log/debug "block with transactions"))
                      (block-callback block)
                      ;(callback (chain/->Block address signature header []))
                      (print ".")
                      (Thread/sleep 1000))
                    (catch InterruptedException e
                      (log/info "forger shutting down")
                      (throw e))
                    (catch StackOverflowError e             ;Exception e
                      (log/warn "Exception" (.toString e))
                      (throw e)))
                  (recur))))))

(defn stop []
  (if (future? task) (future-cancel task)))

(defn running []
  (future? task))