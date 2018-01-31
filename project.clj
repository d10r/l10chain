(defproject l10chain "0.2.1"
  :description "A minimal Blockchain in Clojure"
  :url "https://github.com/d10r/l10chain"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [http-kit "2.2.0"]
                 [http.async.client "1.2.0"]
                 [cheshire "5.7.0"] ; JSON
                 [buddy/buddy-core "1.2.0"] ; crypto
                 [aleph "0.4.3"]
                 [org.clojure/tools.cli "0.3.5"] ; cmdline parsing
                 [mvxcvi/alphabase "0.2.2"] ; base58 codec
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.12"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]]

  :main ^:skip-aot b.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
