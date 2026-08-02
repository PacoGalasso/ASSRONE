// features/profile/profile.ts
import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators} from '@angular/forms';
import {ProfileService} from '../../core/auth/services/profile-service';
import {UserProfile} from '../../core/auth/models/profile.model';
import {DatePipe} from '@angular/common';


function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return newPassword === confirmPassword ? null : {passwordsMismatch: true};
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './profile.html',
})
export class Profile implements OnInit {
  profile = signal<UserProfile | null>(null);
  loading = signal(true);
  savingInfo = signal(false);
  infoSuccess = signal(false);
  infoError = signal<string | null>(null);
  savingPassword = signal(false);
  passwordSuccess = signal(false);
  passwordError = signal<string | null>(null);
  avatarUrl = signal<string | null>(null);
  uploadingAvatar = signal(false);
  avatarError = signal<string | null>(null);
  initials = computed(() => {
    const p = this.profile();
    if (!p) return '';
    return `${p.firstName?.[0] ?? ''}${p.lastName?.[0] ?? ''}`.toUpperCase() || p.username[0]?.toUpperCase() || '?';
  });
  private profileService = inject(ProfileService);
  private fb = inject(FormBuilder);
  infoForm = this.fb.nonNullable.group({
    username: ['', Validators.required],
    firstName: [''],
    lastName: [''],
    email: ['', [Validators.required, Validators.email]],
  });

  passwordForm = this.fb.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    },
    {validators: passwordsMatchValidator}
  );

  ngOnInit(): void {
    this.profileService.getProfile().subscribe({
      next: (data) => {
        this.profile.set(data);
        this.infoForm.patchValue({
          username: data.username,
          firstName: data.firstName,
          lastName: data.lastName,
          email: data.email,
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.loadAvatar();
  }

  onAvatarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;

    this.avatarError.set(null);
    this.uploadingAvatar.set(true);

    this.profileService.uploadAvatar(file).subscribe({
      next: (data) => {
        this.profile.set(data);
        this.uploadingAvatar.set(false);
        this.loadAvatar();
      },
      error: () => {
        this.avatarError.set("Une erreur est survenue lors de l'envoi de la photo.");
        this.uploadingAvatar.set(false);
      },
    });
  }

  private loadAvatar(): void {
    this.profileService.getAvatar().subscribe({
      next: (blob) => {
        const previous = this.avatarUrl();
        if (previous) {
          window.URL.revokeObjectURL(previous);
        }
        this.avatarUrl.set(window.URL.createObjectURL(blob));
      },
      error: () => this.avatarUrl.set(null),
    });
  }

  onSaveInfo(): void {
    if (this.infoForm.invalid) {
      this.infoForm.markAllAsTouched();
      return;
    }

    this.savingInfo.set(true);
    this.infoError.set(null);
    this.infoSuccess.set(false);

    this.profileService.updateProfile(this.infoForm.getRawValue()).subscribe({
      next: (data) => {
        this.profile.set(data);
        this.savingInfo.set(false);
        this.infoSuccess.set(true);
      },
      error: () => {
        this.infoError.set('Une erreur est survenue lors de la mise à jour.');
        this.savingInfo.set(false);
      },
    });
  }

  onChangePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.savingPassword.set(true);
    this.passwordError.set(null);
    this.passwordSuccess.set(false);

    const {currentPassword, newPassword} = this.passwordForm.getRawValue();
    this.profileService.changePassword({currentPassword, newPassword}).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordSuccess.set(true);
        this.passwordForm.reset();
      },
      error: (err) => {
        this.passwordError.set(
          err.status === 400 ? 'Le mot de passe actuel est incorrect.' : 'Une erreur est survenue.'
        );
        this.savingPassword.set(false);
      },
    });
  }
}
