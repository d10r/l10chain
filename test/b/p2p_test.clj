(ns b.p2p-test
  (:require [clojure.test :refer :all]
            [b.p2p :as p2p]))

(deftest ws-comms
  (testing "start server"
    (p2p/start-server "::" 43019 {}))

  (testing "connect a client"
    (def client-socket (p2p/connect "ws://localhost" 43019 #())))

  ;(testing "write to server socket"
  ;  @(p2p/sendmsg server-socket-promise :test {:some "data"}))

  (testing "write to client socket"
    @(p2p/sendmsg client-socket {:test {:map "data" :arr [2 3]}}))

  (testing "connect a second client"
    (def client-socket2 (p2p/connect "ws://localhost" 43019 #())))

  (Thread/sleep 1000)

  (testing "stop server"
    (p2p/stop-server))

  )
