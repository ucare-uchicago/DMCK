#!/bin/bash

. ./readconfig

while read line
do
		kill -9 $line
done < $working_dir/samc-workload1.pid

while read line
do
		kill -9 $line
done < $working_dir/samc-workload2.pid

while read line
do
		kill -9 $line
done < $working_dir/samc-workload3.pid

echo "Kill Workloads." >> $working_dir/mc.log

rm $working_dir/samc-workload1.pid
rm $working_dir/samc-workload2.pid
rm $working_dir/samc-workload3.pid

rm /tmp/liblz4-java*

