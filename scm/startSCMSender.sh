#!/usr/bin/env bash

if [ $# -ne 4 ]; then
  echo "usage: startSCMSender.sh <ipc_dir> <dmck_dir> <node_id> <test_id>"
  exit 1
fi

. ./readconfig

ipc_dir=$1
dmck_dir=$2
node_id=$3
test_id=$4

java -cp $CLASSPATH -Dnode.log.dir=$working_dir/log/$test_id/node-$node_id -Dlog4j.configuration=scm_log.properties edu.uchicago.cs.ucare.dmck.scm.SCMSender $ipc_dir $dmck_dir $node_id &
echo $nodeId":"$! >> pid_file
