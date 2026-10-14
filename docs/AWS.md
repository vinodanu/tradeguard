# Gated AWS demonstration

No AWS resource has been provisioned by this implementation. The application is configured for an owner-controlled, temporary demonstration on an active Free plan with credits.

## Before provisioning

Record account plan, credit balance, credit expiry, Free-plan expiry, region, intended demo hours, and a teardown date. Estimate **compute + EBS + public IPv4 + transfer + monitoring** using current prices. Include persistent storage for the whole retention period, not just running hours. Keep at least 20% of credits in reserve. Confirm `m7i-flex.large` is allowed in your account/region and that the measured stack fits its memory.

Use `infra/aws/terraform.tfvars.example` as a starting point. Fill genuine billing values; `credits_verified` defaults to false. Terraform's checks are attestations and date/cost comparisons; they cannot independently inspect your credit balance or guarantee zero cost.

```sh
cd infra/aws
terraform init
terraform plan
terraform apply
```

The module creates a VPC, public subnet, internet gateway, one EC2 instance, one encrypted root volume, security group, instance role, and scoped GitHub deployment role. It creates no RDS, EKS, MSK, ALB, NAT gateway, or Elastic IP. It requires an existing GitHub OIDC provider ARN. Attach the generated owner-session policy only to your owner identity. Interactive Systems Manager sessions are restricted by owner IP; no SSH port is exposed.

The host installs Docker/Compose and schedules power-off at `teardown_at`. Shutdown reduces compute usage but does not remove EBS charges. Set a calendar reminder and destroy the entire module before expiry.

## Initialize the host

1. In an owner Systems Manager session, clone the public repository into `/opt/tradeguard`.
2. Install the bootstrap Python dependency in an isolated environment and run `scripts/bootstrap.py`.
3. Create an Atlas Free cluster in a nearby AWS region. Create separate `compliance` and `liveview` database users with only their database privileges. Add only the current EC2 public IP to its allowlist.
4. Add `ATLAS_COMPLIANCE_URI` and `ATLAS_LIVE_URI` to protected `.env`. Each URI must select its owning database and correct authentication database. URL-encode credential characters.
5. Add `DUCKDNS_SUBDOMAIN`, `DUCKDNS_TOKEN`, and `TLS_EMAIL`. Keep `.env` root-readable only.
6. Set `IMAGE_PREFIX=ghcr.io/your-user/your-repository` and `IMAGE_TAG` to a verified commit SHA. Publish images first; GHCR images must be accessible to the host.
7. Pull images with `docker compose -f compose.yaml -f compose.aws.yaml pull`.
8. Run `bash scripts/aws-prepare.sh`. It updates DNS, provisions the certificate, starts the AWS profile, and installs DNS/TLS renewal timers.
9. Run a browser smoke test over HTTPS, then inspect health, memory, disk, lag, and billing.

The DNS update script reports when the Atlas allowlist needs checking. An EC2 stop/start can change the public IP; update Atlas before restarting application traffic. It does not open Atlas access to the whole internet. The AWS profile excludes local MongoDB and uses two Atlas URIs.

## GitHub deployment

Configure the `aws-demo` GitHub environment and its variables: `AWS_DEPLOY_ROLE`, `AWS_REGION`, `AWS_INSTANCE_ID`. Use environment branch protection. The manual workflow accepts a verified commit SHA and an explicit completed cost-gate attestation. OIDC avoids stored AWS access keys. Deployment pulls that version, starts it over Systems Manager, and waits for health. Use the prior verified image SHA for application rollback.

No GitHub repository, workflow run, DNS account, Atlas account, or AWS credential is created automatically by the local implementation.

## Finish

Before expiry, export needed synthetic data, save the demonstration recording, and run `terraform destroy`. Check Billing and the EC2/VPC consoles for residual resources. Remove the Atlas allowlist entry and DNS record as appropriate. Remain on the Free plan; no automatic paid-plan upgrade is part of this workflow.
