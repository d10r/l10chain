(ns b.core
  (:gen-class)
  (:require [b.p2p :as p2p]
            [b.block :as block]
            [b.chain :as chain]
            [b.crypto :as crypto]
            [b.forger :as forger]
            [b.transaction :as tx]
            [b.protocol :as protocol])
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [b.block :as b])
  (:import (org.apache.log4j Logger Level)))

(.setLevel (Logger/getLogger (str *ns*)) Level/INFO)

(def config {:default-p2p-port 20202
             :default-data-dir "node0"
             :blocktime 5
             :block-max-txns 500
             :genesis false
             :forge false
             :server false})

(defn exit [status msg]
  (println msg)
  (System/exit status))

; TODO: hand as argument to p2p callback
; Note that since it's stored as a linked list, fn first will always return the most recent block.
(def blockchain (atom nil))     ; dummy in order to avoid some special cases
;(def blockchain (atom (list)))     ; dummy in order to avoid some special cases
(def b blockchain) ; TODO: for convenience, remove

(def mykeys {})

; pool of pending transactions using Clojure's PersistentQueue (also see http://stackoverflow.com/q/43150765/261952)
;(def mempool (atom (clojure.lang.PersistentQueue/EMPTY)))
; Actually, queue is probably not so great in the context of concurrency. Back to set
; this creates a PersistentTreeSet
(def mempool (atom (sorted-set-by tx/higher-prio?)))

(defn- genesis-conf-meta[]
  "Chain config to added as metadata to the genesis block"
  (assoc (select-keys config [:blocktime :block-max-txns]) :validated true))

(defn- default-genesis-block []
  "Returns a genesis block with hardcoded forger address and signature. Timestamp and blockhash are dynamically created."
  (log/debug "Loading hardcoded genesis block")
  (with-meta (block/->Block "3059301306072a8648ce3d020106082a8648ce3d03010703420004a0141000ec4c9dd583c0fb8f406f98c19c226978b0f1d97124cf9a4550b38d802d72baca3c1dd4e46ca920f81ae5d0c2180b20010db15d68bd9f1a3904840489"
                            "30450221008da5cd654602a71fc0bd37d372065686f5397350aac0d2ace7b39ebb0053b686022050e43a1d1206b6994b00bd7861268f64cd304d2107ba0bef9b2238d5b29436e2"
                            0
                            "TODO: political or philosophical statement"
                            ""
                            []) (genesis-conf-meta)))

(defn- create-genesis-block []
  "Creates a genesis block for the loaded account."
  (log/debug "Creating genesis block for " (:own-address config))
  (with-meta (block/->Block (:own-address config)
                            (crypto/sign "0" (:privkey mykeys))
                            0
                            "TODO: political or philosophical statement"
                            ""
                            []) (genesis-conf-meta)))

