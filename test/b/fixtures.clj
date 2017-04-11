(ns b.fixtures)

; a valid chain with 6 blocks (5-0). Created with (pr-str @b)
(def ch_0_5 (read-string "()"))

; subchain 4-0
(def ch_0_4 (read-string "()"))

; subchain 5-3
(def bl_3_5 (read-string "()"))

; height 5
(def bl_5a (read-string "()"))

(def bl-map (into {} bl_5a))
(def bl_5a_badhash (read-string "()"))

(def addr "3059301306072a8648ce3d020106082a8648ce3d03010703420004a0141000ec4c9dd583c0fb8f406f98c19c226978b0f1d97124cf9a4550b38d802d72baca3c1dd4e46ca920f81ae5d0c2180b20010db15d68bd9f1a3904840489")
(def sig "3045022100cf6e099dd2c3dfcedd6e24cb683545670ad892f4e60fa316c1ea15f651085dfc02200ae29da9451baa04b3a3b902772f03a1b4b1cffdfe015183244c93f6391bcfd7")
(def bhash "460da06fc301afb74b46049e209455d3bec856f2d2683f4c4159e5358610d018")