# CS203 Shared Database: Setup Guide

Our Postgres 16 database runs on an AWS EC2 server. You connect to it through
an **SSH tunnel**: an encrypted connection that makes the remote database
appear on your own laptop at `localhost:5433`. The database isn't exposed to
the internet, so the tunnel is the only way in.

```text
your laptop                          EC2 server
app / DB tool
  -> localhost:5433  ==== SSH ====>   localhost:5432 (Postgres)
```

You only need to do the one-time setup once. After that, the daily workflow
is two steps.

> **Windows: PowerShell or WSL?** Use the terminal you'll run the app from.
> - Running the app from **IntelliJ on Windows** → do everything in **PowerShell**.
> - Running the app in **WSL** (e.g. `./run.sh`) → do everything in a **WSL terminal**.
>
> A tunnel opened in PowerShell can't be reached from WSL, and the other way
> round. Windows and WSL also have separate `.ssh` folders, so generate the key
> in the one you'll use.

---

## 1. One-time setup

### Step 1: Generate your SSH key

Open **PowerShell** or **WSL** on Windows (see above), or **Terminal** on Mac, and run:

```bash
ssh-keygen -t ed25519 -C "your-name"
```

- Press **Enter** to accept the default location.
- Press **Enter** twice more to skip the passphrase, or set one if you want.
- If it says the file already exists and asks to **overwrite**, type `n`. You
  already have a key (maybe for GitHub). Keep it and use that one.

This creates **two files** in your `.ssh` folder:

| File | What it is | Share it? |
|---|---|---|
| `id_ed25519` | Your **private** key | **Never.** Don't send it to anyone. |
| `id_ed25519.pub` | Your **public** key | Yes, send this one to Jun Sheng |

### Step 2: Send your public key to Jun Sheng

Print it:

```bash
cat ~/.ssh/id_ed25519.pub
```

On Windows PowerShell, the file is at `C:\Users\<you>\.ssh\id_ed25519.pub`, and
the same command works.

It's one line starting with `ssh-ed25519 AAAA...`. Copy the **whole line** and
send it to Jun Sheng. Once your key has been added, Jun Sheng will DM you the
server IP and the database password. Wait for that before testing.

### Step 3: Create your SSH config

This saves you typing a long command every time.

Create a file called `config` (no file extension) in your `.ssh` folder:
- Mac / WSL: `~/.ssh/config`
- Windows PowerShell: `C:\Users\<you>\.ssh\config`

Paste this in, replacing `<SERVER_IP>` with the IP from Jun Sheng:

```
Host cs203-db
    HostName <SERVER_IP>
    User ubuntu
    IdentityFile ~/.ssh/id_ed25519
    LocalForward 5433 localhost:5432
    ServerAliveInterval 30
    ExitOnForwardFailure yes
```

- `ServerAliveInterval 30` stops an idle tunnel from silently dropping.
- `ExitOnForwardFailure yes` makes SSH stop with an error if port 5433 is
  already taken, instead of connecting without a tunnel.

### Step 4: Test the tunnel

```bash
ssh -N cs203-db
```

- First time only: it asks about the server's fingerprint. Type `yes`.
- Then it shows **nothing** and looks frozen. That means it's working. Leave
  this window open. Press Ctrl+C when you want to close the tunnel.
- Always include `-N`. Your key is tunnel-only, so connecting without `-N`
  won't give you a shell.

Then connect with any DB tool (IntelliJ Database panel, DBeaver, pgAdmin):

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5433` |
| Database | `postgres` |
| Username | `postgres` |
| Password | sent to you privately by Jun Sheng |

If it connects, you're set up. You should see tables such as `users`, `trips`
and `flyway_schema_history`.

---

## 2. Daily workflow

1. **Start the tunnel** and leave the window open:
   ```bash
   ssh -N cs203-db
   ```
2. **Run the Spring Boot app** with the **`dev` profile** and **`DB_PASSWORD`**
   set. Pick one way:

   | How you run it | What to do |
   |---|---|
   | **IntelliJ** (recommended) | Run → Edit Configurations → set **Active profiles** to `dev`, and add `DB_PASSWORD=<the password>` under **Environment variables**. |
   | **`./run.sh`** (Mac / WSL) | Put `DB_PASSWORD=<the password>` in `backend/.env` (copy `backend/.env.example` if you don't have one). `run.sh` uses `dev` automatically. |
   | **Maven**, from `backend/` | Same `backend/.env` as above, then `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |

   - In `.env`, **don't put quotes** around the password. They'd become part of it.
   - `backend/.env` is gitignored. Make sure `git status` never lists it.

