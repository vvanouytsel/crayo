#!/bin/sh
# Checks that one commit subject follows the Conventional Commits format:
#   type(optional scope): short description
# Used by the local commit hook and by the GitHub check.

subject="$1"

case "$subject" in
  Merge\ *|Revert\ *|fixup!*|squash!*) exit 0 ;;
esac

if printf '%s' "$subject" | grep -Eq '^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9._/-]+\))?!?: .+'; then
  exit 0
fi

echo "Commit message does not follow Conventional Commits:" >&2
echo "  $subject" >&2
echo "Use: type(optional scope): description" >&2
echo "Types: feat fix docs style refactor perf test build ci chore revert" >&2
exit 1
