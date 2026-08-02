import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/auth/services/auth-service';
import { ProfileService } from '../../../core/auth/services/profile-service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLinkActive, RouterLink, CommonModule],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css',
})
export class Navbar {
  isMenuOpen = signal(false);
  isProfileOpen = signal(false);
  avatarUrl = signal<string | null>(null);
  private authService = inject(AuthService);
  private profileService = inject(ProfileService);
  private router = inject(Router);

  isLoggedIn = this.authService.isLoggedIn;
  user = this.authService.user;
  isAdmin = () => this.authService.isAdmin();
  initials = computed(() => this.user()?.username?.[0]?.toUpperCase() ?? '?');

  constructor() {
    effect(() => {
      if (this.isLoggedIn()) {
        this.loadAvatar();
      } else {
        this.avatarUrl.set(null);
      }
    });
  }

  toggleMenu = () => this.isMenuOpen.update(v => !v);
  toggleProfile = (e: Event) => {
    e.stopPropagation();
    this.isProfileOpen.update(v => !v);
  };
  closeMenus = () => {
    this.isMenuOpen.set(false);
    this.isProfileOpen.set(false);
  };
  logout = () => {
    this.authService.logout();
    this.closeMenus();
  };

  private loadAvatar(): void {
    this.profileService.getAvatar().subscribe({
      next: (blob) => this.avatarUrl.set(window.URL.createObjectURL(blob)),
      error: () => this.avatarUrl.set(null),
    });
  }
}
