# BCLForgeLib
This Forge mod is a library made for [BetterChunkLoader](https://github.com/Arcturus-Official/BetterChunkLoader) to be able to load Chunks without any issues. This mod doesn't have any configurations or commands it is only providing the glue needed for BetterChunkLoader to load chunks.

# Changes from Original Version

Changed:
- **Asynchronous Processing**: Introduced `CompletableFuture` for asynchronous loading and unloading of chunks to improve performance.
- **Concurrent Data Structures**: Replaced `HashMap` with `ConcurrentHashMap` for thread-safe operations.
- **Efficient Map Management**: Utilized `computeIfAbsent` for managing tickets and chunk loaders, simplifying code and reducing manual checks.
- **Multi-threading Support**

