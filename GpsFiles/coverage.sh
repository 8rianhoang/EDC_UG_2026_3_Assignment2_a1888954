#!/bin/bash
# Compiles everything, runs the unit tests under JaCoCo and writes an HTML coverage report.
# Needs these environment variables (see testing.pdf):
#   SODIUM SWIDGETS JUNIT JACOCO_AGENT JACOCO_CLI  (paths to the jar files)
# Run it from the folder that contains the .java files.
set -e
CLASSES="GpsMath TrackPoint SimpleGps GpsFormat GpsRange DistanceWindow GpsLogic GpsEvent"

javac --release 11 -cp ".:$SODIUM:$SWIDGETS:$JUNIT" *.java
rm -f jacoco.exec

INCLUDES=$(echo $CLASSES | tr ' ' ':')
java -javaagent:"$JACOCO_AGENT"=destfile=jacoco.exec,includes="$INCLUDES" \
  -jar "$JUNIT" --class-path ".:$SODIUM" --scan-classpath=.

ARGS=""
for c in $CLASSES; do ARGS="$ARGS --classfiles $c.class"; done
java -jar "$JACOCO_CLI" report jacoco.exec $ARGS --sourcefiles . \
  --html coverage-report --csv coverage.csv

echo
echo "Coverage report: coverage-report/index.html"
awk -F, 'NR > 1 { printf "%-16s lines %d/%d  branches %d/%d\n", $3, $9, $8 + $9, $7, $6 + $7 }' coverage.csv
