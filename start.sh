#!/bin/bash
cd "$(dirname "$0")"
echo Starting Information Workbench Wrapper
echo 
echo Default host: http://localhost:8888
echo Logs: $PWD/fiwb/logs
echo 
echo Note: startup may take up to a few minutes
echo 
if [ -n "$1" -a "$1" = "-d" ] ; then
    fiwb/iwb.sh start
else
	fiwb/iwb.sh console
fi
