import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { CreateEvent } from './create-event';
import { EventService } from '../../core/auth/services/event-service';
import { Router } from '@angular/router';
import { EventItem } from '../../core/auth/models/event.model';

describe('CreateEvent', () => {
  let component: CreateEvent;
  let fixture: ComponentFixture<CreateEvent>;
  let eventService: { create: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    eventService = {create: vi.fn()};
    router = {navigate: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [CreateEvent],
      providers: [
        {provide: EventService, useValue: eventService},
        {provide: Router, useValue: router},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateEvent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders a free-text input for type instead of a select', () => {
    // #then
    const select = fixture.nativeElement.querySelector('select[formControlName="type"]');
    const input = fixture.nativeElement.querySelector('input[formControlName="type"]');
    expect(select).toBeNull();
    expect(input).not.toBeNull();
  });

  it('marks the form invalid when type is empty', () => {
    // #given
    component.form.patchValue({title: 'Titre', description: 'Desc', type: '', location: 'Lieu'});

    // #then
    expect(component.form.controls.type.invalid).toBe(true);
  });

  it('rejects a type longer than 80 characters', () => {
    // #given
    component.form.controls.type.setValue('A'.repeat(81));

    // #then
    expect(component.form.controls.type.hasError('maxlength')).toBe(true);
  });

  it('accepts a type of exactly 80 characters', () => {
    // #given
    component.form.controls.type.setValue('A'.repeat(80));

    // #then
    expect(component.form.controls.type.hasError('maxlength')).toBe(false);
  });

  it('sends the trimmed type value on submit', () => {
    // #given
    eventService.create.mockReturnValue(of({} as EventItem));
    component.form.setValue({
      title: 'Atelier', description: 'Description', type: '  Rencontre  ',
      eventDate: '2027-01-01', startTime: '18:00', endTime: '20:00',
      location: 'Local', maxParticipants: 10,
    });

    // #when
    component.onSubmit();

    // #then
    expect(eventService.create).toHaveBeenCalledWith(expect.objectContaining({type: 'Rencontre'}));
  });

  it('does not submit while a request is pending, preventing double submission', () => {
    // #given
    const pending = new Subject<EventItem>();
    eventService.create.mockReturnValue(pending.asObservable());
    component.form.setValue({
      title: 'Atelier', description: 'Description', type: 'Rencontre',
      eventDate: '2027-01-01', startTime: '18:00', endTime: '20:00',
      location: 'Local', maxParticipants: 10,
    });

    // #when
    component.onSubmit();
    component.onSubmit();

    // #then
    expect(eventService.create).toHaveBeenCalledTimes(1);
  });

  it('navigates to /events on success', () => {
    // #given
    eventService.create.mockReturnValue(of({} as EventItem));
    component.form.setValue({
      title: 'Atelier', description: 'Description', type: 'Rencontre',
      eventDate: '2027-01-01', startTime: '18:00', endTime: '20:00',
      location: 'Local', maxParticipants: 10,
    });

    // #when
    component.onSubmit();

    // #then
    expect(router.navigate).toHaveBeenCalledWith(['/events']);
  });

  it('surfaces an error message and re-enables the form on failure', () => {
    // #given
    eventService.create.mockReturnValue(throwError(() => ({status: 400})));
    component.form.setValue({
      title: 'Atelier', description: 'Description', type: 'Rencontre',
      eventDate: '2027-01-01', startTime: '18:00', endTime: '20:00',
      location: 'Local', maxParticipants: 10,
    });

    // #when
    component.onSubmit();

    // #then
    expect(component.errorMessage()).not.toBeNull();
    expect(component.submitting()).toBe(false);
  });
});
