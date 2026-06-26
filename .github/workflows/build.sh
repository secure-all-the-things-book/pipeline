#!/usr/bin/env bash
set -euo pipefail
mvn -B -DskipTests -U -f pom.xml clean install && java -jar target/*jar