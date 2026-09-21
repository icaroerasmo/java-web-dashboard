import { Component, ElementRef, ViewChild, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MenuComponent } from './menu/menu.component';
import { ConfigModalComponent } from './config-modal/config-modal.component';

// Import the VideoRTC class and register the custom element
import { VideoRTC } from '../assets/video-rtc';

if (typeof customElements !== 'undefined' && !customElements.get('video-rtc')) {
  customElements.define('video-rtc', VideoRTC);
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, MenuComponent, ConfigModalComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements AfterViewInit, OnDestroy {
  @ViewChild('videoContainer', { static: true }) videoContainer!: ElementRef<HTMLDivElement>;

  menuOpen = false;
  configModalOpen = false;
  theme: 'dark' | 'light' = 'dark';

  private player: VideoRTC | null = null;

  constructor() {
    const saved = localStorage.getItem('dashboard-theme');
    if (saved === 'light') {
      this.theme = 'light';
    }
    this.applyTheme();
  }

  private applyTheme(): void {
    document.documentElement.classList.toggle('light', this.theme === 'light');
  }

  ngAfterViewInit(): void {
    this.initPlayer();
  }

  ngOnDestroy(): void {
    if (this.player) {
      this.player.remove();
      this.player = null;
    }
  }

  private initPlayer(): void {
    const el = document.createElement('video-rtc') as VideoRTC;
    el.style.width = '100%';
    el.style.height = '100%';
    el.style.display = 'block';
    el.src = `ws://${window.location.hostname}:1984/api/ws?src=panel`;
    this.videoContainer.nativeElement.appendChild(el);
    el.play();
    this.player = el;
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
  }

  toggleTheme(): void {
    this.theme = this.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem('dashboard-theme', this.theme);
    this.applyTheme();
  }
}
