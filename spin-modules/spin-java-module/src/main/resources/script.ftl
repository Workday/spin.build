#!/bin/sh

SCRIPTPATH=$(dirname `readlink -f "$0"`)

CP=`readlink -f "$SCRIPTPATH/../modules"`

$SCRIPTPATH/java -cp ${classpath} build.spin.application.Spin $@
