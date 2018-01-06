#!/bin/bash

if [ $# != 2 ]; then
  echo "Usage: startWorkload.sh <data_key> <test_id>" > workload_error.log
	exit 1m
fi

. ./readconfig

data_key=$1
test_id=$2

classpath=$working_dir:$target_sys_dir/build/classes/main:$target_sys_dir/build/classes/thrift
driverLib=$target_sys_dir/datastax_lib
for i in `ls $driverLib/*.jar`; do
	classpath=$classpath:$i
done

java -cp $classpath -Dlog4j.configuration=cass_log.properties edu.uchicago.cs.ucare.dmck.interceptor.WorkloadExecutor $data_key > $working_dir/console/$test_id/workload.log &
echo $! > $working_dir/samc-workload.pid
