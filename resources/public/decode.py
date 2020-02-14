#!/usr/bin/env python3
import sys
import tarfile
import json
import gzip
import base64
import io
from nacl.public import PublicKey, PrivateKey, SealedBox
from nacl.encoding import URLSafeBase64Encoder

if len(sys.argv) != 3:
    raise Exception("Usage: decode.py <keypair> file")

(_, priv, _) = sys.argv[1].split(":")

priv += "==="  # python's base64 decoder can't cope with no padding
priv = PrivateKey(priv, encoder=URLSafeBase64Encoder)

with tarfile.open(sys.argv[2]) as tar:
    for member in tar:
        enc = tar.extractfile(member).read()
        f = io.BytesIO(SealedBox(priv).decrypt(enc))
        with gzip.open(f) as g:
            for item in json.load(g):
                print(json.dumps(item))
