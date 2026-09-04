#!/usr/bin/env bash
# -E  : ERR trap is inherited by functions/subshells
# -e  : abort on any unchecked non-zero command
# -u  : abort on unset variables
# -o pipefail : a failure anywhere in a pipeline fails the pipeline (needed
#               because we pipe the app through tee below)
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

./mvnw -B -ntp -U -DskipTests -f pom.xml clean install

# `java -jar target/*jar` is unsafe: if the glob matches more than one file
# (-sources.jar, -plain.jar, ...) the extras are silently passed to the app as
# ARGUMENTS and the wrong jar runs. Resolve to exactly one executable jar.
shopt -s nullglob
candidates=(target/*.jar)
shopt -u nullglob

jars=()
for candidate in "${candidates[@]}"; do
	case "$candidate" in
	*-plain.jar | *-sources.jar | *-javadoc.jar | *-tests.jar) continue ;;
	esac
	jars+=("$candidate")
done

if [ "${#jars[@]}" -ne 1 ]; then
	echo "::error::expected exactly one runnable jar in target/, found ${#jars[@]}: ${jars[*]:-<none>}" >&2
	exit 1
fi

jar="${jars[0]}"
log="target/pipeline-run.log"
echo "running $jar"

set +e
java -jar "$jar" 2>&1 | tee "$log"
code=${PIPESTATUS[0]}
set -e

if [ "$code" -ne 0 ]; then
	echo "::error::$jar exited with status ${code}" >&2
	exit "$code"
fi

# Second net. Spring Batch swallows step exceptions into the JobExecution's
# status, so if anything ever regresses the exit-code plumbing in main() we
# still want a red build rather than a green one with a stack trace in it.
if grep -Eq 'and the following status: \[(FAILED|ABANDONED|UNKNOWN|STOPPED)\]' "$log"; then
	echo "::error::the batch job reported a non-COMPLETED status (see log above)" >&2
	grep -E 'and the following status: \[' "$log" >&2 || true
	exit 1
fi

echo "pipeline completed successfully"
