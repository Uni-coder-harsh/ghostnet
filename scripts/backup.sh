#!/usr/bin/env bash
# GhostNet Database Backup Utility
# Creates an atomic snapshot of data/ghostnet.db into data/backups/

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

DB_FILE="data/ghostnet.db"
BACKUP_DIR="data/backups"

mkdir -p "$BACKUP_DIR"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
TARGET="$BACKUP_DIR/ghostnet_backup_${TIMESTAMP}.db"

echo "Creating point-in-time database snapshot..."
if [ -f "$DB_FILE" ]; then
    cp "$DB_FILE" "$TARGET"
    echo "Backup completed: $TARGET ($(stat -c%s "$TARGET") bytes)"
else
    echo "Error: Database file $DB_FILE does not exist yet."
    exit 1
fi
