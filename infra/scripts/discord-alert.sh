#!/usr/bin/env bash
# ============================================================
# Discord Alert Script for BekoLolek Plugin Factory
# ============================================================
# Receives AlertManager-style JSON on stdin and posts formatted
# alert embeds to a Discord webhook.
#
# Usage:
#   echo '{"alerts":[...]}' | DISCORD_WEBHOOK_URL=https://... ./discord-alert.sh
#   curl -X POST -d @payload.json http://localhost:9093/alert | ./discord-alert.sh
#
# Environment:
#   DISCORD_WEBHOOK_URL - Required. The Discord webhook URL to post to.
# ============================================================

set -euo pipefail

# Validate webhook URL
if [[ -z "${DISCORD_WEBHOOK_URL:-}" ]]; then
    echo "ERROR: DISCORD_WEBHOOK_URL environment variable is not set." >&2
    exit 1
fi

# Read JSON from stdin
INPUT=$(cat)

if [[ -z "$INPUT" ]]; then
    echo "ERROR: No input received on stdin." >&2
    exit 1
fi

# Check if jq is available
if ! command -v jq &>/dev/null; then
    echo "ERROR: jq is required but not installed." >&2
    exit 1
fi

# Parse alert count
ALERT_COUNT=$(echo "$INPUT" | jq -r '.alerts | length')

if [[ "$ALERT_COUNT" -eq 0 ]]; then
    echo "No alerts to process."
    exit 0
fi

# Build Discord embeds from alerts
EMBEDS="[]"

for i in $(seq 0 $((ALERT_COUNT - 1))); do
    ALERT=$(echo "$INPUT" | jq -r ".alerts[$i]")

    STATUS=$(echo "$ALERT" | jq -r '.status // "unknown"')
    SEVERITY=$(echo "$ALERT" | jq -r '.labels.severity // "info"')
    ALERTNAME=$(echo "$ALERT" | jq -r '.labels.alertname // "Unknown Alert"')
    SUMMARY=$(echo "$ALERT" | jq -r '.annotations.summary // "No summary provided"')
    DESCRIPTION=$(echo "$ALERT" | jq -r '.annotations.description // "No description provided"')
    INSTANCE=$(echo "$ALERT" | jq -r '.labels.instance // "N/A"')
    STARTS_AT=$(echo "$ALERT" | jq -r '.startsAt // "N/A"')

    # Color coding based on severity
    case "$SEVERITY" in
        critical)
            COLOR=15158332  # Red (#E74C3C)
            EMOJI="CRITICAL"
            ;;
        warning)
            COLOR=15105570  # Orange (#E67E22)
            EMOJI="WARNING"
            ;;
        info)
            COLOR=3447003   # Blue (#3498DB)
            EMOJI="INFO"
            ;;
        *)
            COLOR=9807270   # Gray (#959595)
            EMOJI="ALERT"
            ;;
    esac

    # If the alert is resolved, override color to green
    if [[ "$STATUS" == "resolved" ]]; then
        COLOR=3066993  # Green (#2ECC71)
        EMOJI="RESOLVED"
    fi

    # Build the embed JSON
    EMBED=$(jq -n \
        --arg title "[$EMOJI] $ALERTNAME" \
        --arg description "$SUMMARY" \
        --argjson color "$COLOR" \
        --arg instance "$INSTANCE" \
        --arg detail "$DESCRIPTION" \
        --arg started "$STARTS_AT" \
        --arg status "$STATUS" \
        '{
            title: $title,
            description: $description,
            color: $color,
            fields: [
                { name: "Status", value: $status, inline: true },
                { name: "Instance", value: $instance, inline: true },
                { name: "Details", value: $detail, inline: false },
                { name: "Started At", value: $started, inline: false }
            ],
            footer: {
                text: "BekoLolek Plugin Factory Monitoring"
            },
            timestamp: (now | todate)
        }')

    EMBEDS=$(echo "$EMBEDS" | jq --argjson embed "$EMBED" '. + [$embed]')
done

# Build the final Discord webhook payload
PAYLOAD=$(jq -n \
    --arg username "PluginFactory Alerts" \
    --argjson embeds "$EMBEDS" \
    '{
        username: $username,
        avatar_url: "https://cdn.discordapp.com/embed/avatars/0.png",
        embeds: $embeds
    }')

# Post to Discord webhook
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD" \
    "$DISCORD_WEBHOOK_URL")

if [[ "$HTTP_STATUS" -ge 200 && "$HTTP_STATUS" -lt 300 ]]; then
    echo "Successfully posted $ALERT_COUNT alert(s) to Discord (HTTP $HTTP_STATUS)."
else
    echo "ERROR: Failed to post to Discord. HTTP status: $HTTP_STATUS" >&2
    exit 1
fi
