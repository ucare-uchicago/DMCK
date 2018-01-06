#!/bin/bash

. ./readconfig

while read line
do
		kill -9 $line
done < $working_dir/samc-workload.pid

echo "Kill Workload." >> $working_dir/mc.log
rm $working_dir/samc-workload.pid

