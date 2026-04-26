#!/bin/sh
set -e

echo "Setting up LocalStack resources..."

# Create SQS FIFO queue for booking events
aws sqs create-queue \
  --queue-name booking-events.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true \
  --region eu-west-1

echo "Created SQS FIFO queue: booking-events.fifo"

# Store SSM parameters (mirrors prod ECS task definition env vars)
aws ssm put-parameter \
  --name /workflow-service/db-url \
  --value "jdbc:postgresql://postgres:5432/workflowdb" \
  --type String \
  --overwrite

aws ssm put-parameter \
  --name /workflow-service/redis-host \
  --value "redis" \
  --type String \
  --overwrite

aws ssm put-parameter \
  --name /workflow-service/sqs-booking-events-url \
  --value "http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events.fifo" \
  --type String \
  --overwrite

echo "SSM parameters created."
echo "LocalStack setup complete."
