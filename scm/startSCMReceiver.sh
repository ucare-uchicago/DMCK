#!/usr/bin/env bash

if [ $# -ne 4 ]; then
  echo "usage: startSCMReceiver.sh <ipc_dir> <dmck_dir> <peer_nodes> <test_id>"
  exit 1
fi

. ./readconfig

ipc_dir=$1
dmck_dir=$2
peer_nodes=$3
test_id=$4

java -cp $CLASSPATH -Dnode.log.dir=$working_dir/log/$test_id/node-0 -Dlog4j.configuration=scm_log.properties edu.uchicago.cs.ucare.dmck.scm.SCMReceiver $ipc_dir $dmck_dir $peer_nodes &
echo "0:"$! >> pid_file
