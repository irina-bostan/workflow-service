import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as actions from 'aws-cdk-lib/aws-cloudwatch-actions';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export interface WorkflowServiceStackProps extends cdk.StackProps {
  environment: string;
}

export class WorkflowServiceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: WorkflowServiceStackProps) {
    super(scope, id, props);

    const isProd = props.environment === 'prod';

    // ─────────────────────────── VPC ────────────────────────────
    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
      natGateways: isProd ? 2 : 1,
    });

    // ─────────────────────────── ECR ────────────────────────────
    const repository = ecr.Repository.fromRepositoryName(this, 'Repository', 'workflow-service');

    // ─────────────────────────── RDS ────────────────────────────
    const dbSecurityGroup = new ec2.SecurityGroup(this, 'DbSg', { vpc });

    const database = new rds.DatabaseInstance(this, 'Database', {
      engine: rds.DatabaseInstanceEngine.postgres({ version: rds.PostgresEngineVersion.VER_16 }),
      // Prod: non-burstable r6g.large for stable perf at sustained 100 rps + headroom for scale-out
      // (10 tasks × 20 Hikari conn = 200 conn vs t4g.medium's ~75 limit).
      // Non-prod: t4g.medium is fine — bursty workload, lower cost.
      instanceType: isProd
        ? ec2.InstanceType.of(ec2.InstanceClass.R6G, ec2.InstanceSize.LARGE)
        : ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MEDIUM),
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [dbSecurityGroup],
      multiAz: isProd,
      storageEncrypted: true,
      deletionProtection: isProd,
      databaseName: 'workflowdb',
      credentials: rds.Credentials.fromGeneratedSecret('workflow'),
    });

    // ─────────────────────────── ElastiCache Redis ──────────────
    const redisSg = new ec2.SecurityGroup(this, 'RedisSg', { vpc });

    const redisSubnetGroup = new elasticache.CfnSubnetGroup(this, 'RedisSubnetGroup', {
      description: 'workflow-service redis subnet group',
      subnetIds: vpc.privateSubnets.map(s => s.subnetId),
    });

    const redis = new elasticache.CfnCacheCluster(this, 'Redis', {
      engine: 'redis',
      cacheNodeType: 'cache.t4g.micro',
      numCacheNodes: 1,
      cacheSubnetGroupName: redisSubnetGroup.ref,
      vpcSecurityGroupIds: [redisSg.securityGroupId],
    });

    // ─────────────────────────── SQS FIFO ───────────────────────
    const bookingEventsDlq = new sqs.Queue(this, 'BookingEventsDlq', {
      queueName: 'booking-events-dlq.fifo',
      fifo: true,
    });

    const bookingEventsQueue = new sqs.Queue(this, 'BookingEventsQueue', {
      queueName: 'booking-events.fifo',
      fifo: true,
      contentBasedDeduplication: true,
      deadLetterQueue: { queue: bookingEventsDlq, maxReceiveCount: 3 },
      visibilityTimeout: cdk.Duration.seconds(30),
    });

    // ─────────────────────────── SSM Parameters ─────────────────
    new ssm.StringParameter(this, 'SqsQueueUrl', {
      parameterName: '/workflow-service/sqs-booking-events-url',
      stringValue: bookingEventsQueue.queueUrl,
    });

    // ─────────────────────────── ECS Cluster ────────────────────
    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc,
      containerInsights: true,
    });

    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: '/ecs/workflow-service',
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
    });

    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      memoryLimitMiB: 4096,
      cpu: 2048,
    });

    // ─────────────────────────── ADOT collector sidecar ─────────
    // Receives OTLP from the app on localhost:4318 and forwards traces → X-Ray,
    // metrics (EMF) → CloudWatch. The default config bundled with the image
    // (/etc/ecs/ecs-default-config.yaml) wires both exporters out of the box.
    const otelContainer = taskDefinition.addContainer('otel-collector', {
      image: ecs.ContainerImage.fromRegistry('public.ecr.aws/aws-observability/aws-otel-collector:latest'),
      command: ['--config=/etc/ecs/ecs-default-config.yaml'],
      essential: false,
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'otel-collector', logGroup }),
      portMappings: [
        { containerPort: 4317, protocol: ecs.Protocol.TCP },  // OTLP gRPC
        { containerPort: 4318, protocol: ecs.Protocol.TCP },  // OTLP HTTP
      ],
    });

    const appContainer = taskDefinition.addContainer('WorkflowService', {
      image: ecs.ContainerImage.fromEcrRepository(repository, 'latest'),
      portMappings: [{ containerPort: 8080 }],
      environment: {
        SPRING_PROFILES_ACTIVE: 'prod',
        AWS_REGION: this.region,
        // App pushes OTLP to the sidecar over loopback — same task, shared network namespace.
        OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: 'http://localhost:4318/v1/traces',
        OTEL_EXPORTER_OTLP_METRICS_ENDPOINT: 'http://localhost:4318/v1/metrics',
        OTEL_SERVICE_NAME: 'workflow-service',
      },
      secrets: {
        DB_URL: ecs.Secret.fromSsmParameter(
          ssm.StringParameter.fromStringParameterName(this, 'DbUrl', '/workflow-service/db-url')
        ),
      },
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'workflow-service', logGroup }),
      healthCheck: {
        // Liveness only — restart the container only if the JVM is unhealthy.
        // External-dependency outages do not restart pods.
        command: ['CMD-SHELL', 'wget -qO- http://localhost:8080/actuator/health/liveness || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        startPeriod: cdk.Duration.seconds(60),
        retries: 3,
      },
    });

    // App starts only after the collector is up, so first-request traces aren't dropped.
    appContainer.addContainerDependencies({
      container: otelContainer,
      condition: ecs.ContainerDependencyCondition.START,
    });

    // Grant permissions
    database.secret?.grantRead(taskDefinition.taskRole);
    bookingEventsQueue.grantSendMessages(taskDefinition.taskRole);
    // Health indicator calls GetQueueAttributes; outbox relay only sends. Both covered:
    bookingEventsQueue.grant(taskDefinition.taskRole, 'sqs:GetQueueAttributes');

    // ADOT collector pushes to X-Ray + CloudWatch (metrics & logs).
    taskDefinition.taskRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('AWSXRayDaemonWriteAccess'),
    );
    taskDefinition.taskRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('CloudWatchAgentServerPolicy'),
    );

    // ─────────────────────────── ECS Service + ALB ──────────────
    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      vpc,
      internetFacing: true,
    });

    const listener = alb.addListener('HttpsListener', {
      port: 443,
      open: true,
    });

    const service = new ecs.FargateService(this, 'Service', {
      cluster,
      taskDefinition,
      desiredCount: 2,
      minHealthyPercent: 50,
      maxHealthyPercent: 200,
      assignPublicIp: false,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    const targetGroup = listener.addTargets('ServiceTargets', {
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [service],
      healthCheck: {
        // Readiness — pull pod from LB rotation if DB/Redis are unreachable.
        path: '/actuator/health/readiness',
        interval: cdk.Duration.seconds(30),
        healthyHttpCodes: '200',
      },
    });

    // Allow ECS → RDS
    dbSecurityGroup.addIngressRule(
      service.connections.securityGroups[0],
      ec2.Port.tcp(5432),
      'Allow ECS to RDS'
    );

    // Allow ECS → Redis
    redisSg.addIngressRule(
      service.connections.securityGroups[0],
      ec2.Port.tcp(6379),
      'Allow ECS to Redis'
    );

    // ─────────────────────────── Auto Scaling ───────────────────
    // Primary trigger: ALB requests-per-target. The booking flow is I/O-bound (DB +
    // Redis + Multi-AZ commit), so CPU stays low even at sustained peak — CPU as a
    // primary signal would only fire at ~2.4× peak load. RequestCountPerTarget is
    // direct and predictable: target=80/task gives ~60% headroom over the per-task
    // share of 100 rps peak (50/task), and scales smoothly past peak.
    //
    // Secondary trigger: CPU at 80%, kept as a safety net for CPU-bound regressions
    // (serialization bugs, runaway loops, log flood). Won't fire under normal traffic.
    // ECS takes the max of the two policies' recommendations.
    const scaling = service.autoScaleTaskCount({ minCapacity: 2, maxCapacity: 10 });

    scaling.scaleOnRequestCount('RequestCountScaling', {
      targetGroup,
      requestsPerTarget: 80,
      scaleInCooldown: cdk.Duration.seconds(300),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 80,
      scaleInCooldown: cdk.Duration.seconds(300),
      scaleOutCooldown: cdk.Duration.seconds(60),
    });

    // ─────────────────────────── CloudWatch Alarms ──────────────
    const alarmTopic = new sns.Topic(this, 'AlarmTopic', {
      displayName: `workflow-service-${props.environment}-alarms`,
    });

    new cloudwatch.Alarm(this, 'HighCpuAlarm', {
      metric: service.metricCpuUtilization({ period: cdk.Duration.minutes(5) }),
      threshold: 80,
      evaluationPeriods: 2,
      alarmName: 'workflow-service-ecs-cpu',
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    }).addAlarmAction(new actions.SnsAction(alarmTopic));

    // ─────────────────────────── Outputs ────────────────────────
    new cdk.CfnOutput(this, 'AlbDnsName', { value: alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'BookingQueueUrl', { value: bookingEventsQueue.queueUrl });
  }
}
