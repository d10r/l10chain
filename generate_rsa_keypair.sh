# For stronger keys, the Oracle JVM needs to be told not to fuck around... See http://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#importlimits

openssl genrsa -aes128 -out privkey.pem 1024
openssl rsa -pubout -in privkey.pem -out pubkey.pem
