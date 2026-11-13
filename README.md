# Itinerary Planner

SMU CS203 Human-AI Collaborative Software Development

## Project Structure

- `backend/` - Spring Boot backend
- `frontend/` - React + Vite frontend
- `planning/` - Project planning documents

## Running the Project

## Environment Variables

Environment variables may be used for local configuration (things like Clerk).
However, the project has been specifically set up such that it can be run without setting up these variables.
For proper testing of authentication though, it is recommended that you have a .env file in both /frontend and /backend.
Here is how to set it up:

1. In `/frontend` and `/backend`, make a copy of the respective `.env.example` file, and rename that copy to `.env`
2. Fill in the fields as provided within our group. **`.env` files contain private keys that must not be shared with others!**
3. Run the project with the instructions below.

### Backend

From the project's root directory:

```bash
cd backend
./mvnw spring-boot:run
```

The backend will run on `http://localhost:8080`.

### Frontend

From the project's root directory:

```bash
cd frontend
npm install
npm run dev
```

The frontend will be available at the URL shown by Vite, typically `http://localhost:5173`.

### Building the Frontend

From the project's root directory:

```bash
cd frontend
npm run build
```

This generates the production frontend in `frontend/dist/`.

## Testing and Code Coverage

From the project's root directory:

```bash
cd backend
./mvnw test
```

This runs the backend test suite and writes a JaCoCo coverage report to
`backend/target/site/jacoco/index.html` (machine-readable data in `jacoco.xml`).

To additionally enforce the coverage floor (default 75% of lines), run:

```bash
cd backend
./mvnw verify
```

Notes:

- Override or relax the floor with `-Djacoco.line.coverage.minimum=0.70`, or skip
  coverage instrumentation entirely with `-Djacoco.skip=true`.
- Open the HTML report with `open target/site/jacoco/index.html` (macOS).
- CI uploads the report as the `backend-jacoco-report` artifact on every run.
- If you see Flyway "duplicate version" errors after switching branches, stale
  migration files are lingering in `target/classes`; run `./mvnw clean test`.
