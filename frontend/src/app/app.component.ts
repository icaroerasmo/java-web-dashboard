import { Component, ElementRef, ViewChild, QueryList, ViewChildren, AfterViewInit, OnDestroy, Inject, PLATFORM_ID, HostBinding } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MenuComponent } from './menu/menu.component';
import { ConfigModalComponent } from './config-modal/config-modal.component';
import { ConfigService } from './services/config.service';
import { DetectionWebSocketService } from './services/detection-websocket.service';
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
  presentationMode = false;

  streams: CameraStream[] = [];
  loadingStreams = false;
  streamsError = false;
  gridColumns = 1;
  gridRows = 1;
  expanded: CameraStream | null = null;

  detections: Record<string, string> = {};
  private offlinePolling: any = null;
  private frozenPolling: any = null;

  private players: VideoRTC[] = [];
  private expandedOverlay: HTMLElement | null = null;
  private expandedPlayer: VideoRTC | null = null;
  private expandedStartTransform = '';
  private pendingAttach = false;
  private offlineStrikes = new Map<string, number>();
  private frameStates = new Map<string, { hash: string; count: number }>();
  private stoppedStreams = new Set<string>();
  private lastRecoveryAt = new Map<string, number>();
  private frozenFrameCanvas: HTMLCanvasElement | null = null;
  private frozenFrameCtx: CanvasRenderingContext2D | null = null;
  private frozenSeconds = 3;
  private readonly RECOVER_INTERVAL_MS = 15000;

  @HostBinding('class.presentation') get hasPresentationMode(): boolean {
    return this.presentationMode;
  }

  private onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      if (this.presentationMode) {
        this.exitPresentation();
      } else {
        this.closeExpanded();
      }
    }
  };

  constructor(
    private configService: ConfigService,
    private detectionWebSocketService: DetectionWebSocketService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    const saved = localStorage.getItem('dashboard-theme');
    if (saved === 'light') {
      this.theme = 'light';
    }
    this.readUrlParams();
    this.applyTheme();
  }

  private readUrlParams(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    if (params.get('presentation') === '1') {
      this.presentationMode = true;
    }
    const frozen = Number(params.get('frozen'));
    if (frozen > 0) {
      this.frozenSeconds = frozen;
    }
  }

  ngAfterViewInit(): void {
    this.loadStreams();
    this.startDetectionSocket();
    this.startHealthChecks();
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
    this.detectionWebSocketService.disconnect();
    this.stopHealthChecks();
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
          (s: CameraStream) => !!s && !!s.name && !String(s.name).toLowerCase().endsWith('panel')
        );
        const grid = computeGrid(Math.max(1, this.streams.length));
        this.gridColumns = grid.columns;
        this.gridRows = grid.rows;
        this.resetPerStreamState();
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

  private resetPerStreamState(): void {
    this.offlineStrikes.clear();
    this.frameStates.clear();
    this.stoppedStreams.clear();
    this.lastRecoveryAt.clear();
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

  private startDetectionSocket(): void {
    this.detectionWebSocketService.connect();
    this.detectionWebSocketService.messages().subscribe({
      next: (list) => {
        this.detections = buildDetectionMap(list);
      }
    });
  }

  private startHealthChecks(): void {
    this.offlinePolling = setInterval(() => this.checkOffline(), 2000);
    this.frozenPolling = setInterval(() => this.checkFrozen(), 1000);
  }

  private stopHealthChecks(): void {
    if (this.offlinePolling) {
      clearInterval(this.offlinePolling);
      this.offlinePolling = null;
    }
    if (this.frozenPolling) {
      clearInterval(this.frozenPolling);
      this.frozenPolling = null;
    }
  }

  private checkOffline(): void {
    for (let i = 0; i < this.players.length && i < this.streams.length; i++) {
      const name = this.streams[i].name;
      const player = this.players[i];
      if (this.stoppedStreams.has(name)) {
        this.offlineStrikes.delete(name);
        continue;
      }
      const video = player.video;
      const ok = !!video && video.videoWidth > 0 && (video.readyState ?? 0) >= 2;
      const strikes = this.offlineStrikes.get(name) ?? 0;
      if (ok) {
        this.offlineStrikes.delete(name);
      } else {
        this.offlineStrikes.set(name, strikes + 1);
      }
      this.maybeRecover(name, player);
    }
  }

  private checkFrozen(): void {
    for (let i = 0; i < this.players.length && i < this.streams.length; i++) {
      const name = this.streams[i].name;
      const player = this.players[i];
      if (this.stoppedStreams.has(name) || (player.video && player.video.paused)) {
        this.frameStates.delete(name);
        continue;
      }
      const video = player.video;
      if (!video || video.videoWidth <= 0 || (video.readyState ?? 0) < 2) {
        this.frameStates.delete(name);
        continue;
      }
      const hash = this.sampleFrameHash(video);
      if (hash === null) {
        this.frameStates.delete(name);
        continue;
      }
      const state = this.frameStates.get(name);
      if (state && state.hash === hash) {
        state.count += 1;
        this.frameStates.set(name, state);
      } else {
        this.frameStates.set(name, { hash, count: 1 });
      }
      this.maybeRecover(name, player);
    }
  }

  private sampleFrameHash(video: HTMLVideoElement): string | null {
    try {
      if (!this.frozenFrameCanvas) {
        this.frozenFrameCanvas = document.createElement('canvas');
        this.frozenFrameCanvas.width = 32;
        this.frozenFrameCanvas.height = 18;
        this.frozenFrameCtx = this.frozenFrameCanvas.getContext('2d');
      }
      const ctx = this.frozenFrameCtx;
      if (!ctx) {
        return null;
      }
      ctx.drawImage(video, 0, 0, 32, 18);
      const data = ctx.getImageData(0, 0, 32, 18).data;
      let a = 5381;
      let b = 52711;
      for (let i = 0; i < data.length; i += 16) {
        a = ((a << 5) + a + data[i]) >>> 0;
        b = ((b << 5) + b + data[i + 1]) >>> 0;
      }
      return `${a.toString(36)}-${b.toString(36)}`;
    } catch {
      return null;
    }
  }

  private maybeRecover(name: string, player: VideoRTC): void {
    const offline = (this.offlineStrikes.get(name) ?? 0) >= 3;
    if (!offline) {
      return;
    }
    const now = Date.now();
    if (now - (this.lastRecoveryAt.get(name) ?? 0) < this.RECOVER_INTERVAL_MS) {
      return;
    }
    this.lastRecoveryAt.set(name, now);
    try {
      player.restart();
    } catch (e) {
      console.warn('[streams] recover failed for', name, e);
    }
  }

  isOffline(streamName: string): boolean {
    if (this.stoppedStreams.has(streamName)) {
      return false;
    }
    if ((this.offlineStrikes.get(streamName) ?? 0) >= 3) {
      return true;
    }
    const state = this.frameStates.get(streamName);
    return !!state && state.count >= this.frozenSeconds;
  }

  offlineLabel(streamName: string): string {
    const state = this.frameStates.get(streamName);
    if (state && state.count >= this.frozenSeconds) {
      return 'FRAME CONGELADO';
    }
    return 'SEM SINAL';
  }

  private playerFor(streamName: string): VideoRTC | undefined {
    const index = this.streams.findIndex((s) => s.name === streamName);
    if (index >= 0 && index < this.players.length) {
      return this.players[index];
    }
    if (this.expanded?.name === streamName) {
      return this.expandedPlayer ?? undefined;
    }
    return undefined;
  }

  isStopped(streamName: string): boolean {
    return this.stoppedStreams.has(streamName);
  }

  isPaused(streamName: string): boolean {
    if (this.isStopped(streamName)) {
      return false;
    }
    const player = this.playerFor(streamName);
    return !!player && !!player.video && player.video.paused;
  }

  togglePlay(streamName: string, event: Event): void {
    event.stopPropagation();
    const player = this.playerFor(streamName);
    if (!player) {
      return;
    }
    if (this.stoppedStreams.has(streamName)) {
      this.stoppedStreams.delete(streamName);
      this.offlineStrikes.delete(streamName);
      this.frameStates.delete(streamName);
      try {
        player.restart();
      } catch (e) {
        console.warn('[streams] restart failed for', streamName, e);
      }
      return;
    }
    if (player.video && player.video.paused) {
      player.play();
    } else if (player.video) {
      player.video.pause();
    }
  }

  stopStream(streamName: string, event: Event): void {
    event.stopPropagation();
    const player = this.playerFor(streamName);
    if (!player) {
      return;
    }
    this.stoppedStreams.add(streamName);
    this.offlineStrikes.delete(streamName);
    this.frameStates.delete(streamName);
    try {
      player.stop();
    } catch (e) {
      console.warn('[streams] stop failed for', streamName, e);
    }
  }

  detectionFor(streamName: string): string | undefined {
    return this.detections[streamName];
  }

  openStream(stream: CameraStream, event: Event): void {
    if (this.expandedOverlay || this.presentationMode) {
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
    overlay.style.position = 'absolute';
    overlay.style.inset = '0';
    overlay.style.zIndex = '50';
    overlay.style.background = '#000';
    overlay.style.transformOrigin = 'center center';
    overlay.style.cursor = 'pointer';
    overlay.style.borderRadius = '8px';
    overlay.style.overflow = 'hidden';
    overlay.style.transform = this.expandedStartTransform;
    overlay.style.transition = 'none';

    const player = this.createPlayer(stream.name, true);
    this.expandedPlayer = player;
    overlay.appendChild(player);

    const label = document.createElement('span');
    label.className = 'expanded-label';
    label.textContent = stream.name;
    overlay.appendChild(label);

    overlay.appendChild(this.buildExpandedControls(stream.name));

    overlay.addEventListener('click', () => this.closeExpanded());

    host.appendChild(overlay);
    this.expandedOverlay = overlay;
    this.expanded = { name: stream.name, url: stream.url };

    requestAnimationFrame(() => {
      overlay.style.transition = 'transform 0.55s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease';
      overlay.style.transform = 'translate(0, 0) scale(1)';
    });
  }

  private buildExpandedControls(streamName: string): HTMLElement {
    const controls = document.createElement('div');
    controls.className = 'expanded-controls';

    const playBtn = document.createElement('button');
    playBtn.className = 'expanded-btn';
    playBtn.innerHTML = this.playPauseIcon(true);
    playBtn.title = 'Pausar';
    playBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const player = this.playerFor(streamName);
      if (!player || !player.video) {
        return;
      }
      const willPause = !player.video.paused;
      if (willPause) {
        player.video.pause();
      } else if (this.isStopped(streamName)) {
        this.togglePlay(streamName, e);
      } else {
        player.play();
      }
      playBtn.innerHTML = this.playPauseIcon(!willPause);
      playBtn.title = willPause ? 'Reproduzir' : 'Pausar';
    });
    controls.appendChild(playBtn);

    const stopBtn = document.createElement('button');
    stopBtn.className = 'expanded-btn';
    stopBtn.title = 'Parar';
    stopBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12"/></svg>';
    stopBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (this.isStopped(streamName)) {
        this.togglePlay(streamName, e);
      } else {
        this.stopStream(streamName, e);
      }
    });
    controls.appendChild(stopBtn);

    const muteBtn = document.createElement('button');
    muteBtn.className = 'expanded-btn';
    muteBtn.title = 'Mudo';
    muteBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/></svg>';
    muteBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const player = this.playerFor(streamName);
      if (!player || !player.video) {
        return;
      }
player.video.muted = !player.video.muted;
      muteBtn.innerHTML = player.video.muted
        ? '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/></svg>'
        : '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/></svg>';
      muteBtn.title = player.video.muted ? 'Com som' : 'Mudo';
    });
    controls.appendChild(muteBtn);

    return controls;
  }

  private playPauseIcon(isPause: boolean): string {
    if (isPause) {
      return '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="5" width="4" height="14"/><rect x="14" y="5" width="4" height="14"/></svg>';
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"/></svg>';
  }

  private closeExpanded(): void {
    if (!this.expandedOverlay) {
      return;
    }
    const overlay = this.expandedOverlay;
    this.expanded = null;
    this.expandedPlayer = null;
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

  enterPresentation(): void {
    this.presentationMode = true;
    this.closeMenu();
    this.closeExpanded();
  }

  exitPresentation(): void {
    this.presentationMode = false;
  }

  toggleTheme(): void {
    this.theme = this.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem('dashboard-theme', this.theme);
    this.applyTheme();
  }
}