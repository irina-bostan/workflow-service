# Contributors

This file records the people and tools that contributed to workflow-service.

## Authors

- **Irina Bostan** &lt;irina.bostan0@gmail.com&gt; — design, implementation, review

## AI assistance

A substantial portion of the code, infrastructure, tests, and documentation in this repository was produced with the assistance of **Anthropic Claude Code** (Claude Opus 4.x). Every AI-generated artifact was reviewed for correctness, security, and architectural fit by the author before commit. Architectural decisions (profile separation, transactional outbox, idempotency design, autoscaling triggers, schema choices) are the author's; AI was the implementation accelerant.

Where AI helped most:
- Spring Boot scaffolding, MapStruct mappers, Jakarta validation
- AWS CDK stack (ECS Fargate, ALB, RDS, ElastiCache, SQS, alarms)
- Test layers across all four levels (unit/slice, integration, BDD, JMeter)
- React Native UI scaffolding and tests
- Documentation: ARCHITECTURE, PERFORMANCE, sequence + component diagrams

Where the author drove and AI mostly translated:
- Stack and instance class choices, autoscaling triggers, SLO targets
- Trade-off calls (e.g. retain SENT outbox rows for audit vs delete on send)
- Scope decisions on what to defer

## How to add yourself

If you contribute via a pull request, add a line under "Authors" with your name and contact in the same format. Squash commits into a single, atomic change with a Co-Authored-By trailer if you prefer to keep your email out of the file body.
