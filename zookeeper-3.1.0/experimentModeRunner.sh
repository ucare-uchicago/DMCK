#!/bin/bash

startmin=1
maxmin=1
interval=1
minusall=0
if [ $# -lt 3  ] || [ $# -gt 6 ]; then
	echo "Usage: ./experimentModeRunner.sh <exploring_policy> <persistent_dir> <max_minus_path>"
  echo "       ./experimentModeRunner.sh <exploring_policy> <persistent_dir> <starting_minus_path> <max_minus_path>"
  echo "       ./experimentModeRunner.sh <exploring_policy> <persistent_dir> <starting_minus_path> <max_minus_path> <1 if you want to do minus all>"
	echo "       ./experimentModeRunner.sh <exploring_policy> <persistent_dir> <starting_minus_path> <max_minus_path> <1 if you want to do minus all> <interval>"
	exit 1
elif [ $# -eq 6 ]; then
  startmin=$3
	maxmin=$4
  minusall=$5
	interval=$6
elif [ $# -eq 5 ]; then
  startmin=$3
  maxmin=$4
  minusall=$5
elif [ $# -eq 4 ]; then
	startmin=$3
  maxmin=$4
elif [ $# -eq 3 ]; then
  maxmin=$3
fi

exploringPolicy=$1
persistentDir=$2
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


for i in `seq $startmin $interval $maxmin`; do
	step="${i}ev"

	# prepare target-sys.conf
	initialPath="initial_path=${dmck_dir}/zookeeper-3.1.0/initialPaths/zk-335-${step}"
	sed -i "s:.*initial_path=.*:$initialPath:g" $working_dir/target-sys.conf

	runWorkload $step

	# reset working directory
	$dmck_dir/zookeeper-3.1.0/setup
done

# lastly, runWorkload without initial_path
if [ $minusall == 1 ]; then
	sed -i '6d' $working_dir/target-sys.conf
	runWorkload all
fi
