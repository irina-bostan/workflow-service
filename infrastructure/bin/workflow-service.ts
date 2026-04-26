#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { WorkflowServiceStack } from '../lib/workflow-service-stack';

const app = new cdk.App();

const env = app.node.tryGetContext('env') ?? 'staging';

new WorkflowServiceStack(app, `WorkflowService-${env}`, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? 'eu-west-1',
  },
  environment: env,
  tags: {
    Service: 'workflow-service',
    Environment: env,
    ManagedBy: 'CDK',
  },
});
