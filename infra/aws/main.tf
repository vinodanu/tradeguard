data "aws_ami" "linux" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}
resource "aws_vpc" "demo" {
  cidr_block           = "10.44.0.0/16"
  enable_dns_hostnames = true
  tags                 = { Name = "tradeguard-demo" }
}
resource "aws_subnet" "demo" {
  vpc_id                  = aws_vpc.demo.id
  cidr_block              = "10.44.1.0/24"
  map_public_ip_on_launch = true
}
resource "aws_internet_gateway" "demo" { vpc_id = aws_vpc.demo.id }
resource "aws_route_table" "demo" {
  vpc_id = aws_vpc.demo.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.demo.id
  }
}
resource "aws_route_table_association" "demo" {
  subnet_id      = aws_subnet.demo.id
  route_table_id = aws_route_table.demo.id
}
resource "aws_security_group" "demo" {
  name   = "tradeguard-demo"
  vpc_id = aws_vpc.demo.id
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
resource "aws_iam_role" "host" {
  name_prefix        = "tradeguard-host-"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.host.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_instance_profile" "host" { role = aws_iam_role.host.name }
resource "aws_instance" "demo" {
  ami                    = data.aws_ami.linux.id
  instance_type          = "m7i-flex.large"
  subnet_id              = aws_subnet.demo.id
  vpc_security_group_ids = [aws_security_group.demo.id]
  iam_instance_profile   = aws_iam_instance_profile.host.name
  metadata_options { http_tokens = "required" }
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    encrypted             = true
    delete_on_termination = true
  }
  user_data = templatefile("${path.module}/host-bootstrap.sh", { teardown_at = "${formatdate("YYYY-MM-DD hh:mm:ss", var.teardown_at)} UTC" })
  tags      = { Name = "tradeguard-demo", TeardownAt = var.teardown_at }
  lifecycle {
    precondition {
      condition     = var.credits_verified && var.estimated_demo_cost_usd > 0 && var.credit_balance_usd * 0.8 >= var.estimated_demo_cost_usd
      error_message = "Deployment blocked: verify eligibility and costs within 80% of remaining credits."
    }
    precondition {
      condition     = timecmp(var.teardown_at, var.credit_expiry) < 0 && timecmp(var.teardown_at, var.free_plan_expiry) < 0 && timecmp(var.teardown_at, plantimestamp()) > 0
      error_message = "Teardown must be in the future and before both credit and Free-plan expiry."
    }
  }
}
resource "aws_iam_role" "deploy" {
  name_prefix        = "tradeguard-deploy-"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Federated = var.github_oidc_provider_arn }, Action = "sts:AssumeRoleWithWebIdentity", Condition = { StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com", "token.actions.githubusercontent.com:sub" = "repo:${var.github_repository}:environment:aws-demo" } } }] })
}
resource "aws_iam_role_policy" "deploy" {
  role = aws_iam_role.deploy.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ssm:SendCommand"], Resource = [aws_instance.demo.arn, "arn:aws:ssm:${var.region}::document/AWS-RunShellScript"] },
    { Effect = "Allow", Action = ["ssm:GetCommandInvocation"], Resource = "*" }
  ] })
}
output "instance_id" { value = aws_instance.demo.id }
output "public_ip" { value = aws_instance.demo.public_ip }
output "deploy_role_arn" { value = aws_iam_role.deploy.arn }

resource "aws_iam_policy" "owner_session" {
  name_prefix = "tradeguard-owner-session-"
  description = "Attach to the owner identity only. Interactive administration is restricted to owner IP."
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["ssm:StartSession"], Resource = [aws_instance.demo.arn, "arn:aws:ssm:${var.region}::document/SSM-SessionManagerRunShell"], Condition = { IpAddress = { "aws:SourceIp" = var.owner_cidr } } }
  ] })
}
output "owner_session_policy_arn" { value = aws_iam_policy.owner_session.arn }
