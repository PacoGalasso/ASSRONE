import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {AdminUserService} from './admin-user-service';

describe('AdminUserService', () => {
  let service: AdminUserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminUserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getAll should GET /api/users', () => {
    // #when
    service.getAll().subscribe();

    // #then
    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('changeRole should PUT the new role to /api/users/{id}/role', () => {
    // #when
    service.changeRole(2, 'ADMIN').subscribe();

    // #then
    const req = httpMock.expectOne('/api/users/2/role');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({role: 'ADMIN'});
    req.flush(null);
  });

  it('deleteUser should DELETE /api/users/{id}', () => {
    // #when
    service.deleteUser(2).subscribe();

    // #then
    const req = httpMock.expectOne('/api/users/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
