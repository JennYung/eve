#!/bin/sh
# package the Eve library in a zip file

version="0.1"
bin="../bin"
package="${bin}/eve-nodejs-${version}.zip"
files="eve.js server.js README agent node_modules"

# make bin directory
mkdir -p "${bin}"

# create package
echo "creating package ${package}..."
zip -q -r "${package}" ${files}

echo "done"
