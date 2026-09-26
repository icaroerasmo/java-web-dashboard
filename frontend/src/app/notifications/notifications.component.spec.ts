import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NotificationsComponent } from './notifications.component';
import { NotificationService, NotificationSummary } from '../services/notification.service';
import { NotificationWebSocketService } from '../services/notification-websocket.service';

describe('NotificationsComponent', () => {
  let component: NotificationsComponent;
  let fixture: ComponentFixture<NotificationsComponent>;
  let notificationService: jasmine.SpyObj<NotificationService>;

  function summary(id: string, timestamp: number): NotificationSummary {
    return {
      id, sender: 'recorder', mediaType: 'DOCUMENT', kind: 'log',
      summary: 'summary ' + id, fileId: 'file' + id, filename: 'log.txt',
      sentAt: null, timestamp, date: '2026-09-25', hour: '10', size: 1024
    };
  }

  beforeEach(async () => {
    notificationService = jasmine.createSpyObj('NotificationService', ['getNotifications', 'mediaUrl', 'getMediaText']);
    notificationService.mediaUrl.and.returnValue('/api/notifications/media/x');
    notificationService.getMediaText.and.returnValue(of('log content'));
    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        { provide: NotificationService, useValue: notificationService },
        { provide: NotificationWebSocketService, useValue: { messages: () => of(null) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
  });

  it('loads the first page on open', () => {
    notificationService.getNotifications.and.returnValue(of([summary('n1', 3000), summary('n2', 2000)]));
    component.open = true;
    component.ngOnChanges({
      open: { currentValue: true, previousValue: false, firstChange: true, isFirstChange: () => true }
    } as any);

    expect(notificationService.getNotifications).toHaveBeenCalledWith(100);
    expect(component.all.length).toBe(2);
  });

  it('loadMore appends older items and dedupes by id', () => {
    component.all = [summary('n3', 3000), summary('n2', 2000)];
    notificationService.getNotifications.and.returnValue(of([summary('n2', 2000), summary('n1', 1000)]));

    component.loadMore();

    expect(notificationService.getNotifications).toHaveBeenCalledWith(100, 2000);
    expect(component.all.map((n) => n.id)).toEqual(['n3', 'n2', 'n1']);
  });

  it('loadMore stops when a page is smaller than pageSize', () => {
    component.all = [summary('n3', 3000), summary('n2', 2000)];
    notificationService.getNotifications.and.returnValue(of([summary('n1', 1000)]));

    component.loadMore();

    expect(component.hasMore).toBeFalse();
  });

  it('loadMore does nothing while already loading or when exhausted', () => {
    component.all = [summary('n1', 1000)];
    component.loadingMore = true;
    component.loadMore();
    expect(notificationService.getNotifications).not.toHaveBeenCalled();

    component.loadingMore = false;
    component.hasMore = false;
    component.loadMore();
    expect(notificationService.getNotifications).not.toHaveBeenCalled();
  });

  it('onScroll triggers loadMore near the bottom', () => {
    component.all = [summary('n1', 1000)];
    notificationService.getNotifications.and.returnValue(of([]));
    const el = { scrollTop: 800, clientHeight: 200, scrollHeight: 1000 } as HTMLElement;

    component.onScroll({ target: el } as any);

    expect(notificationService.getNotifications).toHaveBeenCalled();
  });
});