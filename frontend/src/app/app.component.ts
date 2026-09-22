import { Component, ElementRef, ViewChild, QueryList, ViewChildren, AfterViewInit, OnDestroy, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MenuComponent } from './menu/menu.component';
import { ConfigModalComponent } from './config-modal/config-modal.component';
import { ConfigService } from './services/config.service';
import { computeGrid } from './services/grid-layout';
import { buildDetectionMap } from './services/detection-map';

// Import the VideoRTC class and register the custom element
import { VideoRTC } from '../assets/video-rtc';

if (typeof customElements !== 'undefined' && !customElements.get('video-rtc')) {
  customElements.define('video-rtc', VideoRTC);
}

interface CameraStream {
  name: string;
  url: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, MenuComponent, ConfigModalComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements AfterViewInit, OnDestroy {
  @ViewChild('cameraWall', { static: true }) cameraWall!: ElementRef<HTMLDivElement>;
  @ViewChildren('tileVideo', { read: ElementRef }) tileVideoAnchors?: QueryList<ElementRef<HTMLDivElement>>;

  menuOpen = false;
  configModalOpen = false;
  theme: 'dark' | 'light' = 'dark';

  streams: CameraStream[] = [];
  loadingStreams = false;
  streamsError = false;
  gridColumns = 1;
  gridRows = 1;
  expanded: CameraStream | null = null;

  detections: Record<string, string> = {};
  private detectionPolling: any = null;

  private players: VideoRTC[] = [];
  private expandedOverlay: HTMLElement | null = null;
  private expandedStartTransform = '';
  private pendingAttach = false;
  private offlineStrikes = new Map<string, number>();
  private onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      this.closeExpanded();
    }
  };

  constructor(
    private configService: ConfigService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    const saved = localStorage.getItem('dashboard-theme');
    if (saved === 'light') {
      this.theme = 'light';
    }
    this.applyTheme();
  }

  ngAfterViewInit(): void {
    this.loadStreams();
    this.startDetectionPolling();
    window.addEventListener('keydown', this.onKeydown);
  }

  ngAfterViewChecked(): void {
    if (this.pendingAttach) {
      const anchors = this.tileVideoAnchors?.toArray() ?? [];
      if (anchors.length >= this.streams.length) {
        this.pendingAttach = false;
        this.attachTilePlayers();
      }
    }
  }

  ngOnDestroy(): void {
    window.removeEventListener('keydown', this.onKeydown);
    this.stopDetectionPolling();
    this.disposePlayers();
    if (this.expandedOverlay) {
      this.expandedOverlay.querySelectorAll('video-rtc').forEach((el) => el.remove());
      this.expandedOverlay.remove();
      this.expandedOverlay = null;
    }
  }

  private applyTheme(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    document.documentElement.classList.toggle('light', this.theme === 'light');
  }

  loadStreams(): void {
    this.loadingStreams = true;
    this.streamsError = false;
    this.configService.getGo2RtcStreams().subscribe({
      next: (list) => {
        this.loadingStreams = false;
        this.streams = (list ?? []).filter(
          (s: CameraStream) => !!s && !!s.url && !String(s.name).toLowerCase().endsWith('panel')
        );
        const grid = computeGrid(Math.max(1, this.streams.length));
        this.gridColumns = grid.columns;
        this.gridRows = grid.rows;
        this.disposePlayers();
        this.pendingAttach = true;
      },
      error: () => {
        this.loadingStreams = false;
        this.streams = [];
        this.streamsError = true;
      }
    });
  }

  private attachTilePlayers(): void {
    this.disposePlayers();
    const anchors = this.tileVideoAnchors?.toArray() ?? [];
    for (let i = 0; i < anchors.length; i++) {
      const anchor = anchors[i].nativeElement;
      anchor.replaceChildren();
      if (i < this.streams.length) {
        const player = this.createPlayer(this.streams[i].name, false);
        anchor.appendChild(player);
        this.players.push(player);
      }
    }
  }

  private createPlayer(streamName: string, withAudio: boolean): VideoRTC {
    const el = document.createElement('video-rtc') as VideoRTC;
    el.style.width = '100%';
    el.style.height = '100%';
    el.style.display = 'block';
    el.media = withAudio ? 'video,audio' : 'video';
    el.src = this.streamSource(streamName);
    return el;
  }

  private streamSource(streamName: string): string {
    const encoded = encodeURIComponent(streamName);
    return `ws://${window.location.hostname}:1984/api/ws?src=${encoded}`;
  }

  private disposePlayers(): void {
    for (const player of this.players) {
      player.remove();
    }
    this.players = [];
  }

  private startDetectionPolling(): void {
    this.refreshDetections();
    this.detectionPolling = setInterval(() => {
      this.refreshDetections();
      this.checkOffline();
    }, 2000);
  }

  private checkOffline(): void {
    const anchors = this.tileVideoAnchors?.toArray() ?? [];
    for (let i = 0; i < this.players.length && i < this.streams.length; i++) {
      const name = this.streams[i].name;
      const player = this.players[i];
      const video = player.video;
      const ok = !!video && video.videoWidth > 0 && (video.readyState ?? 0) >= 2;
      const strikes = this.offlineStrikes.get(name) ?? 0;
      if (ok) {
        this.offlineStrikes.delete(name);
      } else {
        this.offlineStrikes.set(name, strikes + 1);
      }
    }
    void anchors;
  }

  isOffline(streamName: string): boolean {
    return (this.offlineStrikes.get(streamName) ?? 0) >= 3;
  }

  private stopDetectionPolling(): void {
    if (this.detectionPolling) {
      clearInterval(this.detectionPolling);
      this.detectionPolling = null;
    }
  }

  private refreshDetections(): void {
    this.configService.getDetections().subscribe({
      next: (list) => {
        this.detections = buildDetectionMap(list);
      },
      error: () => {
        this.detections = {};
      }
    });
  }

  detectionFor(streamName: string): string | undefined {
    return this.detections[streamName];
  }

  openStream(stream: CameraStream, event: Event): void {
    if (this.expandedOverlay) {
      return;
    }
    const tile = event.currentTarget as HTMLElement;
    const host = this.cameraWall.nativeElement;
    const hostRect = host.getBoundingClientRect();
    const tileRect = tile.getBoundingClientRect();
    if (!hostRect.width || !hostRect.height || !tileRect.width || !tileRect.height) {
      return;
    }

    const scaleX = tileRect.width / hostRect.width;
    const scaleY = tileRect.height / hostRect.height;
    const dx = tileRect.left + tileRect.width / 2 - (hostRect.left + hostRect.width / 2);
    const dy = tileRect.top + tileRect.height / 2 - (hostRect.top + hostRect.height / 2);
    this.expandedStartTransform = `translate(${dx}px, ${dy}px) scale(${scaleX}, ${scaleY})`;

    const overlay = document.createElement('div');
    overlay.className = 'expanded-view';
    overlay.style.transform = this.expandedStartTransform;
    overlay.style.transition = 'none';

    const player = this.createPlayer(stream.name, true);
    overlay.appendChild(player);

    const label = document.createElement('span');
    label.className = 'expanded-label';
    label.textContent = stream.name;
    overlay.appendChild(label);

    const close = document.createElement('button');
    close.className = 'expanded-close';
    close.setAttribute('aria-label', 'Fechar visualização');
    close.title = 'Fechar (Esc)';
    close.innerHTML = '&times;';
    close.addEventListener('click', (e) => {
      e.stopPropagation();
      this.closeExpanded();
    });
    overlay.appendChild(close);

    overlay.addEventListener('click', () => this.closeExpanded());

    host.appendChild(overlay);
    this.expandedOverlay = overlay;
    this.expanded = { name: stream.name, url: stream.url };

    requestAnimationFrame(() => {
      overlay.style.transition = 'transform 0.55s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease';
      overlay.style.transform = 'translate(0, 0) scale(1)';
    });
  }

  private closeExpanded(): void {
    if (!this.expandedOverlay) {
      return;
    }
    const overlay = this.expandedOverlay;
    this.expanded = null;
    overlay.style.transform = this.expandedStartTransform;
    const toRemove = overlay;
    setTimeout(() => {
      toRemove.querySelectorAll('video-rtc').forEach((el) => el.remove());
      toRemove.remove();
      if (this.expandedOverlay === toRemove) {
        this.expandedOverlay = null;
      }
    }, 600);
  }

  toggleMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  closeMenu(): void {
    this.menuOpen = false;
  }

  openConfigs(): void {
    this.configModalOpen = true;
  }

  closeConfigModal(): void {
    this.configModalOpen = false;
    this.loadStreams();
  }

  toggleTheme(): void {
    this.theme = this.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem('dashboard-theme', this.theme);
    this.applyTheme();
  }
}