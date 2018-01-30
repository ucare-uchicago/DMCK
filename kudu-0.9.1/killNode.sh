#!/bin/bash

if [ $# -ne 2 ]; then
  echo "Usage: ./killNode.sh <node_id> <test_id>"
  exit 1
fi

. ./readconfig

node_id=$1
test_id=$2

while read line
do
		kill -9 $line
done < $working_dir/log/node-$node_id.pid

echo "Kill node $node_id." >> $working_dir/log/$test_id/node-$node_id/*.INFO
rm $working_dir/log/node-$node_id.pid
