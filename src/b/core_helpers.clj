(in-ns 'b.core)

(defn r []
  "stop threads and reload"
  (p2p/stop-server)
  (forger/stop)
  (refresh))

(defn l []
  "init and prepare for manual testing"
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
  "reload and init"
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

(defn ms []
  "init and start server"
  (-main)
  (s))

(defn f []
  "start forger"
  (def forgerref (forger/start {:address   (:own-address config)
                                :blocktime (:blocktime config)
                                :max-txns  (:block-max-txns config)}
                               blockchain mempool block-forged sign)))

(defn sf []
  "stop forger"
  (forger/stop))

(defn m [& args]
  "alias to main (yes, I am VERY lazy!)"
  (-main args))

(defn k [dir]
  "load keys"
  (def mykeys (crypto/loadkeys (str dir "/keys") ""))
  (def config (assoc config :own-address (:pubkey-hex mykeys))))

(defn sy []
  "request sync"
  (protocol/request :getblocks {} (first @p2p/peers)))

(defn g []
  "reset chain to genesis"
  (reset! blockchain (list (default-genesis-block))))

(defn d []
  "disconnect"
  (p2p/disconnect))

(defn mp []
  "add dummy transactions to the mempool"
  (def tx1 (tx/->Transaction (:own-address config) "xyz" 2 sign))
  (def tx2 (tx/->Transaction (:own-address config) "abcd" 1 sign))
  (add-to-mempool tx1)
  (add-to-mempool tx2))

(defn dtx []
  "define dummy transactions"
  (def tx1 (tx/->Transaction (:own-address config) "aaa" 1 sign))
  (def tx2 (tx/->Transaction (:own-address config) "bbb" 1 sign))
  (def tx3 (tx/->Transaction (:own-address config) "ccc" 1 sign)))