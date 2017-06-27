; This tests aren't implemented as actual tests which can fail, because I didn't yet figure out how to do this
; for async operations like is the case here.
; Still, in case something goes wrong, the test will spit stack traces to the console.
; At least when executed manually, that should be easily detectable as error.

(ns b.p2p-test
  (:require [clojure.test :refer :all]
            [b.p2p :as p2p]))

(defn receive-handler [cmd data peer]
  (println "received cmd " cmd " with data " data))

(def testdata {:testcmd {:k1 "v1" :k2 [2 3]}})

(deftest ws-comms
  (testing "start server"
    (p2p/start-server "::" 43019 receive-handler))

  (testing "connect a client"
    (def client-socket (p2p/connect "ws://localhost" 43019 #())))

  (testing "write to client socket"
    @(p2p/sendmsg client-socket testdata))

  (testing "connect a second client"
    (def client-socket2 (p2p/connect "ws://localhost" 43019 #())))

  (Thread/sleep 1000)

  (testing "stop server"
    (p2p/disconnect)
    (p2p/stop-server)))
