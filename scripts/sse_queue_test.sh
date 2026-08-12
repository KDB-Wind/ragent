#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:9090/api/ragent}"
QUESTION="${QUESTION:-你是谁？你是 ChatGPT 么？}"
CONCURRENCY="${CONCURRENCY:-3}"
TOKEN="${TOKEN:-}"
CONVERSATION_ID="${CONVERSATION_ID:-}"
LOG_DIR="${LOG_DIR:-$(pwd)/logs}"
CURL_TIMEOUT_SECONDS="${CURL_TIMEOUT_SECONDS:-330}"
REQUEST_ID_PREFIX="${REQUEST_ID_PREFIX:-sse-$(date -u +%Y%m%dT%H%M%SZ)-$$}"

for command_name in curl perl grep; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command is missing: %s\n' "$command_name" >&2
    exit 2
  fi
done

if ! [[ "$CONCURRENCY" =~ ^[1-9][0-9]*$ ]]; then
  printf 'CONCURRENCY must be a positive integer\n' >&2
  exit 2
fi
if ! [[ "$CURL_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'CURL_TIMEOUT_SECONDS must be a positive integer\n' >&2
  exit 2
fi

mkdir -p "$LOG_DIR"
printf 'endpoint=%s concurrency=%s log_dir=%s request_id_prefix=%s\n' \
  "$BASE_URL" "$CONCURRENCY" "$LOG_DIR" "$REQUEST_ID_PREFIX"

auth_header=()
if [[ -n "$TOKEN" ]]; then
  auth_header=(-H "Authorization: ${TOKEN}")
fi
conversation_args=()
if [[ -n "$CONVERSATION_ID" ]]; then
  conversation_args=(--data-urlencode "conversationId=${CONVERSATION_ID}")
fi

pids=()
for i in $(seq 1 "$CONCURRENCY"); do
  (
    request_id="${REQUEST_ID_PREFIX}-${i}"
    raw_file="${LOG_DIR}/ragent_chat_${i}.raw.sse"
    log_file="${LOG_DIR}/ragent_chat_${i}.log"
    header_file="${LOG_DIR}/ragent_chat_${i}.headers"
    stderr_file="${LOG_DIR}/ragent_chat_${i}.stderr.log"
    status_file="${LOG_DIR}/ragent_chat_${i}.status"

    set +e
    curl -G -N --no-buffer --silent --show-error --fail-with-body \
      --connect-timeout 10 --max-time "$CURL_TIMEOUT_SECONDS" \
      -D "$header_file" \
      -H 'Accept: text/event-stream' \
      -H "X-Request-ID: ${request_id}" \
      "${auth_header[@]}" \
      --data-urlencode "question=${QUESTION}_${i}" \
      "${conversation_args[@]}" \
      --data-urlencode 'deepThinking=false' \
      "${BASE_URL}/rag/v3/chat" 2>"$stderr_file" | \
      tee "$raw_file" | \
      perl -ne 'use Time::HiRes qw(gettimeofday); use POSIX qw(strftime); chomp; my ($s,$us)=gettimeofday; my $ts=strftime("%Y-%m-%d %H:%M:%S", localtime($s)); printf "[%s.%03d] %s\n", $ts, $us/1000, $_;' \
      >"$log_file"
    curl_status=$?
    set -e

    result=0
    if (( curl_status != 0 )); then
      printf 'worker=%s request_id=%s curl_exit=%s\n' "$i" "$request_id" "$curl_status" >>"$stderr_file"
      result=1
    fi
    if ! grep -qi '^content-type:.*text/event-stream' "$header_file"; then
      printf 'worker=%s request_id=%s assertion=missing_sse_content_type\n' "$i" "$request_id" >>"$stderr_file"
      result=1
    fi
    if ! grep -q '^data:' "$raw_file"; then
      printf 'worker=%s request_id=%s assertion=missing_data_event\n' "$i" "$request_id" >>"$stderr_file"
      result=1
    fi
    if ! grep -q '^event: done' "$raw_file" || ! grep -Eq '^data:[[:space:]]*\[DONE\][[:space:]]*$' "$raw_file"; then
      printf 'worker=%s request_id=%s assertion=missing_done_event\n' "$i" "$request_id" >>"$stderr_file"
      result=1
    fi

    printf '%s\n' "$result" >"$status_file"
    if (( result == 0 )); then
      printf 'PASS worker=%s request_id=%s\n' "$i" "$request_id"
    else
      printf 'FAIL worker=%s request_id=%s stderr=%s\n' "$i" "$request_id" "$stderr_file" >&2
    fi
  ) &
  pids+=("$!")
done

for pid in "${pids[@]}"; do
  wait "$pid"
done

failed=0
for i in $(seq 1 "$CONCURRENCY"); do
  status_file="${LOG_DIR}/ragent_chat_${i}.status"
  if [[ ! -f "$status_file" ]] || [[ "$(<"$status_file")" != "0" ]]; then
    failed=$((failed + 1))
  fi
done

printf 'completed=%s failed=%s logs=%s\n' "$CONCURRENCY" "$failed" "$LOG_DIR"
if (( failed > 0 )); then
  exit 1
fi
