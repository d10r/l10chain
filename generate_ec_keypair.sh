openssl ecparam -name prime256v1 -out params.pem
openssl ecparam -in params.pem -genkey -noout -out privkey.pem
openssl ec -in privkey.pem -pubout -out pubkey.pem
