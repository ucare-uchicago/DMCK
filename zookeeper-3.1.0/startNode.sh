#!/bin/bash

if [ $# -ne 2 ]; then
  echo "Usage: ./startNode.sh <nodeId> <testId>"
  exit 1
fi

. ./readconfig

nodeId=$1
testId=$2

classpath=$working_dir:$target_sys_dir/build/classes
workingDirLib=$target_sys_dir/lib
for j in `ls $workingDirLib/*.jar`; do
  classpath=$classpath:$j
done

java -cp $classpath -Xmx1G -Dzookeeper.log.dir=$working_dir/log/zk$nodeId -Dlog4j.configuration=zk_log.properties org.apache.zookeeper.server.quorum.QuorumPeerMain $working_dir/config/zk$nodeId/zoo.cfg >> $working_dir/console/$testId/zk$nodeId.out &
echo $! > $working_dir/data/zk$nodeId/zookeeper_server.pid


