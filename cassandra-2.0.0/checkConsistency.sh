#!/bin/bash

if [ $# -ne 0 ]; then
  echo "Usage: ./checkConsistency.sh"
  exit 1
fi

. ./readconfig

classpath=$working_dir:$target_sys_dir/build/classes/main:$target_sys_dir/build/classes/thrift
driverLib=$target_sys_dir/datastax_lib
for i in `ls $driverLib/*.jar`; do
	classpath=$classpath:$i
done

java -cp $classpath -Dlog4j.configuration=cass_log.properties edu.uchicago.cs.ucare.dmck.interceptor.ConsistentVerifier $working_dir > verifier.log
