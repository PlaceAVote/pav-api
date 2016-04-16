#!/bin/sh

bin_dir=$(dirname $0)
databases_script="$bin_dir/databases.sh"

help() {
	cat <<EOF
Usage: $0 [options]
Run listed tasks, starting and stopping databases if necessary.

 t[est] - run all tests
 r[epl] - start REPL

EOF
}

run_tests() {
	$databases_script start
	lein midje
	$databases_script stop
}

run_repl() {
	$databases_script start
	lein repl
	$databases_script stop
}

case "$1" in
	"t"|"test"|"tests")
		run_tests
		;;
	"r"|"repl")
		run_repl
		;;
	*)
		help
		;;
esac