The app connects to `localhost:5433`, which is the tunnel. It **won't start**
without the `dev` profile. That's deliberate, so nobody accidentally runs
against a temporary database.

`./mvnw test` doesn't need the tunnel. Tests run on an in-memory H2 database
and never touch the shared one.

---

## 3. Rules for the shared database

Everyone uses the **same** database, so one person's mistake breaks it for
everyone.

- **Schema changes only through Flyway migration files.** Never run
  `ALTER TABLE`, `CREATE TABLE` or `DROP TABLE` by hand.
- **Never commit the password.** Keep it in an environment variable or the
  gitignored `backend/.env`.
- **No `DROP`, `TRUNCATE` or bulk `DELETE`** without telling the team first.
  There are no automatic backups, so deleted data is gone for good.
- `ddl-auto` is set to `validate`. If your entity has a field with no matching
  column, the app **won't start**. That's on purpose: write the migration.

### Adding a migration

Flyway runs the files in `backend/src/main/resources/db/migration/` in order,
once each, and records them in the `flyway_schema_history` table. Only
`V1__init.sql` exists so far, so the next one is `V2`.

1. **Claim your V number in the group chat** first, so two people don't both
   make `V2`.
2. Create `V<number>__<description>.sql`, e.g. `V2__add_phone_to_users.sql`.
   That's a capital V and **two** underscores.
3. Write the SQL for just your change:
   ```sql
   ALTER TABLE users ADD COLUMN phone varchar(255);
   ```
4. Start the app with `dev`. Flyway applies your file, then Hibernate checks
   the entities match. Tests don't run migrations, so this is the real test.

- **Never edit a migration that's already been merged or run.** Flyway stores
  a checksum of each file and refuses to start if one changes. Fix mistakes
  with a new migration.
- **Adding an enum value** (e.g. to `InvitationStatus`, `TripRole` or
  `AppRole`) also needs a migration. The tables have `CHECK` constraints
  listing the allowed values, so you have to drop and re-add the constraint.

---

## 4. Troubleshooting

| Error | Cause | Fix |
|---|---|---|
| `Connection refused` on port 5433 | Tunnel isn't running, has dropped, or is open in the other terminal (PowerShell vs WSL) | Run `ssh -N cs203-db` in the same environment as the app, and keep it open |
| `password authentication failed` | Wrong or missing `DB_PASSWORD`, or quotes around it in `.env` | Paste the password again without quotes and check the env var is set |
| `Could not resolve placeholder 'DB_PASSWORD'` | `DB_PASSWORD` isn't set at all | Add it to your IntelliJ run config or `backend/.env` |
| `Failed to configure a DataSource: 'url' attribute is not specified` | No active profile | Set the active profile to `dev` |
| `Permission denied (publickey)` | Your key isn't on the server, or SSH is using a different key | Confirm with Jun Sheng that your key was added, and check `IdentityFile` in your config |
| `Address already in use` on 5433 | A tunnel is already open, or something else is using 5433 | Close the other tunnel window |
| SSH connects then closes immediately | You left out `-N` | Use `ssh -N cs203-db` |
| App fails with `missing column` / `SchemaManagementException` | Entity doesn't match the database | Add a Flyway migration for your change |
| Flyway `checksum mismatch` | Someone edited an old migration | Revert the edit and make a new migration instead |
| Flyway `Detected applied migration not resolved locally` | Someone merged a newer migration your branch doesn't have | Pull or merge `main` |

If you're stuck, send the **exact error message** in the group chat, not "it
doesn't work".
