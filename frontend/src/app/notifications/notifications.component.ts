import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { NotificationService, NotificationSummary } from '../services/notification.service';
import { NotificationWebSocketService } from '../services/notification-websocket.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css'
})
export class NotificationsComponent implements OnChanges {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();

  tab: 'notifications' | 'logs' = 'notifications';
  all: NotificationSummary[] = [];
  kinds: string[] = [];
  selectedKind = 'all';
  selectedDate = '';
  selectedHour = '';
  expandedId: string | null = null;

  constructor(
    private notificationService: NotificationService,
    private ws: NotificationWebSocketService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.loadHistory();
      this.subscribeToLive();
    } else if (changes['open'] && !this.open) {
      this.unsubscribeFromLive();
    }
  }

  private loadHistory(): void {
    this.notificationService.getNotifications(300).subscribe((list) => {
      this.all = list.sort((a, b) => b.timestamp - a.timestamp);
      this.recomputeKinds();
    });
  }

  private liveSub: Subscription | null = null;

  private subscribeToLive(): void {
    if (this.liveSub) {
      return;
    }
    this.liveSub = this.ws.messages().subscribe((summary) => {
      if (summary && summary.id && !this.all.some((n) => n.id === summary.id)) {
        this.all.unshift(summary);
        this.recomputeKinds();
      }
    });
  }

  private unsubscribeFromLive(): void {
    if (this.liveSub) {
      this.liveSub.unsubscribe();
      this.liveSub = null;
    }
  }

  private recomputeKinds(): void {
    this.kinds = Array.from(new Set(this.logs().map((l) => l.kind ?? 'sem categoria'))).sort();
  }

  logs(): NotificationSummary[] {
    return this.all.filter((n) => n.mediaType === 'DOCUMENT');
  }

  notifications(): NotificationSummary[] {
    return this.all.filter((n) => n.mediaType !== 'DOCUMENT');
  }

  selectTab(tab: 'notifications' | 'logs'): void {
    this.tab = tab;
  }

  filteredLogs(): NotificationSummary[] {
    let list = this.logs();
    if (this.selectedKind !== 'all') {
      list = list.filter((l) => (l.kind ?? 'sem categoria') === this.selectedKind);
    }
    if (this.selectedDate) {
      list = list.filter((l) => l.date === this.selectedDate);
    }
    if (this.selectedHour !== '' && this.selectedHour !== null && this.selectedHour !== undefined) {
      const hour = String(this.selectedHour).padStart(2, '0');
      list = list.filter((l) => l.hour === hour);
    }
    return list;
  }

  mediaUrl(n: NotificationSummary): string | null {
    if (!n.fileId) {
      return null;
    }
    const base = this.notificationService.mediaUrl(n.fileId);
    return n.filename ? `${base}?filename=${encodeURIComponent(n.filename)}` : base;
  }

  isMedia(n: NotificationSummary): boolean {
    return n.mediaType === 'PHOTO' || n.mediaType === 'ANIMATION';
  }

  isExpanded(n: NotificationSummary): boolean {
    return this.expandedId === n.id;
  }

  toggleExpand(n: NotificationSummary): void {
    this.expandedId = this.isExpanded(n) ? null : n.id;
  }

  openMedia(n: NotificationSummary): void {
    const url = this.mediaUrl(n);
    if (url) {
      window.open(url, '_blank');
    }
  }

  senderLabel(sender: string): string {
    return sender === 'object-detection' ? 'Detecção' : sender === 'recorder' ? 'Recorder' : sender === 'live-transmission' ? 'Live' : sender;
  }

  timeOf(n: NotificationSummary): string {
    const d = new Date(n.timestamp);
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  onClose(): void {
    this.close.emit();
  }
}
