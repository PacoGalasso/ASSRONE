import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import Events from './events';
import { EventService } from '../../core/auth/services/event-service';
import { AuthService } from '../../core/auth/services/auth-service';
import { EventItem } from '../../core/auth/models/event.model';

const SAMPLE_EVENT: EventItem = {
  id: 1,
  title: 'Atelier bénévolat',
  description: 'Description',
  type: 'WEBINAIRE',
  eventDate: '2027-01-01',
  startTime: '18:00:00',
  endTime: '20:00:00',
  location: 'Local associatif',
  maxParticipants: 10,
  currentParticipants: 2,
};

describe('Events', () => {
  let component: Events;
  let fixture: ComponentFixture<Events>;
  let eventService: { getAll: ReturnType<typeof vi.fn>; register: ReturnType<typeof vi.fn> };

  async function setup(isAdmin: boolean) {
    eventService = {
      getAll: vi.fn().mockReturnValue(of([SAMPLE_EVENT])),
      register: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Events],
      providers: [
        provideRouter([]),
        {provide: EventService, useValue: eventService},
        {provide: AuthService, useValue: {isAdmin: () => isAdmin}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Events);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should create', async () => {
    await setup(false);
    expect(component).toBeTruthy();
  });

  it('shows a "Modifier" link pointing to the edit route for an admin', async () => {
    // #when
    await setup(true);

    // #then
    const editLink = fixture.nativeElement.querySelector('a[title="Modifier l\'événement"]');
    expect(editLink).not.toBeNull();
    expect(editLink.getAttribute('href')).toBe('/events/1/edit');
  });

  it('hides the "Modifier" link for a non-admin', async () => {
    // #when
    await setup(false);

    // #then
    const editLink = fixture.nativeElement.querySelector('a[title="Modifier l\'événement"]');
    expect(editLink).toBeNull();
  });

  it('does not render the removed static slogan', async () => {
    // #when
    await setup(false);

    // #then
    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Nos prochaines formations, webinaires et ateliers');
  });

  it('shows the backend-provided message on a 409 registration conflict', async () => {
    // #given
    await setup(false);
    eventService.register.mockReturnValue(
      throwError(() => ({status: 409, error: {error: 'Vous êtes déjà inscrit à cet événement.'}}))
    );
    component.registrationForm.setValue({fullName: 'Jean Dupont', email: 'jean.dupont@assrone.ch'});

    // #when
    component.onRegister(SAMPLE_EVENT);

    // #then
    expect(component.registrationError()).toBe('Vous êtes déjà inscrit à cet événement.');
  });

  it('falls back to a generic message on a 409 without a backend error body', async () => {
    // #given
    await setup(false);
    eventService.register.mockReturnValue(throwError(() => ({status: 409, error: null})));
    component.registrationForm.setValue({fullName: 'Jean Dupont', email: 'jean.dupont@assrone.ch'});

    // #when
    component.onRegister(SAMPLE_EVENT);

    // #then
    expect(component.registrationError()).toBe('Une erreur est survenue. Réessayez.');
  });
});
