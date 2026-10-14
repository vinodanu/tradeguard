"""Check Linux startup assets before a Docker build, including on Windows."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    "infra/app-entrypoint.sh",
    "infra/kafka-init.sh",
    "infra/mysql-init.sh",
    "infra/aws/host-bootstrap.sh",
    "scripts/start.sh",
    "scripts/aws-prepare.sh",
    "scripts/renew-tls.sh",
]


def check() -> None:
    failures = []
    for name in FILES:
        data = (ROOT / name).read_bytes()
        if b"\r" in data or not data.startswith(b"#!/bin/bash\n"):
            failures.append(name)
    if failures:
        raise SystemExit("Linux scripts require LF line endings: " + ", ".join(failures))
    print(f"PASS: {len(FILES)} Linux startup scripts use LF line endings")


if __name__ == "__main__":
    check()
