# Shared test fixtures

Place reusable, legally redistributable test inputs and golden outputs here when the owning Core feature is implemented. Keep simple module-local test data beside that module's tests. Follow the category and naming conventions in [`docs/TESTING.md`](../docs/TESTING.md), and never make a test download fixture data at runtime.

Phase 0 creates the fixture location; it does not add placeholder `.td`, profile, raster, or protocol cases before their implementations exist.
