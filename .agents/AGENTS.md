# Java Code Style Rules
- Do not use fully qualified class names directly in method signatures, fields, or variables (e.g., `PageResponse<org.hvtra.catalog.dto.response.LookupResponse>`).
- Always use standard Java imports and simple class names instead (e.g., `import org.hvtra.catalog.dto.response.LookupResponse;` and `PageResponse<LookupResponse>`).
- Exception: Fully qualified names are allowed when required by JPQL queries (e.g., `SELECT new org.hvtra.catalog.dto...`) inside `@Query` annotations because JPA syntax demands it.
