# Spark Thesis Backend

This Spring Boot application serves the [Spark Android app](https://github.com/aadi535/spark-thesis-android). It verifies Google identity, issues Spark session tokens, stores progress and learning history, handles Knowledge Check, and supplies the XP leaderboard and AI content.

## Run the backend

1. Install Java 21 and Maven. Start PostgreSQL and create a database for Spark. Start Redis if you want the leaderboard.
2. Set the variables below in your shell, IDE run configuration or a local ignored `.env` file.
3. Run `mvn spring-boot:run`. The server listens on port 8080. The Android app points to this backend rather than to Groq directly.

| Variable | What to configure |
|---|---|
| `DATABASE_URL` | JDBC URL, for example `jdbc:postgresql://localhost:5432/spark` |
| `DB_USERNAME`, `DB_PASSWORD` | Credentials for your PostgreSQL role |
| `JWT_SECRET` | A fresh, random signing secret of at least 32 bytes |
| `GOOGLE_CLIENT_ID` | Web OAuth client ID used by the Android sign-in configuration |
| `GROQ_API_KEY` | Your Groq key for AI lessons, chat, question suggestions and reviews |
| `UPSTASH_REDIS_URL` | Redis URI. Local default is `redis://localhost:6379` |
| `UPSTASH_REDIS_SSL` | Enable TLS for a hosted Redis connection that requires it |

Run `mvn test` for the standard suite. The PostgreSQL integration tests are opt-in through `SPARK_TEST_DATABASE_URL`, `SPARK_TEST_DATABASE_USER` and `SPARK_TEST_DATABASE_PASSWORD`. Use an isolated test database because these tests recreate their schema.

## Review version

This snapshot comes from commit `e7087ad52950d0d8e32469776bf0394c8484bc53` on `thesis-hardening` in the original private repository. The review copy has a new Git history and uses local configuration for the reviewer’s services.

- [Architecture](docs/architecture.md)
- [Development history](docs/development-history.md)
