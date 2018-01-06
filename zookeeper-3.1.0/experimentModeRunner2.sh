#!/bin/bash

if [ $# -ne 3 ]; then
	echo "Usage: ./experimentModeRunner2.sh <exploring_policy> <persistent_dir> <path_number_to_run_split_by_comma>"
	echo "Example: ./experimentModeRunner2.sh samc /tmp 21,26,28"
	echo "NOTE: path 0 = minusall"
	exit 1
fi

exploringPolicy=$1
persistentDir=$2
paths=$3

. ./readconfig

function runWorkload {
	# policy setup
	if [ $exploringPolicy == "samc" ] || [ $exploringPolicy == "SAMC" ]; then
		policy="exploring_strategy=edu.uchicago.cs.ucare.dmck.zookeeper.ZKSAMC"
		sed -i "s:.*exploring_strategy=.*:$policy:g" $working_dir/target-sys.conf
	elif [ $exploringPolicy == "dpor" ] || [ $exploringPolicy == "DPOR" ]; then
		policy="exploring_strategy=edu.uchicago.cs.ucare.dmck.server.DporModelChecker"
		sed -i "s:.*exploring_strategy=.*:$policy:g" $working_dir/target-sys.conf
	fi

	# run workload
	$working_dir/dmckRunner.sh

	# copy logs to persistent directory
	today=$( date +%Y%m%d )
	curPersistentDir=$persistentDir/experiment-logs/$today-zookeeper-3.1.0/minus$1
	mkdir -p $curPersistentDir

	cp $working_dir/*.conf $curPersistentDir
	cp -r $working_dir/record $curPersistentDir
}

IFS=',' read -ra path <<< "$paths"
for i in "${path[@]}"; do
	# reset working directory
	$dmck_dir/zookeeper-3.1.0/setup

	if [ $i -eq "0" ]; then
		# prepare target-sys.conf
		sed -i '6d' $working_dir/target-sys.conf

		runWorkload all
	else
		step="${i}ev"
		
		# prepare target-sys.conf
		initialPath="initial_path=${dmck_dir}/zookeeper-3.1.0/initialPaths/zk-335-${step}"
		sed -i "s:.*initial_path=.*:$initialPath:g" $working_dir/target-sys.conf

		runWorkload $step
	fi
done
