export type PhotoTask = {
  id: string;
  image: string;
};

export class PhotoTaskQueue {
  private preview: PhotoTask | null = null;
  private readonly pending: PhotoTask[] = [];

  replacePreview(task: PhotoTask): PhotoTask | null {
    const previous = this.preview;
    if (previous) {
      this.pending.push(previous);
    }
    this.preview = task;
    return previous;
  }

  confirmPreview(taskId: string): boolean {
    if (this.preview?.id !== taskId) {
      return false;
    }

    this.pending.push(this.preview);
    this.preview = null;
    return true;
  }

  discardPreview(taskId: string): boolean {
    if (this.preview?.id !== taskId) {
      return false;
    }

    this.preview = null;
    return true;
  }

  flushPreview(): boolean {
    if (!this.preview) {
      return false;
    }

    this.pending.push(this.preview);
    this.preview = null;
    return true;
  }

  takeNext(): PhotoTask | undefined {
    return this.pending.shift();
  }

  hasPending(): boolean {
    return this.preview !== null || this.pending.length > 0;
  }

  hasReady(): boolean {
    return this.pending.length > 0;
  }

  snapshotPending(): PhotoTask[] {
    return this.preview ? [...this.pending, this.preview] : [...this.pending];
  }
}
