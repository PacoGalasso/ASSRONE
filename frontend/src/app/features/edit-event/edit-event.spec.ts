import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { EditEvent } from './edit-event';
import { EventService } from '../../core/auth/services/event-service';
import { EventItem } from '../../core/auth/models/event.model';

const EXISTING_EVENT: EventItem = {
  id: 7,
  title: 'Ancien événement',
  description: 'Description existante',
  type: 'WEBINAIRE',
  eventDate: '2027-03-15',
  startTime: '18:00:00',
  endTime: '20:00:00',
  location: 'Salle A',
  maxParticipants: 10,
  currentParticipants: 3,
};

describe('EditEvent', () => {
  let component: EditEvent;
  let fixture: ComponentFixture<EditEvent>;
  let eventService: { getById: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  async function setup(getByIdResult = of(EXISTING_EVENT)) {
    eventService = {getById: vi.fn().mockReturnValue(getByIdResult), update: vi.fn()};
    router = {navigate: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [EditEvent],
      providers: [
        {provide: EventService, useValue: eventService},
        {provide: Router, useValue: router},
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {paramMap: convertToParamMap({id: '7'})}},
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditEvent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('should create', async () => {
    await setup();
    expect(component).toBeTruthy();
  });

  it('fetches the event by the route id and prefills the form, including a historical type value', async () => {
    // #when
    await setup();

    // #then
    expect(eventService.getById).toHaveBeenCalledWith(7);
    expect(component.form.controls.title.value).toBe('Ancien événement');
    expect(component.form.controls.type.value).toBe('WEBINAIRE');
    expect(component.form.controls.maxParticipants.value).toBe(10);
  });

  it('surfaces a load error and does not show the form', async () => {
    // #when
    await setup(throwError(() => ({status: 404})));

    // #then
    expect(component.loadErrorMessage()).not.toBeNull();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('sends the update to the correct endpoint with the trimmed type', async () => {
    // #given
    await setup();
    eventService.update.mockReturnValue(of(EXISTING_EVENT));
    component.form.patchValue({type: '  Conférence  '});

    // #when
    component.onSubmit();

    // #then
    expect(eventService.update).toHaveBeenCalledWith(7, expect.objectContaining({type: 'Conférence'}));
  });

  it('navigates to /events on success', async () => {
    // #given
    await setup();
    eventService.update.mockReturnValue(of(EXISTING_EVENT));

    // #when
    component.onSubmit();

    // #then
    expect(router.navigate).toHaveBeenCalledWith(['/events']);
  });

  it('does not call the backend when cancelling (no submit triggered)', async () => {
    // #given
    await setup();

    // #then
    expect(eventService.update).not.toHaveBeenCalled();
    const cancelLink = fixture.nativeElement.querySelector('a[routerLink="/events"]');
    expect(cancelLink).not.toBeNull();
  });

  it('does not submit while a request is pending, preventing double submission', async () => {
    // #given
    await setup();
    const pending = new Subject<EventItem>();
    eventService.update.mockReturnValue(pending.asObservable());

    // #when
    component.onSubmit();
    component.onSubmit();

    // #then
    expect(eventService.update).toHaveBeenCalledTimes(1);
  });

  it('surfaces the capacity-conflict message on 409 without leaving the page', async () => {
    // #given
    await setup();
    eventService.update.mockReturnValue(throwError(() => ({status: 409})));

    // #when
    component.onSubmit();

    // #then
    expect(component.errorMessage()).toContain('inscrits');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('surfaces a 403 as a forbidden message', async () => {
    // #given
    await setup();
    eventService.update.mockReturnValue(throwError(() => ({status: 403})));

    // #when
    component.onSubmit();

    // #then
    expect(component.errorMessage()).not.toBeNull();
  });

  it('surfaces a 404 as a not-found message', async () => {
    // #given
    await setup();
    eventService.update.mockReturnValue(throwError(() => ({status: 404})));

    // #when
    component.onSubmit();

    // #then
    expect(component.errorMessage()).toContain("n'existe plus");
  });

  it('surfaces a 400 as an invalid-payload message', async () => {
    // #given
    await setup();
    eventService.update.mockReturnValue(throwError(() => ({status: 400})));

    // #when
    component.onSubmit();

    // #then
    expect(component.errorMessage()).not.toBeNull();
  });
});
