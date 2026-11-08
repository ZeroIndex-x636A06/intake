# Backups

Intake keeps everything on the phone. Two ways to get a copy off it:

1. **Export to a file** (More → Backup & sync → Export). One JSON file with every
   row of every table, soft-deleted rows included. With a passphrase it is
   encrypted (AES-256-GCM, key from PBKDF2-SHA256); without one it is plain JSON.
   Import shows what will change before writing anything and never deletes.
2. **Nightly backup to your own server** over WebDAV, SFTP or SMB. Always
   encrypted with the backup passphrase. Runs around 2 AM when the phone has a
   network and the battery is not low; retried up to three times if the server
   was unreachable. The newest 30 backups are kept; older files matching
   `intake-backup-YYYYMMDD-HHMMSS.intake` are removed. Nothing else in the
   folder is ever touched.

Each upload is written as `<name>.partial` and renamed when complete, so an
interrupted run never leaves a truncated file that looks like a backup.

## What is stored where

| Item | Where |
| --- | --- |
| Server address, path, username, share, pinned SFTP key | App settings (DataStore) |
| Server password, SFTP private key, backup passphrase | Encrypted preferences (`intake_backup_secrets`), Android Keystore AES-256-GCM |
| History of runs (time, size, destination, error) | `backup_run` table |

None of the secrets is in an export, a log, an error message or Android's own
cloud backup (`allowBackup=false`, all domains excluded).

The passphrase has to be on the phone for an unattended 2 AM run. It protects
the copy on the server: whoever gets the NAS or the cloud folder gets
ciphertext. Keep a copy of the passphrase somewhere other than the phone, or a
backup cannot be restored after the phone is lost.

## Server setup

### WebDAV (Nextcloud, ownCloud, Synology, QNAP, Apache, rclone)

- Address: the WebDAV root, e.g. `https://cloud.example.net/remote.php/dav/files/<user>`
  for Nextcloud, or `https://nas.local:5006` for Synology's WebDAV Server package.
- Must be `https://`. Plain `http://` is accepted only for an address that is
  plainly local: a private or loopback IP, or a `.local` / `.lan` / `.home` /
  `.internal` name.
- For Nextcloud use an app password, not your login password.

### SFTP (any OpenSSH server)

- Host and port (22 unless changed), a path such as `/home/<user>/intake`.
- Password, or paste an OpenSSH/PEM private key (unencrypted; the key itself is
  stored encrypted on the phone).
- The first **Save and test connection** shows the server's key fingerprint.
  Compare it on the server with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`
  and trust it only if it matches. From then on, a different key stops every run
  before anything is sent.

### SMB (Windows share, Samba, most NAS)

- Host, share name, path inside the share, username, password, optional domain.
- SMB 2 or 3 with message signing required. SMB 1 is not supported.

## Restoring

More → Backup & sync → Import, pick the `.intake` (or `.json`) file, enter the
passphrase. To restore a server backup, copy the file to the phone first (from
the NAS app, Files, or a browser).

## Testing the transports

`RemoteStoreIntegrationTest` runs put / list / overwrite / remove and the SFTP
host-key pinning against real servers. It is skipped unless
`INTAKE_REMOTE_IT=1`. The servers it expects, as used during development:

```sh
# WebDAV on 127.0.0.1:8099/dav, user caleb / dav-test-pw
pip install wsgidav cheroot
wsgidav --host 127.0.0.1 --port 8099 --root ./dav-root   # plus a simple_dc user mapping

# SFTP on 127.0.0.1:2222, user bk / sftp-test-pw
sudo useradd -m bk && echo 'bk:sftp-test-pw' | sudo chpasswd
sudo /usr/sbin/sshd -p 2222 -o ListenAddress=127.0.0.1 -o PasswordAuthentication=yes

# SMB on 127.0.0.1:445, share "backup" owned by bk
sudo smbpasswd -a bk   # same password
# smb.conf: [backup] path = /srv/intake-smb, read only = no, valid users = bk

INTAKE_REMOTE_IT=1 ./gradlew :app:testDebugUnitTest --tests '*RemoteStoreIntegrationTest*'
```
