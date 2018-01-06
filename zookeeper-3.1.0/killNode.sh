#!/bin/bash

if [ $# -ne 2 ]; then
  echo "Usage: ./killNode.sh <nodeId> <testId>"
  exit 1
fi

. ./readconfig

nodeId=$1
testId=$2

while read line
do
		kill -9 $line
done < $working_dir/data/zk$nodeId/zookeeper_server.pid

echo "Kill node $nodeId." >> $working_dir/console/$testId/zk$nodeId.out
rm $working_dir/data/zk$nodeId/zookeeper_server.pid
