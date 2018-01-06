#!/bin/bash

if [ $# -ne 3 ]; then
  echo "Usage: ./checkConsistency.sh <num_node> <data_key> <string_of_node_aliveness>"
  exit 1
fi

. ./readconfig

numNode=$1
dataKey=$2
isNodeOnline=$3

classpath=$working_dir:$target_sys_dir/build/classes
workingDirLib=$target_sys_dir/lib
for j in `ls $workingDirLib/*.jar`; do
  classpath=$classpath:$j
done

java -cp $classpath edu.uchicago.cs.ucare.dmck.interceptor.ConsistentVerifier $working_dir $numNode $dataKey $isNodeOnline

