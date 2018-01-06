#!/usr/bin/env bash

pause=""
if [ $# -eq 1 ] && [ $1 == "-p" ]; then
  echo "MODE: Execute DMCK with Pause for each execution path."
  pause="-p"
fi

. ./readconfig

classpath=$dmck_dir/bin
lib=$dmck_dir/lib
for j in `ls $lib/*.jar`; do
  classpath=$classpath:$j
done
export CLASSPATH=$CLASSPATH:$classpath

java -cp $CLASSPATH -Dlog4j.configuration=mc_log.properties -Ddmck.dir=$working_dir edu.uchicago.cs.ucare.dmck.server.DMCKRunner $pause
