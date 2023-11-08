#!/bin/bash

# Fetch health route status json
json=$(curl -sS --max-time 10 'http://localhost:3333/health')
if [ $? -ne 0 ]; then
  echo "Health route is not responding"
  exit 1
fi

# Check severity reported by health route, exit with 1 if severity is other than "non fatal"
severity=$(echo "$json" | jq -r .severity)
if [ "$severity" != "non fatal" ]; then
  echo "Health route reports other than a non fatal severity"
  exit 1
fi

# Everything OK
exit 0
