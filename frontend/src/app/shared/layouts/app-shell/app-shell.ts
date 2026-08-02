import { Component } from '@angular/core';
import {Navbar} from '../../components/navbar/navbar';
import {RouterOutlet} from '@angular/router';

@Component({
  selector: 'app-app-shell',
  imports: [
    Navbar,
    RouterOutlet
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
export class AppShell {}
