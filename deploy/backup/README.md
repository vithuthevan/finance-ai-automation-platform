# Backup scripts

Operator scripts for PostgreSQL and document backup/restore. **Not executed automatically** until configured on the production host after deployment. Recovery has **not** been production-validated.

## Quick start (post-deployment)

```bash
cd /opt/finance-ai-automation-platform
cp deploy/backup/backup.env.example deploy/backup/backup.env
# Edit backup.env — set BACKUP_ROOT, BACKUP_OFFSITE_ENABLED, BACKUP_S3_URI

chmod +x deploy/backup/*.sh deploy/backup/lib/common.sh
sudo mkdir -p /var/backups/finance-platform /var/log/finance-platform

# Manual test (does not overwrite production data)
sudo deploy/backup/run-backup.sh
```

## Scripts

| Script | Purpose |
|--------|---------|
| `run-backup.sh` | Full backup orchestration (non-zero exit on any failed step) |
| `backup-postgres.sh` | `pg_dump` → timestamped `.sql.gz` (invalid dumps discarded) |
| `backup-documents-local.sh` | Tar archive of the `finance_uploads` volume |
| `sync-backups-offsite.sh` | Copy backups to S3 / rclone remote |
| `restore-postgres.sh` | Restore DB into an empty recovery environment |
| `restore-documents-local.sh` | Restore local document volume |
| `verify-restore.sh` | Read-only health + business-data checks |

## Automation (choose one after go-live)

- systemd: `deploy/backup/systemd/` (`finance-platform-backup.timer` + `OnFailure` alert unit)
- cron: `deploy/backup/cron.example`

## Documentation

- [BACKUP_RECOVERY_IMPLEMENTATION.md](../../docs/reports/BACKUP_RECOVERY_IMPLEMENTATION.md) — implementation guide
- [DISASTER_RECOVERY_RUNBOOK.md](../../docs/reports/DISASTER_RECOVERY_RUNBOOK.md) — scenario playbooks
- [BACKUP_DISASTER_RECOVERY_AUDIT.md](../../BACKUP_DISASTER_RECOVERY_AUDIT.md) — persistence and risk audit
