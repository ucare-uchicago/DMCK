#!/usr/bin/env bash

if [ $# -ne 1 ]; then
  echo "usage: refreshStorage.sh <nodeId>"
  exit 1
fi

. ./readconfig

node_id=$1

rm -r $working_dir/storage/server$node_id
cp -r $dmck_dir/raft/storage/server$node_id $working_dir/storage/
