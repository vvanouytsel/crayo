#!/bin/sh
# Checks every new commit between two points. Usage: check-commits.sh <from> <to>
from="$1"
to="$2"
status=0

case "$from" in
  ""|0000000000000000000000000000000000000000) range="$to~1..$to" ;;
  *) range="$from..$to" ;;
esac

for commit in $(git rev-list --no-merges "$range" 2>/dev/null); do
  subject=$(git log -1 --format=%s "$commit")
  "$(dirname "$0")/check-subject.sh" "$subject" || status=1
done

exit $status
