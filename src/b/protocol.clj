(ns b.protocol
  (:require [b.p2p :as p2p]
            [b.block :as b]
            [b.chain :as c]
            [b.transaction :as tx])
  (:require [clojure.tools.logging :as log])
  (:import (org.apache.log4j Logger Level)))

(.setLevel (Logger/getLogger (str *ns*)) Level/DEBUG)

; set by core on init
(def chref)
(def add-to-mempool)
(def update-chain)

(defn init [chain add-mempool-fn upd-chain-fn]
  (def chref chain)
  (def add-to-mempool add-mempool-fn)
  (def update-chain upd-chain-fn))

; the x is appended in order to avoid a name used in clojure.core. TODO: How's that supposed to be handled?
(defmulti sendx (fn [cmd data peer] cmd))

(defmethod sendx :default [cmd data peer]
  (p2p/sendmsg peer {cmd data}))

;(defmethod sendx :block [_ block peer])

;(defmethod sendx :blocks [_ blocks peer]
;  (p2p/sendmsg peer {:blocks blocks}))

(defmulti request (fn [cmd args peer]
                    cmd))

; This acts as a lock which avoids too many requests for getblocks in parallel. Set with timeout.
(def lock-getblocks (future ()))

(defmethod request :getblocks [_ args peer]
  (if (not (future-done? lock-getblocks))
    (log/debug "getblocks locked, skipping request")
    (do
      (log/debug "requesting blocks from peer")
      (p2p/sendmsg peer {:getblocks args})
      (def lock-getblocks (future (Thread/sleep 10000))))))

(defmethod request :default [cmd args peer]
  (log/warn "unrecognized command received: " cmd))


(defmulti broadcast (fn [cmd data] cmd))

(defmethod broadcast :default [cmd data]
  ; map creates a lazy seq, doesn't anything without doall
  (log/debug "broadcasting " cmd " to " (count @p2p/peers) " peers")
  (doall (map #(p2p/sendmsg % {cmd data}) @p2p/peers)))

;(defmethod broadcast :block [_ block]
;  (log/debug "broadcasting block " (b/short-block-hash block) " with height " (get-in block [:header :height]) " to " (count @p2p/connpeers) " peers")
;  (doall (map #(p2p/sendmsg % {:block block}) @p2p/connpeers)))


(defmulti relay (fn [cmd data] cmd))

(defmethod relay :default [cmd data])
  ; before this is activated, we need to manage a list tracking which message should already be known by which peer
  ;(log/debug "relaying " cmd)


(defmulti onreceive (fn [cmd data peer] cmd))

(defmethod onreceive :default [cmd data peer]
  (log/warn "unrecognized command received: " cmd))



(defmethod onreceive :block [_ data peer]
  ; [block (chain/map->Block data)] would leave the header empty
  ; TODO: The above statement may actually be wrong, there was another mistake
  (let [ch @chref
        ;blockheader (chain/map->Blockheader (:header data))
        bl (b/map->Block data) ; (:forger data) (:signature data) blockheader (:transactions data))
        h (b/height bl)
        sbh (b/short-block-hash bl)]
    (log/debug "received new block " sbh " with height " h)
    (if (not (= h (c/max-height ch)))
      (do
        (log/debug "doesn't match current hight " (c/max-height ch))
        (if (not (c/initialized? ch))
          (do
            (log/debug "We have nothing. Requesting sync...")
            (request :getblocks {:starth 0} peer))))
      (let [newch (c/append-or-replace-last ch bl)]
        (if newch
          (if (c/better? newch ch)
            (do
              (log/debug "adding block " sbh " to chain")
              (update-chain newch))
            (do
              (log/debug "block " sbh " didn't improve chain")))
          (do
            (log/debug "couldn't insert block " sbh ", requesting more")
            (request :getblocks {:starth (max 0 (- (c/max-height ch) 10))} peer)))))))

(defmethod onreceive :blocks [_ blocks peer]
  "Checks if received blocks can improve the local chain.
  TODO: as is it works only for complete chains.
  TODO: blocks not typed. Should i care?
  TODO: handle cases: empty local chain, no common ancestor."
  ; The JSON parser creates a vector. We want a list.
  (let [blocklist (into () (reverse (map #(b/map->Block %) blocks)))
        ch @chref
        mergedchain (if (not (c/genesis-block ch))
                      ; not sure if this is the most elegant way...
                      blocklist
                      (c/mergex ch blocklist))]
    (log/debug "received " (count blocklist) " blocks")
    (if (and
          (not (nil? mergedchain))
          (c/valid? mergedchain))
      (if (c/better? mergedchain ch)
        (do
          (log/info "updating local chain with better received one")
          (update-chain mergedchain)))
      (do
        (if (c/genesis-block blocklist)
          (log/info "rejecting full chain, giving up.")
          (let [starth (max 0 (- (c/max-height ch) (* 2 (count blocklist))))]
            (log/debug "requesting more blocks with starth " starth)
            (future-cancel lock-getblocks)
            (request :getblocks {:starth starth} peer)))))))

(defmethod onreceive :transaction [_ transaction peer]
  "Handles a received transaction."
  (log/debug "received transaction")
  (let [tx (tx/map->Transaction transaction)]
    (if (tx/valid? tx)
      (do
        (add-to-mempool tx)
        (relay :transaction tx))
      (log/debug "received invalid transaction"))))

(defmethod onreceive :getblocks [_ args peer]
  "Answers requests for blocks."
  (let [ch @chref
        starth (if (:starth args)
                 (:starth args)
                 0)
        endh (if (:endh args)
               (max (:endh args) (c/height ch))
               (c/height ch))]
    (log/debug "blocks requested. Args: " args)
    (sendx :blocks (c/blocks ch starth endh) peer)))
