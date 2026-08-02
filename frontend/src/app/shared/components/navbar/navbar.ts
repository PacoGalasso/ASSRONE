import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/auth/services/auth-service';

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
  private authService = inject(AuthService);
  private router = inject(Router);

  isLoggedIn = this.authService.isLoggedIn;
  user = this.authService.user;

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
}
