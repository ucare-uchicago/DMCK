#!/usr/bin/env bash

interval=60
if [ ! -z "$1" ]; then
  interval=$1
fi

./testrunner.sh &
lastcheck=""
while [ true ]; do
  num1=`ls record | wc -l | sed '^ *//g'`
  sleep $interval
  num2=`ls record | wc -l | sed '^ *//g'`
  if [ $num1 -eq $num2 ]; then
    if [ "$lastcheck" = "1" ]; then
      break
    fi
    lastcheck="1"
    if [ ! $num1 -eq 0 ]; then
      remove=1
      for test_id in `find state -name .test_id`; do
        i=`cat $test_id`
        if [ $i -eq $num1 ]; then
          remove=""
          break
        fi
      done
      if [ "$remove" = "1" ]; then
        rm -r record/$num1
      fi
    fi
    killall java
    sleep 1
    ./testrunner.sh &
  else
    lastcheck=""
  fi
done
