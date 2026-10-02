# Account sync

The website exposes server-side reading history. Android uses the same endpoints as the website's library and reader, rather than browser local storage.

| Feature | Request |
| --- | --- |
| Library | `GET /api/library?limit=100&offset=0&sort=recent` |
| Save title | `PUT /api/library/{mangaId}` with `{"status":"plan_to_read"}` |
| Remove title | `DELETE /api/library/{mangaId}` |
| Reading history | `GET /api/history` |
| Reading progress | `POST /api/progress/{mangaId}` with `chapterId`, zero-based `page`, `pageCount`, `completed` |
| Clear history | `DELETE /api/history` |
| Followed updates | `GET /api/library/updates?limit=50` |
| Profile | `GET /api/settings`; `PATCH /api/settings` for bio; `POST /api/auth/update-user` for name and username |
| Comments | `GET /api/users/{username}/comments?limit=50` |
| Leaderboard | `GET /api/stats/levels?limit=50` |
| Discover | `GET /api/stats/titles?sort=latest`, `popular`, `release`, or `follows` |

Contracts were checked against the official [website](https://comico.moe), its public JavaScript modules, and [OpenAPI routes](https://comico.moe/api/openapi.json). GET library returns an object with `items` containing nested `manga` objects. History and leaderboard return arrays. Followed updates returns an object with `items` containing nested manga objects.

Session cookies are limited to HTTPS requests to the exact comico.moe host. They are not sent to image providers. Cookie storage uses Android Keystore encryption. Library/history caches and mutation queues use private preferences with separate keys for each account and the guest.

A restored session or successful sign-in loads that account's cached data and synchronizes it. Guest data is imported only through the Data tab. Account bookmark operations use a persisted queue, including deletions. History progress is debounced and queued after native image page counts are known. Remote history is authoritative except for queued changes and local entries without native page counts. Newer reading timestamps win, including when a reader deliberately returns to an earlier page. Remote reads, writes, and retries are visible through the sync status.

Unit tests use MockWebServer to validate requests and parse library/history responses. Real authenticated account and Android device testing still require a user's test session/device; no accounts were created during development.
