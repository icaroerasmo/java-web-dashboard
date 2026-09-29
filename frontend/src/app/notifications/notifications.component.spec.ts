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
      sentAt: null, timestamp, date: '2026-09-25', hour: '10', size: 1024,
      personNames: null
    };
  }

  function page(items: NotificationSummary[], nextCursor: string | null = null, hasMore = false) {
    return { items, nextCursor, hasMore };
  }

  beforeEach(async () => {
    notificationService = jasmine.createSpyObj('NotificationService', ['getNotifications', 'getKinds', 'mediaUrl', 'getMediaText']);
    notificationService.mediaUrl.and.returnValue('/api/notifications/media/x');
    notificationService.getMediaText.and.returnValue(of('log content'));
    notificationService.getKinds.and.returnValue(of([]));
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

  it('loads the first page of notifications on open', () => {
    notificationService.getNotifications.and.returnValue(of(page([summary('n1', 3000)], 'cur1', true)));
    component.open = true;
    component.ngOnChanges({
      open: { currentValue: true, previousValue: false, firstChange: true, isFirstChange: () => true }
    } as any);

    expect(notificationService.getNotifications).toHaveBeenCalledWith('notifications', 100, undefined, undefined, undefined, undefined, undefined);
    expect(component.all.length).toBe(1);
  });

  it('loadMore appends older items and dedupes by id', () => {
    component.all = [summary('n3', 3000), summary('n2', 2000)];
    (component as any).nextCursor = 'cur1';
    component.hasMore = true;
    notificationService.getNotifications.and.returnValue(of(page([summary('n2', 2000), summary('n1', 1000)], 'cur2', false)));

    component.loadMore();

    expect(notificationService.getNotifications).toHaveBeenCalledWith('notifications', 100, 'cur1', undefined, undefined, undefined, undefined);
    expect(component.all.map((n) => n.id)).toEqual(['n3', 'n2', 'n1']);
    expect(component.hasMore).toBeFalse();
  });

  it('loadMore does nothing while exhausted or without cursor', () => {
    component.all = [summary('n1', 1000)];
    component.hasMore = false;
    (component as any).nextCursor = null;
    component.loadMore();
    expect(notificationService.getNotifications).not.toHaveBeenCalled();
  });

  it('selectTab loads logs with type=logs', () => {
    notificationService.getNotifications.and.returnValue(of(page([summary('n1', 1000)])));
    component.selectTab('logs');

    expect(component.tab).toBe('logs');
    expect(notificationService.getNotifications).toHaveBeenCalledWith('logs', 100, undefined, undefined, undefined, undefined, undefined);
  });

  it('onSearch reloads passing the text query', () => {
    notificationService.getNotifications.and.returnValue(of(page([])));
    component.searchText = 'disco cheio';
    component.onSearch();

    expect(notificationService.getNotifications).toHaveBeenCalledWith('notifications', 100, undefined, 'disco cheio', undefined, undefined, undefined);
  });

  it('onScroll triggers loadMore near the bottom', () => {
    component.all = [summary('n1', 1000)];
    (component as any).nextCursor = 'cur1';
    component.hasMore = true;
    notificationService.getNotifications.and.returnValue(of(page([])));
    const el = { scrollTop: 800, clientHeight: 200, scrollHeight: 1000 } as HTMLElement;

    component.onScroll({ target: el } as any);

    expect(notificationService.getNotifications).toHaveBeenCalled();
  });

  it('sizeLabel formats bytes as Kb with one decimal', () => {
    expect(component.sizeLabel({ ...summary('n1', 1000), size: 1843 })).toBe('(1.8Kb)');
    expect(component.sizeLabel({ ...summary('n1', 1000), size: 1536 })).toBe('(1.5Kb)');
    expect(component.sizeLabel({ ...summary('n1', 1000), size: 1024 })).toBe('(1Kb)');
    expect(component.sizeLabel({ ...summary('n1', 1000), size: 0 })).toBe('');
    expect(component.sizeLabel({ ...summary('n1', 1000), size: null })).toBe('');
  });
});
