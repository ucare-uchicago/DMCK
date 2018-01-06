#!/bin/bash

if [ $# != 2 ]; then
  echo "Usage: startWorkload.sh <data_key> <test_id>" > workload_error.log
	exit 1
fi

. ./readconfig

data_key=$1
test_id=$2

classpath=$working_dir:$target_sys_dir/build/classes
workingDirLib=$target_sys_dir/lib
for i in `ls $workingDirLib/*.jar`; do
	classpath=$classpath:$i
done

java -cp $classpath -Dlog4j.configuration=zk_log.properties edu.uchicago.cs.ucare.dmck.interceptor.WorkloadExecutor $data_key > $working_dir/console/$test_id/workload.log &
echo $! > $working_dir/dmck-workload.pid

