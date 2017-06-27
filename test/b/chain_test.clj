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

  (testing "longer chain is better"
    ; This currently fails because the shorter chain happens to have a lower avg distance, which is amplified by the penalty
    ; TODO: It may thus be better to also give older blocks higher weight (similar to the penalty mechanism).
    (is (chain/better? ch_0_5 (pop ch_0_5))))

  (testing "legal append new block"
    (let [nc (chain/append-or-replace-last (pop ch_0_5) bl_5_alt_valid)]
      (is (and
            (chain/valid? nc)
            (= nc (conj (pop ch_0_5) bl_5_alt_valid))))))

  (testing "illegal append new block - bad hash"
    (is (nil? (chain/append-or-replace-last ch_0_5 bl_5_alt_invalid_hash))))

  (testing "illegal append new block - bad signature"
    (is (nil? (chain/append-or-replace-last ch_0_5 bl_5_alt_invalid_sig))))

;  (testing "replace last block"
;    (is (= (chain/append-or-replace-block (pop ch_0_5) bl_5a) (conj (pop ch_0_5) bl_5a)))))

  (testing "illegal add/replace last block because of gap"
    (let [ch_0_3 (pop (pop ch_0_5))]
      (is (nil? (chain/append-or-replace-last ch_0_3 bl_5_alt_valid)))))
  )