#!/bin/bash

if [ $# -ne 2 ]; then
  echo "Usage: ./startNode.sh <node_id> <test_id>"
  exit 1
fi

. ./readconfig

node_id=$1
test_id=$2

classpath=$working_dir:$target_sys_dir/build/release/bin/
export PATH=${PATH}:$classpath

if [ $node_id -eq "0" ]; then
  echo "Start Master Node.."
  # Start Master node
  kudu-master --fs_data_dirs=$working_dir/data/node-$node_id \
    --fs_wal_dir=$working_dir/wal/node-$node_id \
    --log_dir=$working_dir/log/$test_id/node-$node_id &
  echo $! > $working_dir/log/node-$node_id.pid
else
  RPC_PORT=`expr 7150 + $node_id`
  WEBSERVER_PORT=`expr 8150 + $node_id`
  # Start Tablet Server node
  echo "Start Tablet Server Node.."
  kudu-tserver --fs_data_dirs=$working_dir/data/node-$node_id \
    --fs_wal_dir=$working_dir/wal/node-$node_id \
    --log_dir=$working_dir/log/$test_id/node-$node_id \
    --rpc_bind_addresses=127.0.0.1:$RPC_PORT --webserver_port=$WEBSERVER_PORT &
  echo $! > $working_dir/log/node-$node_id.pid
fi
