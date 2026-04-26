# CI/CD Pipeline

## Overview

The pipeline runs on GitHub Actions and follows a linear promotion model: test → build → staging → prod. Production deployment requires a manual approval gate via a GitHub Environment protection rule.

## Pipeline Stages

### 1. Test (`test` job)
Triggers on every push and pull request.

```
mvn test         # Unit + slice tests (H2, no Docker)
mvn verify       # Integration tests (Testcontainers — PostgreSQL + Redis)
```

Artifacts: Surefire + Failsafe XML reports uploaded to GitHub Actions.

**Unit tests** (`mvn test`) run without Docker — H2 in-memory, simple cache. They complete in under 30 seconds.

**Integration tests** (`mvn verify`) spin up real PostgreSQL 16 and Redis 7 containers via Testcontainers. The `BookingIntegrationTest` covers: create booking end-to-end, idempotency deduplication, unknown employee 404, hotel-without-returnDate 422, and paginated list retrieval.

### 2. Build (`build` job)
Runs after `test` passes, on push to `main` only.

```
mvn package -DskipTests
docker build -t $ECR_REGISTRY/workflow-service:$GITHUB_SHA .
docker push $ECR_REGISTRY/workflow-service:$GITHUB_SHA
docker tag  $ECR_REGISTRY/workflow-service:$GITHUB_SHA \
            $ECR_REGISTRY/workflow-service:latest
docker push $ECR_REGISTRY/workflow-service:latest
```

Images are tagged with the immutable git SHA and `latest`. ECR image scanning is enabled.

### 3. Deploy Staging (`deploy-staging` job)
Runs after `build` passes, on push to `main` only.

```
aws ecs update-service \
  --cluster workflow-staging \
  --service workflow-service \
  --force-new-deployment

aws ecs wait services-stable \
  --cluster workflow-staging \
  --services workflow-service

# Smoke test
curl -f https://staging.techquarter.com/actuator/health
```

ECS performs a rolling update: new tasks start before old ones are stopped. ALB health checks on `/actuator/health` gate task registration.

### 4. Deploy Production (`deploy-prod` job)
Requires manual approval via the `production` GitHub Environment protection rule.

```
aws ecs update-service \
  --cluster workflow-prod \
  --service workflow-service \
  --force-new-deployment

aws ecs wait services-stable \
  --cluster workflow-prod \
  --services workflow-service
```

Deployment settings: `minimumHealthyPercent=50`, `maximumPercent=200` (rolling). Post-deploy CloudWatch alarms are checked — if any alarm is in `ALARM` state, the pipeline fails and triggers rollback by redeploying the previous task definition revision.

## Rollback

ECS task definition revisions are immutable. To rollback:
```
aws ecs update-service \
  --cluster workflow-prod \
  --service workflow-service \
  --task-definition workflow-service:<previous-revision>
```

This is automated in the pipeline if post-deploy health checks fail.

## Environment Variables (GitHub Secrets)

| Secret | Used In |
|---|---|
| `AWS_ACCESS_KEY_ID` | build, deploy jobs |
| `AWS_SECRET_ACCESS_KEY` | build, deploy jobs |
| `AWS_REGION` | build, deploy jobs |
| `ECR_REGISTRY` | build job |
| `STAGING_CLUSTER` | deploy-staging job |
| `PROD_CLUSTER` | deploy-prod job |
