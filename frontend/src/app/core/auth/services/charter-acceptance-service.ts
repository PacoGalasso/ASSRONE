// core/services/charter-acceptance-service.ts
import {Injectable, signal} from '@angular/core';

const STORAGE_KEY = 'charter_accepted';

@Injectable({providedIn: 'root'})
export class CharterAcceptanceService {
  private accepted = signal<boolean>(sessionStorage.getItem(STORAGE_KEY) === 'true');

  hasAccepted(): boolean {
    return this.accepted();
  }

  accept(): void {
    this.accepted.set(true);
    sessionStorage.setItem(STORAGE_KEY, 'true');
  }
}
