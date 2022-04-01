#!/bin/sh

server_name=osstransfer
server_root=/data/server/osstransfer
cd $server_root

case $1 in 
   
   start)
	nohup java -Xmx128m -jar $server_name.jar > $server_root/$server_name.log 2>&1 &
	echo $! > $server_root/$server_name.pid
	;;

   stop)
	kill `cat $server_root/$server_name.pid`
	rm -rf $server_root/$server_name.pid
	;;
   restart)
	$0 stop
	sleep 1
	$0 start
	;;
   *)
	echo "Usage: run.sh {start|stop|restart}"
	;;

esac
exit 0	
