#!/bin/sh
set -e

echo "Setting up LocalStack resources..."

# Create SQS FIFO DLQ first (referenced by main queue's redrive policy)
aws sqs create-queue \
  --queue-name booking-events-dlq.fifo \
  --attributes FifoQueue=true \
  --region eu-west-1

DLQ_ARN=$(aws sqs get-queue-attributes \
  --queue-url "http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events-dlq.fifo" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text \
  --region eu-west-1)

echo "Created SQS FIFO DLQ: booking-events-dlq.fifo (arn=$DLQ_ARN)"

# Create SQS FIFO queue for booking events with redrive to DLQ
aws sqs create-queue \
  --queue-name booking-events.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"3\"}" \
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

aws ssm put-parameter \
  --name /workflow-service/sqs-booking-events-dlq-url \
  --value "http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/booking-events-dlq.fifo" \
  --type String \
  --overwrite

echo "SSM parameters created."
echo "LocalStack setup complete."
