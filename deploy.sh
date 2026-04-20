#!/usr/bin/env bash
set -euo pipefail

SERVER="splitify@192.155.93.19"
REMOTE_JAR="/opt/splitify/splitify.jar"
LOCAL_JAR="build/libs/sorted-music-0.0.1-SNAPSHOT.jar"

echo ">> Building JAR (frontend + backend)..."
./gradlew clean build -x test

if [ ! -f "$LOCAL_JAR" ]; then
    echo "!! JAR not found at $LOCAL_JAR"
    exit 1
fi

echo ">> Uploading $LOCAL_JAR to $SERVER:$REMOTE_JAR..."
scp "$LOCAL_JAR" "$SERVER:$REMOTE_JAR"

echo ">> Restarting splitify service..."
ssh "$SERVER" "sudo systemctl restart splitify"

echo ">> Waiting 5s for service to boot..."
sleep 5

echo ">> Service status:"
ssh "$SERVER" "sudo systemctl status splitify --no-pager -l | head -n 20"

echo ">> Done. Check https://splitifyapp.app"
