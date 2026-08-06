import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {SessionService} from './session-service';
import {SessionInfo} from '../models/session.model';

describe('SessionService', () => {
  let service: SessionService;
  let httpMock: HttpTestingController;

  const session: SessionInfo = {
    id: 'session-public-id',
    current: true,
    createdAt: '2026-08-01T10:00:00',
    lastUsedAt: '2026-08-05T10:00:00',
    expiresAt: '2026-08-12T10:00:00',
    ipAddress: '203.0.113.10',
    device: 'Mozilla/5.0',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list should GET /api/me/sessions', () => {
    // #when
    service.list().subscribe();

    // #then
    const req = httpMock.expectOne('/api/me/sessions');
    expect(req.request.method).toBe('GET');
    req.flush({sessions: [session]});
  });

  it('revoke should DELETE /api/me/sessions/{id}', () => {
    // #when
    service.revoke('session-public-id').subscribe();

    // #then
    const req = httpMock.expectOne('/api/me/sessions/session-public-id');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('revokeOthers should DELETE /api/me/sessions/others', () => {
    // #when
    service.revokeOthers().subscribe();

    // #then
    const req = httpMock.expectOne('/api/me/sessions/others');
    expect(req.request.method).toBe('DELETE');
    req.flush({revokedCount: 2});
  });

  it('revokeAll should DELETE /api/me/sessions', () => {
    // #when
    service.revokeAll().subscribe();

    // #then
    const req = httpMock.expectOne('/api/me/sessions');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
