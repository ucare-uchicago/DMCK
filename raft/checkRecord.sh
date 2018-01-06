#!/usr/bin/env bash

# check parameter length
if [ $# -ne 1 ]; then
	echo "usage: checkRecord.sh <max record to check>"
	exit 1
fi

# get first parameter
maxNumber=$1

hasError=false

# get all files in record directory
FILES=./record/*
for f in $FILES
do
	# get record file number
	IFS='/' read -a fileNumber <<< "$f"

	# only check files which has filename less than maxNumber
	if [[ ${fileNumber[2]} -lt $maxNumber ]]; then

		# read all line in file
		while read line
		do

			# if line starts with false then print it
			if [[ "$line" == "false"* ]]; then
				echo "$f : $line"
				hasError=true
			fi
		done < $f/result
	fi
done

# notification if there is no error
if [ $hasError = false ]; then
	echo "There is no error so far."
fi