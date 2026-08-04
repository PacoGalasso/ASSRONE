import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

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

  async function setup(isAdmin: boolean) {
    await TestBed.configureTestingModule({
      imports: [Events],
      providers: [
        provideRouter([]),
        {provide: EventService, useValue: {getAll: () => of([SAMPLE_EVENT])}},
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
});
