#!/usr/bin/env bash

if [ $# -ne 1 ]; then
  echo "usage: clientWrite.sh <testId>"
  exit 1
fi

. ./readconfig

test_id=$1
ALLSERVERS=127.0.0.1:5254,127.0.0.1:5255,127.0.0.1:5256

echo -n jeff | $working_dir/build/Examples/TreeOps --cluster=$ALLSERVERS write /ucare &> /tmp/raft/log/$test_id/client.log &
echo "3:$!" >> pid_file
