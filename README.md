# AI Code Doctor

Compiler-first AI code review for Java. It compiles your code with the real JDK compiler, then asks a local
Ollama model to explain the errors, review quality and propose a fix - and **recompiles the fix** to check it.

## Requirements
- JDK 21+ (a JDK, not a JRE - the app needs `javac`)
- Maven 3.6.3+
- [Ollama](https://ollama.com) with a coder model: `ollama pull qwen2.5-coder:7b`
  (on tight RAM try `qwen2.5-coder:3b` and set `OLLAMA_MODEL`)

## Run locally
```bash
ollama serve                      # if it is not already running
mvn spring-boot:run               # or: ./scripts/start.sh
```
Open http://localhost:8080. The default database is an embedded H2 file in `./data`.

## Run with Docker (MySQL + Ollama + app)
```bash
docker compose -f docker/docker-compose.yml up --build
docker compose -f docker/docker-compose.yml exec ollama ollama pull qwen2.5-coder:7b
```

## Configuration (environment variables)
| Variable | Default |
|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `OLLAMA_MODEL` | `qwen2.5-coder:7b` |
| `OLLAMA_TIMEOUT` | `180s` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | H2 file / `sa` / empty |
| `PORT` | `8080` |

Limits (`application.yml`, prefix `codedoctor`): `max-source-chars`, `max-files`, `compile-timeout`, `max-fix-attempts`.

## API
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/review` | Full pipeline (compile, AI, verified fix, save) |
| POST | `/api/compile` | Compiler only |
| POST | `/api/tests` | Generate JUnit 5 tests (not executed) |
| POST / GET | `/api/chat`, `/api/chat/{reviewId}` | Follow-up questions / chat history |
| GET / DELETE | `/api/history`, `/api/history/{id}` | List (`?q=`), open, delete |
| GET | `/api/history/{id}/report` | Markdown report |
| GET | `/api/status` | Ollama / model / languages |

Example:
```bash
curl -s localhost:8080/api/compile -H 'Content-Type: application/json' \
  -d '{"files":[{"name":"Main.java","content":"public class Main { int x = \"a\"; }"}]}'
```

## Tests
`mvn test` - compiler, validator and prompt-hygiene tests (no Ollama needed).

## Status vs. the roadmap
Done: layered structure, compiler engine, structured AI output, verified correction loop, review engine modes,
Monaco editor + diff, file upload, multi-file, JUnit test generation, chat, history + report, MySQL/H2, Docker.
Not yet: authentication/user accounts, sandboxed *execution* of generated tests, review statistics dashboard,
integration tests with Testcontainers, additional languages.

## Before going public
Add Spring Security (login + CSRF), per-user history, rate limiting, and run the container as in
`docker/docker-compose.yml` (read-only FS, dropped capabilities, memory/PID limits). Compiling untrusted
input is much safer than running it, but it is not risk-free.
