#!/bin/bash -eu

COMMAND=$0

usage() {
    cat <<_EOF_
Usage: $COMMAND USER [APP-FILE]
Install iwb from unzipped iwb distribution for running as unix daemon under user USER 

USER		User that owns the files and runs the iwb
APP-FILE	Optional an application-zip that shall be installed

Must run as root in the directory where the IWB zip was unzipped.
_EOF_
	exit 1
}

checkArgs() {
    if [ $# -lt 1 -o $# -gt 2 ]
    then
    	usage
	fi
}

checkDir() {
	if [ ! -f start.sh -o ! -f stop.sh -o ! -d fiwb ]
	then
		usage
	fi
}

checkIfRoot() {
	if [ "$(id -u)" != "0" ]
	then
		usage
	fi
}

changeOwnerTo() {
	local user="$1"
	chown -R "$user:$(id -gn "$user")" .
}

makeScriptsExecutable() {
	chmod +x start.sh
	chmod +x stop.sh
	chmod +x fiwb/iwb.sh
	chmod +x fiwb/wrapper-linux*
	chmod +x fiwb/cli.sh
	# ensure that all shell scripts executable
	find . -maxdepth 3 -name "*.sh" -exec chmod +x {} \;
	chmod -R g+w .
}

configureIwbAsService() {
	local user="$1"
	su "$user" -c "echo runningAsService=true >>fiwb/config.prop"
}

copyApp() {
	local user="$1"
	local appZip="$2"
	if [ -n "$appZip" ]
	then 
		su "$user" -c "( [ -d fiwb/apps ] || mkdir fiwb/apps ) && cp \"$appZip\" fiwb/apps"
	fi
} 

checkArgs "$@"
user="$1"
appZip="${2:-}"

checkIfRoot
makeScriptsExecutable
changeOwnerTo "$user"
configureIwbAsService "$user"
copyApp "$user" "$appZip"
cat > /etc/init.d/iwb <<_EOF_
#!/bin/bash
# The following two lines are used by the chkconfig command.
# chkconfig: 2345 20 80
# description: Information Workbench

# SUSE install_initd
### BEGIN INIT INFO
# Provides: iwb
# Required-Start: \$local_fs \$network \$syslog
# Should-Start:
# Required-Stop:
# Default-Start: 2 3 4 5
# Default-Stop: 0 1 6
# Short-Description: Information Workbench
### END INIT INFO

exec bash $(pwd)/fiwb/iwb.sh "\$@"
_EOF_
chmod +x /etc/init.d/iwb
/etc/init.d/iwb start
 