; see http://clojure.github.io/tools.cli/index.html#clojure.tools.cli/parse-opts
(def cli-options
  [["" "--peers PEERLIST" "comma separated list of host[:port]"]
   ["-d" "--data-dir DATADIR" "data directory for this instance" :default (:default-data-dir config)]
   ["-g" "--genesis" "create a new genesis block. NOT YET WORKING FOR ARBITRARY ACCOUNTS (forger and signature hardcoded)!"]
   ["-f" "--forge" "start the forger process"]
   ["-s" "--server" "start a server listening for incoming connections"]
   ["-p" (str "--p2p-port PORT" "port for server socket. Default: " (:default-p2p-port config))
    :default (:default-p2p-port config)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-h" "--help"]])

(defn cli-parse-to-config! [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (str "Usage:\n" summary))
      errors (exit 1 (str "Invalid arguments: " errors "\nUsage:\n" summary)))
    (log/debug "\n***\nopts" options "\nargs" arguments "\nerrs" errors "\nsum" summary "\n***\n")
    (def config (assoc config
                  :p2p-port (:p2p-port options)
                  :peers (if (:peers options) ; is there a way to avoid this explicit conditional?
                           ; in order to convert to map with keys host, port, do something like
                           ; (into {} (map #(clojure.string/split % #":") (clojure.string/split peers #",")))
                           (clojure.string/split (:peers options) #",")
                           [])
                  :data-dir (:data-dir options)
                  :genesis (:genesis options)
                  :forge (:forge options)
                  :server (:server options)))))

(defn sign [data]
  {:pre [(not (nil? (:privkey mykeys)))]}
  "Signs given data with the loaded key."
  (crypto/sign data (:privkey mykeys)))

(defn update-chain
  "Callback for updating the blockchain with blocks received from other peers."
  ; the valid meta tag is used by the validator function to know where it can stop checking.
  ([newchain]
   (let [marked-head (with-meta (first newchain) {:valid true})
         newchain (conj (pop newchain) marked-head)]
     (reset! blockchain newchain)))
  ([newchain purgetxns]
   (update-chain newchain)
   (if (> (count purgetxns) 0)
     (reset! mempool (clojure.set/difference @mempool purgetxns)))))

(defn block-forged [b]
  "Callback for newly forged blocks."
  (let [newchain (chain/append-or-replace-last @blockchain b)]
    (if newchain
      (do
        (log/debug "forged block " (block/short-block-hash b) " can be added to the chain")
        (update-chain newchain (block/transactions b))
        (protocol/broadcast :block b))
      (log/debug "forged block " (block/short-block-hash b) " is rejected: \n" b))))

(defn add-to-mempool [tx]
  "Adds a tx to the local mempool. Acts as callback for protocol."
  (let [tx-with-ts (with-meta tx {:ts-added (System/currentTimeMillis)})]
    (reset! mempool (conj @mempool tx-with-ts))
    ; TODO: use the opportunity to purge ancient transactions
    ))

(defn send-tx [receiver value]
  "Creates and sends a transaction. It's broadcastet and added to local mempool."
  (let [tx (tx/->Transaction (:own-address config) receiver value sign)]
    (add-to-mempool tx)
    (protocol/broadcast :transaction tx)))

(defn -main [& args]
  (cli-parse-to-config! args)
  (def mykeys (crypto/loadkeys (str (:data-dir config) "/keys") ""))
  (def config (assoc config :own-address (:pubkey-hex mykeys)))
  (log/info "Keys loaded for address " (:own-address config))
  (if (:genesis config)
    (do
      (reset! blockchain (list (default-genesis-block)))
      (log/info "Default genesis block loaded. Block hash: " (b/block-hash (chain/genesis-block @blockchain))))
    (do
      (reset! blockchain (list (with-meta (block/->Block nil nil 0 nil nil []) {:blocktime 4})))
      (log/info "Empty chain initialized (will accept any genesis block it receives from peers)")))
  (protocol/init blockchain add-to-mempool update-chain)
  (log/info "protocol initialized")
  (if (:forge config)
    (do
      (forger/start {:address   (:own-address config)
                     :blocktime (:blocktime config)
                     :max-txns  (:block-max-txns config)}
                    blockchain mempool block-forged sign)
      (log/info "forger started")))
  (if (:server config)
    (do
      (p2p/start-server "::" (:p2p-port config) protocol/onreceive)
      (log/info "p2p node started on port " (:p2p-port config))))
  (if (not (empty? (:peers config)))
    (doall (map #(let [[host opt-port] (clojure.string/split % #":")
                       port (if (nil? opt-port)
                              (:default-p2p-port config)
                              opt-port)]
                    (log/info "connecting to " host port)
                    (p2p/connect (str "ws://" host) port protocol/onreceive))
                    ;(connect-to-peer host port))
                   (:peers config)))))

(defn stop[]
  "Idempotent shutdown function"
  (forger/stop)
  (p2p/disconnect)
  (p2p/stop-server))

  ;(forger/start (:own-address config) chain/blockchain (:blocktime config) chain/append-block)
  ;(log/info "Forger started")
  ;(p2p/start-server "::" (:p2p-port config) {})

; =========================
; Helper functions for the REPL
; =========================

(defn r []
  "Convenience function for repl: reload"
  (p2p/stop-server)
  (forger/stop)
  (refresh))

(defn l []
  "Convenience function for repl: init for testing"
  (-main)
  @(future (Thread/sleep 5000))
  (forger/stop)
  (def chain @b)
  (def address (:own-address config))
  (def a address)
  (def b (:signature (default-genesis-block)))
  (def chain1 chain)
  (def chain2 chain)
  (println "loaded"))


(defn rl []
  (r)
  (Thread/sleep 500) ; not sure why, but without it loading sometimes fails in strange ways
  (l))

(defn s []
  "start server"
  (p2p/start-server "::" 20202 protocol/onreceive))

(defn c []
  "connect client"
  (let [peer (p2p/connect "ws://localhost" 20202 protocol/onreceive)]
    (if (nil? (chain/genesis-block @blockchain))
      (protocol/request :getblocks {:starth 0} peer))))

(defn ms [] (-main) (s))

(defn f []
  (def forgerref (forger/start {:address   (:own-address config)
                                :blocktime (:blocktime config)
                                :max-txns  (:block-max-txns config)}
                               blockchain mempool block-forged sign)))

(defn sf [] (forger/stop))

(defn m [& args] (-main args))
;(-main)

(defn k [dir]
  (def mykeys (crypto/loadkeys (str dir "/keys") ""))
  (def config (assoc config :own-address (:pubkey-hex mykeys))))

(require '[cheshire.core :refer :all])

(def bl (parse-string (generate-string {:block (first @b)}) true))

(defn sy [] (protocol/request :getblocks {} (first @p2p/connpeers)))

(defn g [] (reset! blockchain (list (default-genesis-block))))

(defn d [] (p2p/disconnect))

(defn sub []
  (def chain1 (chain/blocks @b 0 4))
  (def chain2 (chain/blocks @b 3 5)))

(defn mp []
  (def tx1 (tx/->Transaction (:own-address config) "xyz" 2 sign))
  (def tx2 (tx/->Transaction (:own-address config) "abcd" 1 sign))
  (add-to-mempool tx1)
  (add-to-mempool tx2))

(defn dtx []
  (def tx1 (tx/->Transaction (:own-address config) "aaa" 1 sign))
  (def tx2 (tx/->Transaction (:own-address config) "bbb" 1 sign))
  (def tx3 (tx/->Transaction (:own-address config) "ccc" 1 sign)))
