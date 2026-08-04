import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {EventService} from './event-service';

describe('EventService', () => {
  let service: EventService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EventService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getById should GET /api/events/{id}', () => {
    // #when
    service.getById(7).subscribe();

    // #then
    const req = httpMock.expectOne('/api/events/7');
    expect(req.request.method).toBe('GET');
    req.flush(null);
  });

  it('update should PUT the payload to /api/events/{id}', () => {
    // #given
    const request = {
      title: 'Atelier', description: 'Description', type: 'Rencontre',
      eventDate: '2027-01-01', startTime: '18:00', endTime: '20:00',
      location: 'Local', maxParticipants: 10,
    };

    // #when
    service.update(7, request).subscribe();

    // #then
    const req = httpMock.expectOne('/api/events/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(null);
  });
});
