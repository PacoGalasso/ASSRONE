// features/admin-committee/admin-committee.ts
import {Component, OnInit, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {CommitteeMemberService} from '../../core/auth/services/committee-member-service';
import {CommitteeMemberDto} from '../../core/auth/models/committee-member.model';

@Component({
  selector: 'app-admin-committee',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './admin-committee.html',
})
export class AdminCommittee implements OnInit {
  private committeeMemberService = inject(CommitteeMemberService);
  private fb = inject(FormBuilder);

  members = signal<CommitteeMemberDto[]>([]);
  loading = signal(true);
  listErrorMessage = signal<string | null>(null);

  showForm = signal(false);
  editingId = signal<number | null>(null);
  saving = signal(false);
  formErrorMessage = signal<string | null>(null);
  selectedFile = signal<File | null>(null);

  deletingId = signal<number | null>(null);
  uploadingPhotoId = signal<number | null>(null);
  removingPhotoId = signal<number | null>(null);

  form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(80)]],
    role: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    description: ['', Validators.maxLength(1200)],
    displayOrder: [0, [Validators.required, Validators.min(0)]],
    active: [true, Validators.required],
  });

  ngOnInit(): void {
    this.loadMembers();
  }

  loadMembers(): void {
    this.loading.set(true);
    this.listErrorMessage.set(null);
    this.committeeMemberService.getAllForAdmin().subscribe({
      next: (data) => {
        this.members.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.listErrorMessage.set('Une erreur est survenue lors du chargement des membres.');
        this.loading.set(false);
      },
    });
  }

  photoUrl(member: CommitteeMemberDto): string {
    return this.committeeMemberService.photoUrl(member.id);
  }

  openCreateForm(): void {
    this.editingId.set(null);
    this.selectedFile.set(null);
    this.formErrorMessage.set(null);
    this.form.reset({firstName: '', lastName: '', role: '', description: '', displayOrder: 0, active: true});
    this.showForm.set(true);
  }

  openEditForm(member: CommitteeMemberDto): void {
    this.editingId.set(member.id);
    this.selectedFile.set(null);
    this.formErrorMessage.set(null);
    this.form.reset({
      firstName: member.firstName,
      lastName: member.lastName,
      role: member.role,
      description: member.description ?? '',
      displayOrder: member.displayOrder,
      active: member.active,
    });
    this.showForm.set(true);
  }

  cancelForm(): void {
    this.showForm.set(false);
    this.editingId.set(null);
    this.selectedFile.set(null);
    this.formErrorMessage.set(null);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.formErrorMessage.set(null);
    const value = this.form.getRawValue();

    const id = this.editingId();
    const request$ = id === null
      ? this.committeeMemberService.create(value)
      : this.committeeMemberService.update(id, value);

    request$.subscribe({
      next: (saved) => this.afterSaveSuccess(saved),
      error: (err) => {
        this.formErrorMessage.set(this.saveErrorMessage(err.status));
        this.saving.set(false);
      },
    });
  }

  private afterSaveSuccess(saved: CommitteeMemberDto): void {
    const file = this.selectedFile();
    if (!file) {
      this.saving.set(false);
      this.showForm.set(false);
      this.loadMembers();
      return;
    }

    this.committeeMemberService.uploadPhoto(saved.id, file).subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.loadMembers();
      },
      error: () => {
        this.formErrorMessage.set("La fiche a été enregistrée, mais l'envoi de la photo a échoué.");
        this.saving.set(false);
        this.showForm.set(false);
        this.loadMembers();
      },
    });
  }

  onDeletePhoto(member: CommitteeMemberDto): void {
    this.removingPhotoId.set(member.id);
    this.committeeMemberService.deletePhoto(member.id).subscribe({
      next: () => {
        this.removingPhotoId.set(null);
        this.loadMembers();
      },
      error: () => this.removingPhotoId.set(null),
    });
  }

  onDelete(member: CommitteeMemberDto): void {
    if (!confirm(`Supprimer définitivement "${member.firstName} ${member.lastName}" ? Cette action est irréversible.`)) {
      return;
    }

    this.deletingId.set(member.id);
    this.committeeMemberService.delete(member.id).subscribe({
      next: () => {
        this.members.update(list => list.filter(m => m.id !== member.id));
        this.deletingId.set(null);
      },
      error: () => this.deletingId.set(null),
    });
  }

  private saveErrorMessage(status: number): string {
    if (status === 400) {
      return 'Certains champs sont invalides. Vérifiez le formulaire.';
    }
    if (status === 403) {
      return 'Action interdite : vous devez être administrateur.';
    }
    if (status === 404) {
      return 'Ce membre n\'existe plus.';
    }
    return 'Une erreur est survenue lors de l\'enregistrement.';
  }
}
