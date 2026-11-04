/** One logical fetch owns one key, including a transport retry. No persistent cache. */
export class RecommendationRequestScope {
  readonly controller = new AbortController();
  readonly key: string;
  private active = true;
  constructor(key: string = crypto.randomUUID()) {
    this.key = key;
  }
  isCurrent() {
    return this.active && !this.controller.signal.aborted;
  }
  cancel() {
    this.active = false;
    this.controller.abort();
  }
}

/** Synchronous lock: React state alone cannot prevent two events before a render. */
export class WorkspaceMutationLock {
  private locked = false;
  isLocked() {
    return this.locked;
  }
  acquire(): (() => void) | null {
    if (this.locked) return null;
    this.locked = true;
    return () => {
      this.locked = false;
    };
  }
}
