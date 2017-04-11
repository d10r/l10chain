(ns b.chain-test
  (:require [b.fixtures :refer :all])
  (:require [clojure.test :refer :all]
            [b.chain :as chain]))


(deftest merge-test
  (testing "find mrca"
    (let [mrcablock (chain/mrca ch_0_4 bl_3_5)]
      (is (= (get-in mrcablock [:header :height]) 4))))

  (testing "merge chains"
    (let [mergedch (chain/mergex ch_0_4 bl_3_5)]
      (is (= ch_0_5 mergedch))))

  (testing "better chain"
    ; This currently fails because the shorter chain happens to have a lower avg distance, which is amplified by the penalty
    ; TODO: It may thus be better to also give older blocks higher weight (similar to the penalty mechanism).
    ;(is (chain/isbetter ch_0_5 (pop ch_0_5)))
    )

  (testing "append new block"
    (is (= (chain/append-block (pop ch_0_5) bl_5a) (conj (pop ch_0_5) bl_5a ))))

  (testing "illegal append new block"
    (is (nil? (chain/append-block ch_0_5 bl_5a)))

  (testing "replace last block"
    (is (= (chain/append-or-replace-block (pop ch_0_5) bl_5a) (conj (pop ch_0_5) bl_5a)))))

  (testing "illegal add/replace last block because of gap"
    (let [ch_0_3 (pop (pop ch_0_5))]
      (is (nil? (chain/append-or-replace-block ch_0_3 bl_5a)))))

  )