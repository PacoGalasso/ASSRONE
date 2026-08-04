import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { About } from './about';
import { CommitteeMemberService } from '../../core/auth/services/committee-member-service';
import { CommitteeMemberDto } from '../../core/auth/models/committee-member.model';

describe('About', () => {
  let component: About;
  let fixture: ComponentFixture<About>;
  let committeeMemberService: { getPublicMembers: ReturnType<typeof vi.fn>; photoUrl: ReturnType<typeof vi.fn> };

  const withPhoto: CommitteeMemberDto = {
    id: 1, firstName: 'Béatrice', lastName: 'Amoroso Galasso', role: 'Présidente',
    description: 'Une description.', hasPhoto: true, displayOrder: 1, active: true,
  };
  const withoutPhoto: CommitteeMemberDto = {
    id: 2, firstName: 'Helena', lastName: 'Da Silva', role: 'Vice-présidente',
    description: null, hasPhoto: false, displayOrder: 2, active: true,
  };
  const vacantSeat: CommitteeMemberDto = {
    id: 3, firstName: 'Poste', lastName: 'à pourvoir', role: 'Membre du comité',
    description: null, hasPhoto: false, displayOrder: 3, active: true,
  };

  async function setup(response = of([withPhoto, withoutPhoto, vacantSeat])) {
    committeeMemberService = {
      getPublicMembers: vi.fn().mockReturnValue(response),
      photoUrl: vi.fn().mockImplementation((id: number) => `/api/committee-members/${id}/photo`),
    };

    await TestBed.configureTestingModule({
      imports: [About],
      providers: [
        {provide: CommitteeMemberService, useValue: committeeMemberService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(About);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should create', async () => {
    await setup();
    expect(component).toBeTruthy();
  });

  it('renders the team section in a single centered responsive grid', async () => {
    // #when
    await setup();

    // #then
    const grid = fixture.nativeElement.querySelector('.max-w-5xl.mx-auto.grid');
    expect(grid).not.toBeNull();
    expect(grid.className).toContain('sm:grid-cols-2');
    expect(grid.className).toContain('lg:grid-cols-3');
  });

  it('loads members from the committee member service', async () => {
    // #when
    await setup();

    // #then
    expect(committeeMemberService.getPublicMembers).toHaveBeenCalled();
    expect(component.team()).toEqual([withPhoto, withoutPhoto, vacantSeat]);
  });

  it('renders only the members returned by the backend', async () => {
    // #when
    await setup();

    // #then
    const cards = fixture.nativeElement.querySelectorAll('.max-w-5xl.mx-auto.grid h3');
    expect(cards.length).toBe(3);
  });

  it('displays a photo for a member with hasPhoto true', async () => {
    // #when
    await setup();

    // #then
    const img = fixture.nativeElement.querySelector('img[alt="Béatrice Amoroso Galasso"]');
    expect(img).not.toBeNull();
  });

  it('displays initials for a member without a photo', async () => {
    // #when
    await setup();

    // #then
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('HD');
  });

  it('displays a neutral icon instead of computed initials for the vacant seat placeholder', async () => {
    // #when
    await setup();

    // #then
    expect(component.isVacantSeat(vacantSeat)).toBe(true);
    const initialsSpans = fixture.nativeElement.querySelectorAll('span.font-display.font-semibold.text-navy');
    expect(initialsSpans.length).toBe(1);
    expect(initialsSpans[0].textContent.trim()).toBe('HD');
  });

  it('displays the role for each member', async () => {
    // #when
    await setup();

    // #then
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Présidente');
    expect(text).toContain('Vice-présidente');
  });

  it('displays the description when present', async () => {
    // #when
    await setup();

    // #then
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Une description.');
  });

  it('shows a loading state before the response arrives, then hides it once loaded', async () => {
    // #given
    const pending = new Subject<CommitteeMemberDto[]>();
    committeeMemberService = {
      getPublicMembers: vi.fn().mockReturnValue(pending.asObservable()),
      photoUrl: vi.fn().mockImplementation((id: number) => `/api/committee-members/${id}/photo`),
    };
    await TestBed.configureTestingModule({
      imports: [About],
      providers: [{provide: CommitteeMemberService, useValue: committeeMemberService}],
    }).compileComponents();
    fixture = TestBed.createComponent(About);
    component = fixture.componentInstance;

    // #when
    fixture.detectChanges();

    // #then
    expect(component.teamLoading()).toBe(true);
    expect((fixture.nativeElement.textContent as string)).toContain("Chargement de l'équipe");

    // #when
    pending.next([withPhoto]);
    fixture.detectChanges();

    // #then
    expect(component.teamLoading()).toBe(false);
  });

  it('shows an error message without breaking the rest of the page on failure', async () => {
    // #when
    await setup(throwError(() => ({status: 500})));

    // #then
    expect(component.teamError()).toBe(true);
    const historyHeading = fixture.nativeElement.textContent as string;
    expect(historyHeading).toContain('Notre histoire');
  });
});
