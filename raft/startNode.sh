#!/usr/bin/env bash

if [ $# -ne 2 ]; then
  echo "usage: startNode.sh <nodeId> <testId>"
  exit 1
fi

. ./readconfig

nodeId=$1
testId=$2

$target_sys_dir/build/LogCabin -c $target_sys_dir/config/logcabin-$nodeId.conf -l $working_dir/log/$testId/node-$nodeId.log & 
echo $nodeId":"$! >> pid_file
