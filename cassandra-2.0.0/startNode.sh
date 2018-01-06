#!/bin/bash

if [ $# -ne 2 ]; then
  echo "Usage: ./startNode.sh <nodeId> <testId>"
  exit 1
fi

. ./readconfig

nodeId=$1
testId=$2

classpath=$working_dir:$target_sys_dir/build/classes/main:$target_sys_dir/build/classes/thrift
workingDirLib=$target_sys_dir/lib
for j in `ls $workingDirLib/*.jar`; do
  classpath=$classpath:$j
done

java -Dcassandra.jmx.local.port=$(expr $nodeId + 7199) -Dlogback.configurationFile=logback.xml -Dcassandra.logdir=$working_dir/log/cass$nodeId -Dlog4j.configuration=cass_log.properties -Dcassandra.storagedir=$working_dir/data/cass$nodeId -Dcassandra-foreground=no -cp $working_dir/config/cass$nodeId:.:$classpath -Dlog4j.defaultInitOverride=true org.apache.cassandra.service.CassandraDaemon >> $working_dir/console/$testId/cass$nodeId.out &
echo $! > $working_dir/data/cass$nodeId/cassandra_server.pid
