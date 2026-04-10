#!/bin/sh

SCRIPTPATH=$(dirname `readlink -f "$0"`)

MP=`readlink -f "$SCRIPTPATH/../modules"`
LIB=`readlink -f "$SCRIPTPATH/../classpath"`

$SCRIPTPATH/java -Djava.util.logging.manager=org.jboss.logmanager.LogManager --module-path $MP<#if classpath?has_content> -cp ${classpath}</#if> -m ${rootModule}/build.spin.application.Spin $@
