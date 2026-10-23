# Repository instructions

## API and Postman synchronization

Whenever an HTTP endpoint is added, removed, renamed, or its contract changes,
update the Postman assets in the same change:

- `postman/DropCore.postman_collection.json`
- `postman/DropCore-Local.postman_environment.json` when variables change
- `postman/README.md` when setup, ordering, fixtures, or behavior changes

Each endpoint must have a clear request description and useful automated
assertions for its documented success and error responses. Cover idempotent
replay when the endpoint supports idempotency. Never put credentials or other
secrets in exported Postman files.

Before finishing, validate both Postman JSON files and run the relevant Maven
tests. Keep the collection compatible with Postman Collection Schema v2.1.
