#!/bin/bash
set -ex
lein clean
rm -rf dist
yarn install
yarn webpack
# prepare for a bit of a hack...
cp node_modules/sql.js/dist/sql-wasm.wasm resources/
lein uberjar
