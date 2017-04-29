#!/bin/bash

set -e
set -u

if (($# != 1)); then
    echo "usage: $0 <data-dir>"
    exit 1
fi

DIR="$1/keys"

mkdir -p "$DIR"

FPARAMS="$DIR/params.pem"
FPRIVKEY="$DIR/privkey.pem"
FPUBKEY="$DIR/pubkey.pem"

if [ -f "$FPRIVKEY" ]; then
    echo "ERROR: file $FPRIVKEY already exists. Delete it if you want to use this location anyway."
    exit 2
fi

openssl ecparam -name prime256v1 -out "$FPARAMS"
openssl ecparam -in "$FPARAMS" -genkey -noout -out "$FPRIVKEY"
openssl ec -in "$FPRIVKEY" -pubout -out "$FPUBKEY"

echo "keys created in $DIR"