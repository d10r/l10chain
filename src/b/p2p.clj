(ns b.p2p
  (:require [aleph.http :as http])
  (:require [manifold.stream :as stream])
  (:require [manifold.deferred :as deferred])
  (:require [cheshire.core :refer :all])
  (:require [clojure.tools.logging :as log])
  (:import (org.apache.log4j Logger Level)))

(.setLevel (Logger/getLogger (str *ns*)) Level/INFO)

; peers: list of connected nodes (stores the socket objects)
(def peers (atom #{}))  ; init with empty set
(def protocolhandler (atom nil))

(defn onreceivemsg [socket msg handler]
  (log/debug "got msg:" msg)
  ;(log/debug "socket: " socket)
  (let [parsedmsg (parse-string msg true) ; the last param converts map keys to symbols
        cmd (first (keys parsedmsg))]
    (log/debug "parsed cmd: " cmd " | parsed msg: " parsedmsg)
    (handler cmd (cmd parsedmsg) socket)))

(def http-server nil)
(defn stop-server
  ([] (if http-server (stop-server http-server))) ;no arg: stop the (last) server previously started by start-server
  ([handle]
   (println "stopping http server...")
   (.close handle)
   (def http-server nil)))

(defn- on-socket-closed [socket]
  (log/debug "closing socket (" (dec (count @peers)) " remaining)")
  (reset! peers (disj @peers socket)))

; setting high max since there's currently no pagination for blocks available
(def wsconfig {:max-frame-payload 0x7fffffff :max-frame-size 0x7fffffff})

(def server-sockets [])
(defn start-server [iface port handler]
  "Starts a http server which upgrades requests to websocket (didn't find a way to skip this intermediate step with aleph).
  Returns the socket.
  @TODO: respect iface param"
  ;(stop-server) ; TODO: remove this, it's just for more convenient testing
  (def config {:iface iface :port port :handler handler}) ; TODO: remove (should not be needed)
  (reset! protocolhandler handler)
  (def http-server (http/start-server (fn [req]
                                        (log/debug "new connection | upgrading to websocket")
                                        (let [socket @(http/websocket-connection req wsconfig)]
                                          (def server-sockets (conj socket)) ; TODO: remove
                                          (reset! peers (conj @peers socket))
                                          (stream/consume #(onreceivemsg socket % handler) socket)
                                          (stream/on-closed socket #(on-socket-closed socket))))
                                      {:port port})))

(defn connect [host port handler]
  "Connects to a given websocket server, installs the callback handler and returns the socket"
  (let [socket @(http/websocket-client (str host ":" port) wsconfig)]
    (stream/consume (fn [msg] (onreceivemsg socket msg handler)) socket)
    (reset! peers (conj @peers socket))
    (stream/on-closed socket #(on-socket-closed socket))
    socket))

(defn disconnect
  ([] (dorun (map #(disconnect %) @peers)))
  ([peer]
   (.close peer)
   (reset! peers (disj @peers peer))))

(defn sendmsg [socket msg]
  "Takes a collection, converts it to JSON and sends it via the given socket."
  (let [msgstr (generate-string msg)]
    (log/debug "sending msg: " msgstr)
    (stream/put! socket msgstr)))
