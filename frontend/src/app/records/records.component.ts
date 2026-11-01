import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RecordService, VideoRecord } from '../services/record.service';

@Component({
  selector: 'app-records',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './records.component.html',
  styleUrl: './records.component.css'
})
export class RecordsComponent implements OnChanges {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();

  cameras: string[] = [];
  selectedCamera = 'all';
  all: VideoRecord[] = [];
  loadingMore = false;
  hasMore = false;
  loading = false;
  expandedFilename: string | null = null;
  playingFilename: string | null = null;
  private nextCursor: string | null = null;
  private readonly pageSize = 100;

  constructor(private recordService: RecordService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.resetAndLoad();
      this.loadCameras();
    }
  }

  selectCamera(camera: string): void {
    if (this.selectedCamera === camera) {
      return;
    }
    this.selectedCamera = camera;
    this.resetAndLoad();
  }

  private cameraParam(): string | undefined {
    return this.selectedCamera !== 'all' ? this.selectedCamera : undefined;
  }

  private resetAndLoad(): void {
    this.all = [];
    this.nextCursor = null;
    this.hasMore = true;
    this.loadingMore = false;
    this.expandedFilename = null;
    this.playingFilename = null;
    this.loadHistory();
  }

  private loadHistory(): void {
    this.loading = true;
    this.recordService.getRecords(this.cameraParam(), this.pageSize).subscribe({
      next: (page) => {
        this.all = page.items;
        this.nextCursor = page.nextCursor;
        this.hasMore = page.hasMore;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.hasMore = false;
      }
    });
  }

  loadMore(): void {
    if (this.loadingMore || !this.hasMore || !this.nextCursor) {
      return;
    }
    this.loadingMore = true;
    this.recordService.getRecords(this.cameraParam(), this.pageSize, this.nextCursor).subscribe({
      next: (page) => {
        const existing = new Set(this.all.map((r) => r.filename));
        const fresh = page.items.filter((r) => !existing.has(r.filename));
        this.all.push(...fresh);
        this.nextCursor = page.nextCursor;
        this.hasMore = page.hasMore;
        this.loadingMore = false;
      },
      error: () => {
        this.loadingMore = false;
      }
    });
  }

  onScroll(event: Event): void {
    const el = event.target as HTMLElement;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 200) {
      this.loadMore();
    }
  }

  private loadCameras(): void {
    this.recordService.getCameras().subscribe({
      next: (cameras) => {
        this.cameras = cameras;
      },
      error: () => {
        this.cameras = [];
      }
    });
  }

  streamUrl(r: VideoRecord): string {
    return this.recordService.streamUrl(r.filename);
  }

  isExpanded(r: VideoRecord): boolean {
    return this.expandedFilename === r.filename;
  }

  togglePlay(r: VideoRecord): void {
    if (this.expandedFilename === r.filename) {
      this.expandedFilename = null;
      this.playingFilename = null;
    } else {
      this.expandedFilename = r.filename;
      this.playingFilename = r.filename;
    }
  }

  onVideoError(r: VideoRecord): void {
    if (this.playingFilename === r.filename) {
      this.playingFilename = null;
    }
  }

  onVideoReady(r: VideoRecord): void {
    if (this.expandedFilename === r.filename) {
      this.playingFilename = r.filename;
    }
  }

  isPlaying(r: VideoRecord): boolean {
    return this.playingFilename === r.filename;
  }

  timeOf(r: VideoRecord): string {
    // timestamp is "yyyy-MM-dd HH:mm:ss"
    if (!r.timestamp) {
      return '';
    }
    const [date, time] = r.timestamp.split(' ');
    const [y, m, d] = date.split('-');
    const [hh, mm] = time.split(':');
    return `${d}/${m}/${y} ${hh}:${mm}`;
  }

  sizeLabel(r: VideoRecord): string {
    if (!r.size || r.size <= 0) {
      return '';
    }
    if (r.size >= 1024 * 1024) {
      return `${(r.size / 1024 / 1024).toFixed(1)} MB`;
    }
    return `${Math.round(r.size / 1024)} KB`;
  }

  onClose(): void {
    this.close.emit();
  }
}
