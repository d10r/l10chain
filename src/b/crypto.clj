(ns b.crypto
  (:require [buddy.core.hash]
            [buddy.core.codecs :refer :all]
            [buddy.core.keys :as keys]
            [buddy.core.dsa :as dsa]
            [alphabase.base58 :as b58])
  (:import [java.security Key KeyFactory KeyPair KeyPairGenerator MessageDigest PrivateKey PublicKey Security Signature KeyStore]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec])
  (:require [clojure.tools.logging :as log])
  (:import (org.apache.log4j Logger Level)))

(.setLevel (Logger/getLogger (str *ns*)) Level/INFO)

(defn base58 [data]
  "Returns the base58 representation of a hash. Input must be byte array."
  (b58/encode data))

; "hash" would override a function in clojure.core, triggering a warning
(defn hashx [data]
  "Returns the sha256 hash of data as hex string"
  ;  (-> (buddy.core.hash/sha256 data)
  ;      (bytes->hex)))
  (let [binhash (buddy.core.hash/sha256 data)]
    ;(println "hash of " data " | " (bytes->hex binhash))
    (bytes->hex binhash)))

(defn loadkeysOLD [directory password]
  ; TODO: Make this functional!
  "Loads the keys from the given directory and returns the public key as hex string."
  (def privkey (keys/private-key (str directory "/privkey.pem") password))
  (def pubkey (keys/public-key (str directory "/pubkey.pem")))
  (bytes->hex (.getEncoded pubkey)))

(defn loadkeys [directory password]
  "Loads the keys from the given directory and returns them in a map."
  (let [privk (keys/private-key (str directory "/privkey.pem") password)
        pubk (keys/public-key (str directory "/pubkey.pem"))
        pubk-hex (bytes->hex (.getEncoded pubk))]
    {:pubkey pubk :privkey privk :pubkey-hex pubk-hex}))

(defn sign [data key]
  "Computes a signature and returns it as hex string."
  (-> (dsa/sign data {:key key :alg :ecdsa+sha256})
       (bytes->hex)))

(defn decodepubkey [pkhex]
  (let [pkbin (hex->bytes pkhex)
        keyfactory (KeyFactory/getInstance "EC")
        keyspec (new X509EncodedKeySpec pkbin)]
    (.generatePublic keyfactory keyspec)))

(defn verifysig [sig data pubkey-hex]
  "Checks if the given hex signature is valid for data."
  (log/debug "sig " sig " data " data " hpk " pubkey-hex)
  (try
    (let [pubkey (decodepubkey pubkey-hex)]
      (dsa/verify data (hex->bytes sig) {:key pubkey :alg :ecdsa+sha256}))
    (catch java.security.SignatureException e
      (log/debug "signature check threw exception: " (.toString e))
      false)))

(defn distance [forger signature]
  "Distance between a given forger and the ideal forger for the next block based on previous block signature.
  The result is scaled down to 64 bit for easier handling. Precision needs to be increased once every metazoan runs a node.
  Returns a double. Probably not ideal, but avoids the headache of overflows."
  (let [scaledownfactor (Math/pow 2 192)]                   ; map to Long
    (let [bigdiff (- (BigInteger. (hashx forger) 16) (BigInteger. (hashx signature) 16))
          scaleddiff (quot bigdiff scaledownfactor)]
      (Math/abs scaleddiff))))

(defn max-distance []
  "The max possible distance between a forger and the ideal forger."
  ; since distance is mapped to a 64 bit int, half of it is the max distance
  (Math/pow 2 64))
