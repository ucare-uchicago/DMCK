#!/usr/bin/env bash

if [ $# -ne 2 ]; then
  echo "usage: snapshot.sh <leaderId> <testId>"
  exit 1
fi

. ./readconfig

leader_id=$1
test_id=$2

SERVER=''
if [[ $leader_id == 0 ]]; then
	SERVER=127.0.0.1:5254
elif [[ $leader_id == 1 ]]; then
	SERVER=127.0.0.1:5255
elif [[ $leader_id == 2 ]]; then
	SERVER=127.0.0.1:5256
fi

$target_sys_dir/build/Client/ServerControl snapshot start -s $SERVER &> $working_dir/log/$test_id/admin-snapshot.log
