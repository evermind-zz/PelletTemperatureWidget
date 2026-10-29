#!/bin/bash

# Configuration
# Leave empty if running inside a local git directory. 
# Set to "owner/repo" if running from outside the project directory.
REPO="" 
OUTPUT_DIR="./reports"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

echo "Starting GitHub Action log sync loop..."
echo "Logs will be saved to: $OUTPUT_DIR"

# Set up repository context flag if explicitly configured above
REPO_FLAG=""
if [ -n "$REPO" ]; then
    REPO_FLAG="-R $REPO"
fi

while true; do
    echo "=========================================="
    echo "Checking for runs at $(date)"
    echo "=========================================="

    # 1. Fetch recent runs natively using gh api
    # Uses implicit context tracking for {owner}/{repo}
    RUNS_JSON=$(gh api $REPO_FLAG "/repos/{owner}/{repo}/actions/runs" --jq '.workflow_runs[] | {id: .id, name: .name}')

    if [ -z "$RUNS_JSON" ]; then
        echo "No runs found or failed to connect to GitHub API."
    else
        # Process each run item found in the response
        echo "$RUNS_JSON" | jq -c '.' | while read -r run; do
            RUN_ID=$(echo "$run" | jq -r '.id')
            RUN_NAME=$(echo "$run" | jq -r '.name')
            
            # Sanitize the workflow name to make it legal for filesystem paths
            SAFE_NAME=$(echo "$RUN_NAME" | sed 's/[^a-zA-Z0-9_-]/_/g')
            FILE_NAME="${RUN_ID}_${SAFE_NAME}"
            FILE_PATH="$OUTPUT_DIR/$FILE_NAME"

            # 2. Check if the log file has already been fetched

            ls  "$FILE_PATH" -laht 
            if [ -d "$FILE_PATH" ]; then
                echo "[-] Already fetched: $FILE_NAME (Skipping)"
                continue
            fi

            echo "[+] New run detected! Fetching logs for ID $RUN_ID..."

            # 3. Pull the zip payload using gh api directly
            TEMP_ZIP="$OUTPUT_DIR/${RUN_ID}_temp.zip"
            #echo "gh api $REPO_FLAG "/repos/{owner}/{repo}/actions/runs/${RUN_ID}/logs" > "$TEMP_ZIP" 2>/dev/null"
            gh api $REPO_FLAG "/repos/{owner}/{repo}/actions/runs/${RUN_ID}/logs" > "$TEMP_ZIP" 2>/dev/null

            # Check if the download succeeded and the file is not empty
            if [ -s "$TEMP_ZIP" ]; then
                # Unzip step logs flat into a single combined .log target file
                python3 -m zipfile -e "$TEMP_ZIP" "$FILE_PATH"
                rm -f "$TEMP_ZIP"
                echo "[✓] Saved: $FILE_NAME"
            else
                echo "[!] Failed to download logs for run $RUN_ID (It might still be active)."
                rm -f "$TEMP_ZIP"
            fi
            exit
        done
    fi

    # 4. Randomize the sleep duration around a 100-second baseline (85 to 115 seconds)
    SLEEP_TIME=$((85 + RANDOM % 31))
    echo "Sleeping for ${SLEEP_TIME} seconds..."
    sleep $SLEEP_TIME
done

