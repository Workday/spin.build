#!/bin/sh

SCRIPTPATH=$(dirname `readlink -f "$0"`)

CP=`readlink -f "$SCRIPTPATH/../modules"`

$SCRIPTPATH/java -Djava.util.logging.manager=org.jboss.logmanager.LogManager -cp ${classpath} build.spin.application.Spin $@
