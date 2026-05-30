#!/bin/sh
set -e

export CONSUMER_API_KEY="${CONSUMER_API_KEY:-dev-consumer}"

envsubst '${CONSUMER_API_KEY}' \
  < /etc/nginx/conf.d/default.conf.tmpl \
  > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
