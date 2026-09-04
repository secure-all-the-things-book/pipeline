#!/usr/bin/env bash
set -euo pipefail


find  /home/runner/Desktop/target/MarkdownProducer/

export TOOLBOOK_API_URL=https://api.mytoolbook.ai

# GitHub Actions step; chapter files are named like 01-preface.md
auth=(-H "Authorization: Bearer $TOOLBOOK_API_KEY")
base="$TOOLBOOK_API_URL/toolbooks/bootiful-spring-ai"

curl --fail-with-body -X DELETE "${auth[@]}" "$base/chapters"


for chapter in /home/runner/Desktop/target/MarkdownProducer/*.md; do
	filename="${chapter##*/}"
	chapter_slug="$filename"
	curl --fail-with-body -X POST "${auth[@]}" -H 'Content-Type: text/markdown' --data-binary "@$chapter" "$base/chapters/$chapter_slug"
done

curl --fail-with-body -X POST "${auth[@]}" "$base/versions/promote"