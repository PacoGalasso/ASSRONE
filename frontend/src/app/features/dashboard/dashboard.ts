import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {AuthService} from '../../core/auth/services/auth-service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  user = this.authService.user;
  isAdmin = this.authService.isAdmin();

  ngOnInit(): void {
    if (!this.authService.getToken()) {
      this.router.navigate(['/auth/login']);
    }
  }

  logout(): void {
    this.authService.logout();
  }

  // USER actions
  uploadFile(): void {
    alert('Fonctionnalité "Déposer fichiers" - À implémenter');
  }

  createEvent(): void {
    alert('Fonctionnalité "Ajouter événement" - À implémenter');
  }

  // ADMIN actions
  addUser(): void {
    alert('Fonctionnalité "Ajouter utilisateur" - À implémenter');
  }

  manageUsers(): void {
    alert('Fonctionnalité "Gérer utilisateurs" - À implémenter');
  }
}
