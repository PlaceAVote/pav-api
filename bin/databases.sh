#!/bin/sh
# optionally download and start/stop necessary databases

WORK_PATH=${WORK_PATH:-".db"}

# packages to download with sha1 sums
es_url="https://download.elastic.co/elasticsearch/release/org/elasticsearch/distribution/tar/elasticsearch/2.3.1/elasticsearch-2.3.1.tar.gz"
es_checksum="387c5f045843339d486203b0048ff8911e9c8c54"
es_dir=$(basename $es_url | sed 's/.tar.gz//')

redis_url="http://download.redis.io/releases/redis-3.0.7.tar.gz"
redis_checksum="e56b4b7e033ae8dbf311f9191cf6fdf3ae974d1c"
redis_dir=$(basename $redis_url | sed 's/.tar.gz//')

dynamodb_url="http://dynamodb-local.s3-website-us-west-2.amazonaws.com/dynamodb_local_latest.tar.gz"
dynamodb_checksum="700811cf99c094eccfcfd8869bb5711906d29f6f"
dynamodb_dir="dynamodb"

# portable way to figure out path of programs
find_path() {
	command -v $1
}

do_fail() {
	echo "*** Error: $1"
	exit 1
}

trace() {
	echo "==> " $1
}

calc_checksum() {
	if [ -z $(find_path sha1sum) ]; then
		do_fail "sha1sum tool isn't present."
	fi
	
	sha1sum $1 | awk '{print $1}'
}

do_download() {
	downloader=$(find_path "wget")
	if [ -z $downloader ]; then
		downloader=$(find_path "curl")
		if [ -z $downloader ]; then
			do_fail "unable to find neither 'wget' nor 'curl'. Install one of them first."
		else
			# curl -LO will download file as is, following any https requests
			downloader="$downloader -LO"
		fi
	fi
	
	url=$1
	sum=$2
	archive=$(basename $url)
	
	if [ ! -f $archive ]; then
		trace "Downloading $archive..."
		$downloader $url
	fi

	if [ -f $archive ] && [ $sum = $(calc_checksum $archive) ]; then
		# This is a trick to search for a substring in a string inside bash. There is
		# also 'perlism' with double squae brackets, but they aren't used here.
		if [ $archive != ${archive/dynamodb/} ]; then
			# Amazon does not pack dynamodb in designated folder so we must
			# do that manually
			mkdir -p $dynamodb_dir && tar -xpvf $archive -C $dynamodb_dir
		else
			tar -xpvf $archive
			# in case of Redis, compile it too
			if [ $archive != ${archive/redis/} ]; then
				if [ -z $(find_path make) ]; then
					do_fail "unable to find 'make' installed. To build Redis, make sure you have 'make' and C compiler installed."
				fi
				trace "Building redis..."
				(cd $redis_dir && make)
			fi
		fi
	else
		do_fail "failed to get '$archive' from '$url'."
	fi
}

es_start() {
	trace "Starting elasticsearch..."
	(cd $ES_PATH && ./bin/elasticsearch 2>../es.err 1>../es.log) &
}

es_stop() {
	trace "Stopping elasticsearch..."
	pid=$(ps aux | grep elasticsearch | awk '{print $2}')
	# trick to silently kill running process, without 'Killing...' stuff
	(kill $pid 2>&1) >/dev/null
}

redis_start() {
	trace "Starting redis..."
	(cd $REDIS_PATH && ./src/redis-server 2>../redis.err 1>../redis.log) &
}

redis_stop() {
	trace "Stopping redis..."
	pid=$(ps aux | grep redis-server | awk '{print $2}')
	(kill $pid 2>&1) >/dev/null
}

dynamodb_start() {
	trace "Starting dynamodb..."
	cmd="java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb"
	(cd $DYNAMODB_PATH && $cmd  2>../dynamodb.err 1>../dynamodb.log) &
}

dynamodb_stop() {
	trace "Stopping dynamodb..."
	pid=$(ps aux | grep DynamoDBLocal | awk '{print $2}')
	(kill $pid 2>&1) >/dev/null
}

help() {
	cat << EOF
Usage: $0 [options]
Start/stop necessary databases, targeting mainly for tests.
The script will download and compile dependent databases, if required.

Options:

  start - start everything
  stop  - stop everything
  help  - this help

Environment variables:

  WORK_PATH     - location where databases will be download and unpacked (default is "$WORK_PATH")
  ES_PATH       - absolute path of ElasticSearch installation. If set, ElasticSearch will not be downloaded.
  REDIS_PATH    - absolute path of Redis installation. If set, Redis will not be downloaded.
  DYNAMODB_PATH - absolute path of DynamoDB installation. If set, DynamoDB will not be downloaded.

EOF
}

start_everything() {
	# operate everything in working directory
	mkdir -p $WORK_PATH && cd $WORK_PATH

	# check necessary variables and download archives if necessary
	if [ -z $ES_PATH ]; then
		[ ! -d $es_dir ] && do_download $es_url $es_checksum
		export ES_PATH=$es_dir
	fi

	if [ -z $REDIS_PATH ]; then
		[ ! -d $redis_dir ] && do_download $redis_url $redis_checksum
		export REDIS_PATH=$redis_dir
	fi

	if [ -z $DYNAMODB_PATH ]; then
		[ ! -d $dynamodb_dir ] && do_download $dynamodb_url $dynamodb_checksum
		export DYNAMODB_PATH=$dynamodb_dir
	fi

	es_start
	redis_start
	dynamodb_start
}

stop_everything() {
	cd $WORK_PATH

	es_stop
	redis_stop
	dynamodb_stop
}

case "$1" in
	"start")
		start_everything
		;;
	"stop")
		stop_everything
		;;
	*)
		help
		;;
esac
