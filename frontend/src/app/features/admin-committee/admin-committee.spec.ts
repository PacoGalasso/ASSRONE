import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { AdminCommittee } from './admin-committee';
import { CommitteeMemberService } from '../../core/auth/services/committee-member-service';
import { CommitteeMemberDto } from '../../core/auth/models/committee-member.model';

describe('AdminCommittee', () => {
  let component: AdminCommittee;
  let fixture: ComponentFixture<AdminCommittee>;
  let committeeMemberService: {
    getAllForAdmin: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    uploadPhoto: ReturnType<typeof vi.fn>;
    deletePhoto: ReturnType<typeof vi.fn>;
    photoUrl: ReturnType<typeof vi.fn>;
  };

  const visibleMember: CommitteeMemberDto = {
    id: 1, firstName: 'Isabelle', lastName: 'Santarelli', role: 'Membre du comité',
    description: null, hasPhoto: false, displayOrder: 6, active: true,
  };
  const hiddenMember: CommitteeMemberDto = {
    id: 2, firstName: 'Jean', lastName: 'Dupont', role: 'Trésorier',
    description: null, hasPhoto: true, displayOrder: 8, active: false,
  };

  function validFormValue() {
    return {firstName: 'Marie', lastName: 'Curie', role: 'Membre du comité', description: '', displayOrder: 9, active: true};
  }

  async function setup(members: CommitteeMemberDto[] = [visibleMember, hiddenMember]) {
    committeeMemberService = {
      getAllForAdmin: vi.fn().mockReturnValue(of(members)),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      uploadPhoto: vi.fn(),
      deletePhoto: vi.fn(),
      photoUrl: vi.fn().mockReturnValue('/api/committee-members/1/photo'),
    };

    await TestBed.configureTestingModule({
      imports: [AdminCommittee],
      providers: [{provide: CommitteeMemberService, useValue: committeeMemberService}],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminCommittee);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should create', async () => {
    await setup();
    expect(component).toBeTruthy();
  });

  it('lists every member including hidden ones', async () => {
    // #when
    await setup();

    // #then
    expect(component.members()).toEqual([visibleMember, hiddenMember]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Isabelle Santarelli');
    expect(text).toContain('Jean Dupont');
  });

  it('shows a "Masqué" badge for an inactive member and "Visible" for an active one', async () => {
    // #when
    await setup();

    // #then
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Visible');
    expect(text).toContain('Masqué');
  });

  it('opens an empty create form when "Ajouter un membre" is clicked', async () => {
    // #given
    await setup();

    // #when
    component.openCreateForm();
    fixture.detectChanges();

    // #then
    expect(component.editingId()).toBeNull();
    expect(component.form.controls.firstName.value).toBe('');
    expect(component.showForm()).toBe(true);
  });

  it('opens a pre-filled edit form for an existing member', async () => {
    // #given
    await setup();

    // #when
    component.openEditForm(visibleMember);

    // #then
    expect(component.editingId()).toBe(1);
    expect(component.form.getRawValue()).toEqual({
      firstName: 'Isabelle', lastName: 'Santarelli', role: 'Membre du comité',
      description: '', displayOrder: 6, active: true,
    });
  });

  it('marks the form invalid when required fields are empty', async () => {
    // #given
    await setup();
    component.openCreateForm();

    // #then
    expect(component.form.invalid).toBe(true);
  });

  it('rejects a description longer than 1200 characters', async () => {
    // #given
    await setup();
    component.form.controls.description.setValue('A'.repeat(1201));

    // #then
    expect(component.form.controls.description.hasError('maxlength')).toBe(true);
  });

  it('accepts a description of exactly 1200 characters', async () => {
    // #given
    await setup();
    component.form.controls.description.setValue('A'.repeat(1200));

    // #then
    expect(component.form.controls.description.hasError('maxlength')).toBe(false);
  });

  it('does not call the service when the form is invalid', async () => {
    // #given
    await setup();
    component.openCreateForm();

    // #when
    component.onSubmit();

    // #then
    expect(committeeMemberService.create).not.toHaveBeenCalled();
  });

  it('creates a member and reloads the list on success', async () => {
    // #given
    await setup();
    committeeMemberService.create.mockReturnValue(of({...visibleMember, id: 9}));
    committeeMemberService.getAllForAdmin.mockReturnValue(of([visibleMember, hiddenMember]));
    component.openCreateForm();
    component.form.setValue(validFormValue());

    // #when
    component.onSubmit();

    // #then
    expect(committeeMemberService.create).toHaveBeenCalledWith(validFormValue());
    expect(component.showForm()).toBe(false);
  });

  it('updates a member using the id from the URL, not the form', async () => {
    // #given
    await setup();
    committeeMemberService.update.mockReturnValue(of(visibleMember));
    component.openEditForm(visibleMember);
    component.form.patchValue({role: 'Nouvelle fonction'});

    // #when
    component.onSubmit();

    // #then
    expect(committeeMemberService.update).toHaveBeenCalledWith(1, expect.objectContaining({role: 'Nouvelle fonction'}));
  });

  it('does not submit twice while a save is pending, preventing double submission', async () => {
    // #given
    await setup();
    const pending = new Subject();
    committeeMemberService.create.mockReturnValue(pending.asObservable());
    component.openCreateForm();
    component.form.setValue(validFormValue());

    // #when
    component.onSubmit();
    component.onSubmit();

    // #then
    expect(committeeMemberService.create).toHaveBeenCalledTimes(1);
  });

  it('uploads the selected photo after a successful create', async () => {
    // #given
    await setup();
    committeeMemberService.create.mockReturnValue(of({...visibleMember, id: 9}));
    committeeMemberService.uploadPhoto.mockReturnValue(of({...visibleMember, id: 9, hasPhoto: true}));
    committeeMemberService.getAllForAdmin.mockReturnValue(of([visibleMember, hiddenMember]));
    component.openCreateForm();
    component.form.setValue(validFormValue());
    const file = new File(['contenu'], 'photo.png', {type: 'image/png'});
    component.selectedFile.set(file);

    // #when
    component.onSubmit();

    // #then
    expect(committeeMemberService.uploadPhoto).toHaveBeenCalledWith(9, file);
  });

  it('surfaces a backend error message on save failure', async () => {
    // #given
    await setup();
    committeeMemberService.create.mockReturnValue(throwError(() => ({status: 400})));
    component.openCreateForm();
    component.form.setValue(validFormValue());

    // #when
    component.onSubmit();

    // #then
    expect(component.formErrorMessage()).not.toBeNull();
    expect(component.saving()).toBe(false);
  });

  it('removes a photo via the dedicated action and reloads the list', async () => {
    // #given
    await setup();
    committeeMemberService.deletePhoto.mockReturnValue(of({...hiddenMember, hasPhoto: false}));

    // #when
    component.onDeletePhoto(hiddenMember);

    // #then
    expect(committeeMemberService.deletePhoto).toHaveBeenCalledWith(2);
  });

  it('asks for confirmation before deleting a member', async () => {
    // #given
    await setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

    // #when
    component.onDelete(visibleMember);

    // #then
    expect(confirmSpy).toHaveBeenCalled();
    expect(committeeMemberService.delete).not.toHaveBeenCalled();
  });

  it('deletes a member after confirmation and removes it from the local list', async () => {
    // #given
    await setup();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    committeeMemberService.delete.mockReturnValue(of(undefined));

    // #when
    component.onDelete(visibleMember);

    // #then
    expect(committeeMemberService.delete).toHaveBeenCalledWith(1);
    expect(component.members()).toEqual([hiddenMember]);
  });

  it('handles a load failure without throwing', async () => {
    // #when
    await setup();
    committeeMemberService.getAllForAdmin.mockReturnValue(throwError(() => ({status: 500})));

    // #then
    expect(() => component.loadMembers()).not.toThrow();
  });
});
