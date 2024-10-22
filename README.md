# Changes from Original Version

Changed:
- **Asynchronous Processing**: Introduced `CompletableFuture` for asynchronous loading and unloading of chunks to improve performance.
- **Concurrent Data Structures**: Replaced `HashMap` with `ConcurrentHashMap` for thread-safe operations.
- **Efficient Map Management**: Utilized `computeIfAbsent` for managing tickets and chunk loaders, simplifying code and reducing manual checks.
- **Multi-threading Support**

