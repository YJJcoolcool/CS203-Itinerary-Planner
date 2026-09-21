# Itinerary Planner

SMU CS203 Human-AI Collaborative Software Development

## Project Structure

- `backend/` - Spring Boot backend
- `frontend/` - React + Vite frontend
- `planning/` - Project planning documents

## Running the Project

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

## Environment Variables

The frontend will use environment variables for local configuration. See
frontend/.env.example for the required variables.
