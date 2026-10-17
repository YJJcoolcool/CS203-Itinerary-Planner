# Connecting to the shared database

The backend uses a shared **PostgreSQL 16** database on an AWS EC2 server.
The database port is not open to the internet, so you reach it through an
**SSH tunnel**. The tunnel makes the remote database appear on your own
machine at `localhost:5433`.

```text
your laptop                         EC2 server
backend (dev profile)
  -> localhost:5433  ==== SSH ====>  localhost:5432 (Postgres)
```

The schema is managed by **Flyway**, not Hibernate. Read
[Changing the schema](#changing-the-schema) before you change any `@Entity`.

> **Fill-in values.** Ask the team lead for the values marked `<...>` below.
> Never commit them to the repo.
>
> | Placeholder | What it is |
> |---|---|
> | `<EC2_HOST>` | Public IP or DNS name of the EC2 server |
> | `<EC2_USER>` | SSH login user (often `ubuntu` or `ec2-user`) |
> | `<KEY_FILE>` | Path to your private key, e.g. `~/.ssh/cs203-ec2.pem` |
> | DB password | Value for `DB_PASSWORD` in `backend/.env` |

## One-time setup

### 1. Store the SSH key

Put the key file somewhere outside the repo, such as `~/.ssh/`, and restrict
its permissions. SSH refuses keys that other users can read.

```bash
mkdir -p ~/.ssh
mv ~/Downloads/cs203-ec2.pem ~/.ssh/
chmod 600 ~/.ssh/cs203-ec2.pem
```

**Windows users:** run everything in this guide from a **WSL terminal**, not
PowerShell or PuTTY. With WSL2's default networking, a tunnel opened on the
Windows side can't be reached from WSL on `localhost`, so the backend gets
"connection refused". If you have to tunnel from Windows, turn on mirrored
networking by adding `networkingMode=mirrored` under `[wsl2]` in
`%UserProfile%\.wslconfig`, then run `wsl --shutdown`.

### 2. Add an SSH shortcut (optional, recommended)

Add this to `~/.ssh/config`:

```text
Host cs203-db
    HostName <EC2_HOST>
    User <EC2_USER>
    IdentityFile <KEY_FILE>
    LocalForward 5433 localhost:5432
    ServerAliveInterval 30
    ExitOnForwardFailure yes
```

`ServerAliveInterval` keeps an idle tunnel from timing out.
`ExitOnForwardFailure` makes SSH exit with an error, instead of silently
connecting without a tunnel, when port 5433 is already in use.

### 3. Set the database password

Copy `backend/.env.example` to `backend/.env` if you don't have one yet, then
set:

```properties
DB_PASSWORD=the-password-from-the-team-lead
```

- **No quotes.** Spring reads `.env` as a properties file, so quotes become
  part of the password.
- `backend/.env` is gitignored. Check that `git status` never lists it.

## Every time you work on the backend

### 1. Open the tunnel

In its own terminal, and leave it running:

```bash
ssh -N cs203-db
```

Without the SSH shortcut:

```bash
ssh -N -L 5433:localhost:5432 -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes \
    -i <KEY_FILE> <EC2_USER>@<EC2_HOST>
```

`-N` means "tunnel only, no shell", so the terminal shows no output while it's
working. Press Ctrl+C to close the tunnel.

### 2. Check the tunnel works

In another terminal:

```bash
pg_isready -h localhost -p 5433
# localhost:5433 - accepting connections
```

If `pg_isready` isn't installed, install it with
`sudo apt install postgresql-client`.

### 3. Start the app

```bash
./run.sh            # backend + frontend; uses the dev profile by default
./run.sh backend    # backend only
```

To start the backend on its own, from `backend/`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The backend **needs a profile** because the base `application.properties`
has no database settings. Without `dev` or `prod`, it fails at startup.

| Profile | Database URL | Used for |
|---|---|---|
| `dev` | `jdbc:postgresql://localhost:5433/postgres` | Local development through the tunnel |
| `prod` | `jdbc:postgresql://db:5432/postgres` | The deployed app (Postgres reachable as host `db`) |

Both use user `postgres` and read the password from the `DB_PASSWORD`
environment variable.

### Looking at the data (optional)

With the tunnel open:

```bash
psql -h localhost -p 5433 -U postgres -d postgres
```

GUI tools such as DBeaver or IntelliJ's Database tab work too. Use host
`localhost`, port `5433`, database `postgres` and user `postgres`.

## This is a shared database

Everyone on the team connects to the **same** database, so:

- Data you create is visible to everyone, and theirs to you.
- **Never** run `DROP`, `TRUNCATE` or `flyway clean`, and never change tables
  by hand in `psql`. Every schema change goes through a migration file.
- If you need a throwaway database to experiment with, run Postgres locally
  in Docker instead (`docker run -p 5433:5432 -e POSTGRES_PASSWORD=... postgres:16`
  with the tunnel closed).

## Changing the schema

Flyway runs the SQL files in `backend/src/main/resources/db/migration/` in
order, once each, and records them in the `flyway_schema_history` table.
Hibernate is set to `ddl-auto=validate`, so it **checks** that the entities
match the tables but never changes them.

If you add or change a field on an `@Entity`, you must also add a migration:

1. Create the next numbered file, with two underscores after the version:
   ```text
   V2__add_trip_description.sql
   ```
2. Write the SQL for just your change:
   ```sql
   ALTER TABLE trips ADD COLUMN description varchar(255);
   ```
3. Start the backend. Flyway applies the file and Hibernate validates the
   result.

Rules:

- **Never edit a migration that has already run**, including `V1__init.sql`.
  Flyway stores a checksum of each file and refuses to start if one changes.
  Fix mistakes with a new `V<n+1>` file.
- **Check version numbers before you merge.** If two branches both add a
  `V2`, rename yours to the next free number before merging.
- **Adding an enum value** (for example to `InvitationStatus`, `TripRole` or
  `AppRole`) also needs a migration. The tables have `CHECK` constraints
  listing the allowed values, so you must drop and re-add the constraint.

Tests don't use the shared database. They run on in-memory H2 with Flyway
turned off (see `backend/src/test/resources/config/application.properties`),
so `./mvnw test` works without the tunnel. Tests don't run your migration
SQL, so run the app once with `dev` to check it.

## Troubleshooting

| Error | Likely cause | Fix |
|---|---|---|
| `Connection to localhost:5433 refused` | Tunnel isn't running or has dropped | Restart it and check with `pg_isready`. On Windows, make sure it runs in WSL (see setup step 1). |
| `Permission denied (publickey)` | Wrong key, user or key permissions | Check `<KEY_FILE>` and `<EC2_USER>`, and run `chmod 600` on the key |
| `bind: Address already in use` | Something else is on port 5433, often an old tunnel | `lsof -i :5433` to find it, then stop it |
| `password authentication failed for user "postgres"` | `DB_PASSWORD` is wrong, quoted or missing | Fix it in `backend/.env` without quotes |
| `Could not resolve placeholder 'DB_PASSWORD'` | `DB_PASSWORD` isn't set at all | Add it to `backend/.env` |
| `Failed to determine a suitable driver class` / no datasource | No profile is active | Use `./run.sh` or add `-Dspring-boot.run.profiles=dev` |
| `Schema-validation: missing column [...]` | You changed an entity without a migration | Add a `V<n>__...sql` file (see [Changing the schema](#changing-the-schema)) |
| `Validate failed: Migration checksum mismatch` | A migration that has already run was edited | Undo the edit and put the change in a new migration |
| `Detected applied migration not resolved locally` | Someone merged a newer migration that your branch doesn't have | Pull or merge `main` |
