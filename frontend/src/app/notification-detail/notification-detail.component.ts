import { Component, EventEmitter, Input, Output, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService, NotificationSummary } from '../services/notification.service';

@Component({
  selector: 'app-notification-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-detail.component.html',
  styleUrl: './notification-detail.component.css'
})
export class NotificationDetailComponent implements OnChanges {
  @Input() notificationId: string | null = null;
  @Output() close = new EventEmitter<void>();

  notification: NotificationSummary | null = null;
  loading = false;
  error = false;
  private loadedMedia = new Set<string>();

  constructor(private notificationService: NotificationService) {}

  ngOnChanges(): void {
    if (this.notificationId) {
      this.load();
    } else {
      this.notification = null;
      this.loading = false;
      this.error = false;
    }
  }

  private load(): void {
    this.loading = true;
    this.error = false;
    this.notification = null;
    this.notificationService.getNotification(this.notificationId!).subscribe({
      next: (n) => {
        this.notification = n;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load notification', this.notificationId, err);
        this.error = true;
        this.loading = false;
      }
    });
  }

  retry(): void {
    this.load();
  }

  onMediaLoaded(id: string): void {
    this.loadedMedia.add(id);
  }

  isMediaLoading(n: NotificationSummary): boolean {
    return this.isMedia(n) && !!n.fileId && !this.loadedMedia.has(n.id);
  }

  mediaUrl(n: NotificationSummary): string {
    return this.notificationService.mediaUrl(n.fileId!);
  }

  isMedia(n: NotificationSummary): boolean {
    return n.mediaType === 'PHOTO' || n.mediaType === 'ANIMATION';
  }

  timeOf(n: NotificationSummary): string {
    const d = new Date(n.timestamp);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  onClose(): void {
    this.close.emit();
  }
}
