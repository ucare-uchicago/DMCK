#!/bin/bash

if [ $# != 1 ]; then
  echo "Usage: startWorkload.sh <test_id>" > workload_error.log
	exit 1m
fi

. ./readconfig

test_id=$1
workload_name="cass-6013"

classpath=$working_dir:$target_sys_dir/build/classes/main:$target_sys_dir/build/classes/thrift
driverLib=$target_sys_dir/datastax_lib
for i in `ls $driverLib/*.jar`; do
	classpath=$classpath:$i
done

java -cp $classpath -Dlog4j.configuration=cass_log.properties edu.uchicago.cs.ucare.dmck.interceptor.WorkloadExecutor $workload_name > $working_dir/console/$test_id/workload.log &
echo $! > $working_dir/samc-workload.pid
