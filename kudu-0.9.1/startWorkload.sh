#!/bin/bash

# Read target-sys.conf
. ./readconfig

# Make sure that parameter is passed
if [ $# -ne 1 ]; then
  echo "Usage: startWorkload.sh <test_id>" > $working_dir/workload_error.log
  exit 1
fi

test_id=$1

# Prepare classpath
classpath=$target_sys_dir/kudu-examples/java/java-sample/target/kudu-java-sample-1.0-SNAPSHOT.jar
export CLASSPATH=$CLASSPATH:$classpath

# Execute Workload
master_url_port=127.0.0.1:7051
java -cp $CLASSPATH -DkuduMaster=$master_url_port org.kududb.examples.sample.Sample "insert_row" > \
  $working_dir/log/$test_id/insert_row_workload.log 2>&1 &
java -cp $classpath -DkuduMaster=$master_url_port org.kududb.examples.sample.Sample "delete_table" > \
  $working_dir/log/$test_id/delete_table_workload.log 2>&1 &

