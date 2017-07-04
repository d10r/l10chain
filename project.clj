(defproject ws "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [http-kit "2.2.0"]
                 [http.async.client "1.2.0"]
                 [cheshire "5.7.0"] ;JSON
                 [buddy/buddy-core "1.2.0"] ;crypto
                 [aleph "0.4.3"]
                 [org.clojure/tools.cli "0.3.5"]
                 [proto-repl "0.3.1"] ; TODO: remove
                 [mvxcvi/alphabase "0.2.2"] ; base58 codec
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.12"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]]

  :main b.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
