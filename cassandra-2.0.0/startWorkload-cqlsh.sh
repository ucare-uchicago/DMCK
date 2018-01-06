#!/bin/bash

if [ $# != 1 ]; then
  echo "Usage: startWorkload.sh <test_id>" > workload_error.log
	exit 1
fi

. ./readconfig

test_id=$1

classpath=$working_dir:$target_sys_dir/build/classes/main:$target_sys_dir/build/classes/thrift:
driverLib=$target_sys_dir/datastax_lib
for i in `ls $driverLib/*.jar`; do
	classpath=$classpath:$i
done

$target_sys_dir/bin/cqlsh 127.0.0.1 --file="$working_dir/query/update1.cql" > $working_dir/console/$test_id/workload1.log &
echo $! > $working_dir/samc-workload1.pid
echo "id=1" > /tmp/ipc/new/cassWorkloadUpdate-1
echo "isApplied=true" >> /tmp/ipc/new/cassWorkloadUpdate-1
mv /tmp/ipc/new/cassWorkloadUpdate-1 /tmp/ipc/send/cassWorkloadUpdate-1
sleep 1s

$target_sys_dir/bin/cqlsh 127.0.0.2 --file="$working_dir/query/update2.cql" > $working_dir/console/$test_id/workload2.log &
echo $! > $working_dir/samc-workload2.pid
echo "id=2" > /tmp/ipc/new/cassWorkloadUpdate-2
echo "isApplied=true" >> /tmp/ipc/new/cassWorkloadUpdate-2
mv /tmp/ipc/new/cassWorkloadUpdate-2 /tmp/ipc/send/cassWorkloadUpdate-2
sleep 1s

$target_sys_dir/bin/cqlsh 127.0.0.3 --file="$working_dir/query/update3.cql" > $working_dir/console/$test_id/workload3.log &
echo $! > $working_dir/samc-workload3.pid
echo "id=3" > /tmp/ipc/new/cassWorkloadUpdate-3
echo "isApplied=true" >> /tmp/ipc/new/cassWorkloadUpdate-3
mv /tmp/ipc/new/cassWorkloadUpdate-3 /tmp/ipc/send/cassWorkloadUpdate-3
