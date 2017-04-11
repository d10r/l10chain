(ns b.block-test
  (:require [b.block :refer :all])
  (:require [b.fixtures :as f])
  (:require [clojure.test :refer :all]))

(deftest block-construction
  (testing "create new block"
    (let [b (->Block f/addr f/sig 3 nil f/bhash [])]
      (is (valid? b))))

  (testing "instantiate existing block"
    (let [b (map->Block f/bl-map)])))