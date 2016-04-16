#!/bin/sh

bin_dir=$(dirname $0)
databases_script="$bin_dir/databases.sh"

help() {
	cat <<EOF
Usage: $0 [options]
Run listed tasks, starting and stopping databases if necessary.

 r[un]      - run application (by default it will be listenning on http://localhost:8080)
 t[est]     - run all tests
 a[utotest] - run tests as you change the code
 r[epl]     - start REPL

EOF
}

run_run() {
	$databases_script start
	lein run
	$databases_script stop
}

run_tests() {
	$databases_script start
	lein midje
	$databases_script stop
}

run_autotests() {
	$databases_script start
	lein midje :autotest
	$databases_script stop
}

run_repl() {
	$databases_script start
	lein repl
	$databases_script stop
}

case "$1" in
	"r"|"run")
		run_run
		;;
	"t"|"test"|"tests")
		run_tests
		;;
	"a"|"autotest"|"autotests")
		run_autotests
		;;
	"r"|"repl")
		run_repl
		;;
	*)
		help
		;;
esac
