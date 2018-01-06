#!/bin/bash

if [ $# != 1 ]; then
  echo "Usage: ./generate-scenarios.sh <main-scenario>"
	exit 1
fi

mainfile=$1
dirfile=$(dirname "${mainfile}")
basename=`basename "${mainfile}"`
numberEvents=`grep --regexp="$" --count $mainfile`
counter=$(($numberEvents - 1))

for i in `seq 1 $counter`; do
	newfile="${basename}-${i}ev"
	head -n -$i $mainfile > $dirfile/$newfile
done

echo "Done..!"

